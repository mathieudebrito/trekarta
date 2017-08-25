package mobi.maptrek.maps.maptrek;
/*
 * Copyright 2013 Hannes Janetzek
 * Copyright 2016 devemux86
 * Copyright 2016 Andrey Novikov
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

import org.oscim.core.MapElement;
import org.oscim.core.PointF;
import org.oscim.core.Tile;
import org.oscim.layers.tile.MapTile;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelTileData;
import org.oscim.layers.tile.vector.labeling.WayDecorator;
import org.oscim.renderer.bucket.RenderBuckets;
import org.oscim.renderer.bucket.SymbolItem;
import org.oscim.renderer.bucket.TextItem;
import org.oscim.theme.styles.RenderStyle;
import org.oscim.theme.styles.SymbolStyle;
import org.oscim.theme.styles.TextStyle;
import org.oscim.utils.geom.PolyLabel;

import mobi.maptrek.util.StringFormatter;

import static org.oscim.core.GeometryBuffer.GeometryType.LINE;
import static org.oscim.core.GeometryBuffer.GeometryType.POINT;
import static org.oscim.core.GeometryBuffer.GeometryType.POLY;
import static org.oscim.layers.tile.vector.labeling.LabelLayer.LABEL_DATA;

public class LabelTileLoaderHook implements VectorTileLayer.TileLoaderThemeHook {
    private int mLang = 0;

    //public final static LabelTileData EMPTY = new LabelTileData();

    private LabelTileData get(MapTile tile) {
        // FIXME could be 'this'..
        LabelTileData ld = (LabelTileData) tile.getData(LABEL_DATA);
        if (ld == null) {
            ld = new LabelTileData();
            tile.addData(LABEL_DATA, ld);
        }
        return ld;
    }

    @Override
    public boolean process(MapTile tile, RenderBuckets buckets, MapElement element, RenderStyle style, int level) {
        if (style instanceof TextStyle) {
            LabelTileData ld = get(tile);

            TextStyle text = (TextStyle) style.current();
            if (element.type == LINE) {
                String value = getTextValue(element, text.textKey);
                if (value == null)
                    return false;

                int offset = 0;
                for (int i = 0, n = element.index.length; i < n; i++) {
                    int length = element.index[i];
                    if (length < 4)
                        break;

                    WayDecorator.renderText(null, element.points, value, text,
                            offset, length, ld);
                    offset += length;
                }
            } else if (element.type == POLY) {
                PointF label = element.labelPosition;

                if (element instanceof ExtendedMapElement) {
                    // skip if element has no name
                    if (((ExtendedMapElement) element).id == 0L)
                        return false;
                    // skip any calculations if element has label position but it is not defined
                    if (((ExtendedMapElement) element).hasLabelPosition && label == null)
                        return false;
                }

                // skip unnecessary calculations if label is outside of visible area
                if (label != null && (label.x < 0 || label.x > Tile.SIZE || label.y < 0 || label.y > Tile.SIZE))
                    return false;

                if (text.areaSize > 0f) {
                    float area = element.area();
                    float ratio = area / (Tile.SIZE * Tile.SIZE); // we can't use static as it's recalculated based on dpi
                    if (ratio < text.areaSize)
                        return false;
                }

                String value = getTextValue(element, text.textKey);
                if (value == null)
                    return false;

                if (label == null)
                    label = PolyLabel.get(element);

                ld.labels.push(TextItem.pool.get().set(label.x, label.y, value, text));
            } else if (element.type == POINT) {
                String value = getTextValue(element, text.textKey);
                if (value == null)
                    return false;

                for (int i = 0, n = element.getNumPoints(); i < n; i++) {
                    PointF p = element.getPoint(i);
                    ld.labels.push(TextItem.pool.get().set(p.x, p.y, value, text));
                }
            }
        } else if (style instanceof SymbolStyle) {
            SymbolStyle symbol = (SymbolStyle) style.current();

            if (symbol.bitmap == null && symbol.texture == null)
                return false;

            LabelTileData ld = get(tile);

            if (element.type == POINT) {
                for (int i = 0, n = element.getNumPoints(); i < n; i++) {
                    PointF p = element.getPoint(i);

                    SymbolItem it = SymbolItem.pool.get();
                    if (symbol.bitmap != null)
                        it.set(p.x, p.y, symbol.bitmap, true);
                    else
                        it.set(p.x, p.y, symbol.texture, true);
                    ld.symbols.push(it);
                }
            } else if (element.type == LINE) {
                //TODO: implement
            } else if (element.type == POLY) {
                PointF centroid = element.labelPosition;
                if (centroid == null)
                    return false;

                if (centroid.x < 0 || centroid.x > Tile.SIZE || centroid.y < 0 || centroid.y > Tile.SIZE)
                    return false;

                SymbolItem it = SymbolItem.pool.get();
                if (symbol.bitmap != null)
                    it.set(centroid.x, centroid.y, symbol.bitmap, true);
                else
                    it.set(centroid.x, centroid.y, symbol.texture, true);
                ld.symbols.push(it);
            }
        }
        return false;
    }

    @Override
    public void complete(MapTile tile, boolean success) {
    }

    private String getTextValue(MapElement element, String key) {
        String value = element.tags.getValue(key);
        if (value != null) {
            if (value.length() == 0)
                return null;
            return value;
        }
        if ("name".equals(key) && element instanceof ExtendedMapElement) {
            ExtendedMapElement extendedElement = (ExtendedMapElement) element;
            if (extendedElement.id == 0L)
                return null;
            String[] names = extendedElement.database.getNames(mLang, extendedElement.id);
            if (names != null) {
                if (names.length == 2 && names[1] != null)
                    return names[1];
                return names[0];
            }
        }
        if ("ele".equals(key) && element instanceof ExtendedMapElement) {
            ExtendedMapElement extendedElement = (ExtendedMapElement) element;
            if (extendedElement.elevation != 0) {
                //TODO Replace with kind flag
                if (element.tags.containsKey("contour"))
                    return StringFormatter.elevationC(extendedElement.elevation);
                else
                    return StringFormatter.elevationH(extendedElement.elevation);
            }

        }
        return null;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        if (preferredLanguage == null) {
            mLang = 0;
            return;
        }
        switch (preferredLanguage) {
            case "en":
                mLang = 840;
                return;
            case "de":
                mLang = 276;
                return;
            case "ru":
                mLang = 643;
        }
    }
}
