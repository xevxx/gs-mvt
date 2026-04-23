package org.geoserver.wms.mvt;

import com.google.common.math.LongMath;
import java.io.IOException;
import java.io.OutputStream;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.WebMap;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.style.FeatureTypeStyle;
import org.geotools.api.style.Rule;
import org.geotools.api.style.Style;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.logging.Logging;

/**
 * Adapted WebMap implementation. Gets the style and retrieves filter rules. These rules are considered when loading
 * features from the datastore.
 */
public class StreamingMVTMap extends WebMap {

    private static final Logger LOGGER = Logging.getLogger(StreamingMVTMap.class);

    /** Tile-local CRS extent, configurable at service level. */
    private static final int targetBinaryCRSTileSize = EnvironmentConfig.getInt("GS_MVT_TILE_EXTENT", 256);

    /** @param context the map context, can be {@code null} is there's _really_ no context around */
    public StreamingMVTMap(WMSMapContent context) {
        super(context);
    }

    /**
     * Retrieves the feature from the underlying datasource and encodes them the MVT PBF format.
     *
     * @param out the outputstream to write to
     * @param avoidEmptyProto indicates that if no feature has to be serialized a not empty protobuf is generated (by
     *     adding the layer element which is valid in vector tiles spec)
     * @param smallGeometryThreshold defines the threshold in length / area when geometries should be skipped in output.
     *     0 or negative means all geoms are included
     * @param genFactors map of generalization factors per zoom level
     * @param fallBackGen fallback value if no suiting value can be found in genFactors map
     * @throws IOException
     */
    public void encode(
            final OutputStream out,
            boolean avoidEmptyProto,
            double smallGeometryThreshold,
            Map<Integer, Double> genFactors,
            double fallBackGen)
            throws IOException {
   
        int zoomLevel = getZoomLevel(this.mapContent.getScaleDenominator());
        double genFactor;
        if (zoomLevel >= 1 && zoomLevel <= 20) {
            genFactor = genFactors.get(zoomLevel);
        } else {
            genFactor = fallBackGen;
            LOGGER.warning("computed zoom level ("
                    + zoomLevel
                    + ") is out of range, using default generalisation ("
                    + fallBackGen
                    + ")");
        }
        // Delegate to the second overload (which DOES handle smallGeomMode / encThreshold)
        this.encode(out, avoidEmptyProto, smallGeometryThreshold, genFactor);
    }

    /**
     * Retrieves the feature from the underlying datasource and encodes them the MVT PBF format.
     *
     * @param out the outputstream to write to
     * @param avoidEmptyProto indicates that if no feature has to be serialized a not empty protobuf is generated (by
     *     adding the layer element which is valid in vector tiles spec)
     * @param smallGeometryThreshold threshold for skipping small geometries
     * @param genFactor the factor for generalisation
     * @throws IOException
     */
    public void encode(final OutputStream out, boolean avoidEmptyProto, double smallGeometryThreshold, double genFactor)
            throws IOException {
        LOGGER.info("MVT write HIT; starting to stream bytes");
        ReferencedEnvelope renderingArea = this.mapContent.getRenderingArea();
        try {
            // ---- ENV (normalize to lowercase keys)
            GetMapRequest req = this.mapContent != null ? this.mapContent.getRequest() : null;
            java.util.Map<String, Object> rawEnv =
                    (req != null && req.getEnv() != null) ? req.getEnv() : java.util.Collections.emptyMap();

            java.util.Map<String, Object> env = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, Object> e : rawEnv.entrySet()) {
                env.put(e.getKey().toLowerCase(), e.getValue());
            }

            // ---- read flags
            String smallGeomMode = "drop";
            Object modeVal = env.get("small_geom_mode");
            if (modeVal != null) smallGeomMode = String.valueOf(modeVal).trim().toLowerCase();

            int pixelSize = 1;
            Object pxVal = env.get("pixel_size");
            if (pxVal != null) {
                try {
                    pixelSize =
                            Math.max(1, Integer.parseInt(String.valueOf(pxVal).trim()));
                } catch (Exception ignore) {
                }
            }

            boolean stripAttributes = false;
            Object saVal = env.get("strip_attributes");
            if (saVal != null) {
                String s = String.valueOf(saVal).trim().toLowerCase();
                stripAttributes = "true".equals(s) || "1".equals(s) || "yes".equals(s);
            }

            boolean pixelAsPoint = false;
            Object papVal = env.get("pixel_as_point");
            if (papVal != null) {
                String s = String.valueOf(papVal).trim().toLowerCase();
                pixelAsPoint = "true".equals(s) || "1".equals(s) || "yes".equals(s);
            }

            // KEEP_ATTRS whitelist (optional)
            java.util.Set<String> keepAttrs = new java.util.LinkedHashSet<>();
            Object keepEnv = env.get("keep_attrs");
            if (keepEnv != null) {
                for (String k : keepEnv.toString().split(",")) {
                    String t = k.trim();
                    if (!t.isEmpty()) keepAttrs.add(t);
                }
            }

            // ---- encoder threshold: 0 when in pixel mode so placeholders aren't dropped
            double encThreshold = "pixel".equals(smallGeomMode) ? 0.0 : smallGeometryThreshold;

            MVTWriter mvtWriter = MVTWriter.getInstance(
                    renderingArea,
                    this.mapContent.getCoordinateReferenceSystem(),
                    targetBinaryCRSTileSize,
                    targetBinaryCRSTileSize,
                    this.mapContent.getBuffer(),
                    avoidEmptyProto,
                    genFactor,
                    encThreshold);

            // writer uses original threshold to *detect* small geoms
            mvtWriter.setSmallGeometryThreshold(smallGeometryThreshold);
            mvtWriter.setSmallGeomMode(smallGeomMode);
            mvtWriter.setPixelSize(pixelSize);
            mvtWriter.setStripAttributes(stripAttributes);
            mvtWriter.setPixelAsPoint(pixelAsPoint);
            mvtWriter.setKeepAttrs(keepAttrs);

            // ---- fetch features & write (unchanged)
            java.util.Map<org.geotools.feature.FeatureCollection, org.geotools.api.style.Style>
                    featureCollectionStyleMap = new java.util.HashMap<>();
            org.geotools.api.filter.FilterFactory ff = org.geotools.factory.CommonFactoryFinder.getFilterFactory();

            for (org.geotools.map.Layer layer : this.mapContent.layers()) {
                org.geotools.api.data.SimpleFeatureSource featureSource =
                        (org.geotools.api.data.SimpleFeatureSource) layer.getFeatureSource();
                org.geotools.api.feature.simple.SimpleFeatureType schema = featureSource.getSchema();
                String defaultGeometry =
                        schema.getGeometryDescriptor().getName().getLocalPart();

                renderingArea = mvtWriter.getSourceBBOXWithBuffer() != null
                        ? mvtWriter.getSourceBBOXWithBuffer()
                        : renderingArea;

                org.geotools.api.filter.spatial.BBOX bboxFilter = ff.bbox(ff.property(defaultGeometry), renderingArea);
                org.geotools.api.data.Query bboxQuery =
                        new org.geotools.api.data.Query(schema.getTypeName(), bboxFilter);
                org.geotools.api.data.Query definitionQuery = layer.getQuery();
                org.geotools.api.data.Query finalQuery = new org.geotools.api.data.Query(
                        org.geotools.data.DataUtilities.mixQueries(definitionQuery, bboxQuery, "mvtEncoder"));

                if (layer.getStyle() != null) {
                    org.geotools.api.filter.Filter styleFilter =
                            getFeatureFilterFromStyle(layer.getStyle(), ff, this.mapContent.getScaleDenominator());
                    if (styleFilter != null) {
                        org.geotools.api.data.Query filterQuery =
                                new org.geotools.api.data.Query(schema.getTypeName(), styleFilter);
                        finalQuery = new org.geotools.api.data.Query(
                                org.geotools.data.DataUtilities.mixQueries(finalQuery, filterQuery, "mvtEncoder"));
                    }
                }

                finalQuery.setCoordinateSystemReproject(mvtWriter.getTargetCRS());
                finalQuery.setHints(definitionQuery.getHints());
                finalQuery.setSortBy(definitionQuery.getSortBy());
                finalQuery.setStartIndex(definitionQuery.getStartIndex());

                featureCollectionStyleMap.put(featureSource.getFeatures(finalQuery), layer.getStyle());
            }

            mvtWriter.writeFeatures(featureCollectionStyleMap, this.mapContent.getScaleDenominator(), out);

        } catch (org.geotools.api.referencing.operation.TransformException
                | org.geotools.api.referencing.FactoryException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "MVT encode failed", e);
            throw new IOException("MVT encode failed", e);
        }
    }

    private int getZoomLevel(double scale) {
        double maxRes = 156543.03;
        double rs = scale / (96 * 39.37);
        int zoom = LongMath.log2((long) (maxRes / rs), RoundingMode.HALF_UP);
        return zoom;
    }

    /**
     * Retrieve Filter information from the Layer Style. TODO maybe there is a better method to do that e.g. using a
     * {@link org.geotools.styling.StyleVisitor}
     *
     * @param style the style of the layer
     * @param ff the filter factory to create (concat) filters
     * @param currentScaleDenominator the current scale denominator of the reuquested tiles
     * @return The filter containing all relevant filters for the current solutions or null if no filter is difined.
     */
    private Filter getFeatureFilterFromStyle(Style style, FilterFactory ff, double currentScaleDenominator) {
        List<Filter> filter = new ArrayList<>();
        for (FeatureTypeStyle featureTypeStyle : style.featureTypeStyles()) {
            for (Rule rule : featureTypeStyle.rules()) {
                if ((rule.getMaxScaleDenominator() < Double.POSITIVE_INFINITY
                                && currentScaleDenominator < rule.getMaxScaleDenominator())
                        || (rule.getMinScaleDenominator() > 0
                                && currentScaleDenominator > rule.getMinScaleDenominator())) {
                    if (rule.getFilter() != null) {
                        filter.add(rule.getFilter());
                    }
                } else if (rule.getMinScaleDenominator() == 0
                        && rule.getMaxScaleDenominator() == Double.POSITIVE_INFINITY) {
                    // No Scale denominator defined so render all
                    if (rule.getFilter() == null) {
                        return null;
                    } else {
                        filter.add(rule.getFilter());
                    }
                }
            }
        }
        return filter.isEmpty() ? null : ff.or(filter);
    }
}
