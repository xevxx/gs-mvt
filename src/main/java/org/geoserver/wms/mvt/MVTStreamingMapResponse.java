package org.geoserver.wms.mvt;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import org.apache.commons.lang.math.NumberUtils;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GeneralisationLevel;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.map.AbstractMapResponse;
import org.geotools.util.logging.Logging;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/** The Streaming Map Response using the StreamingMVTMap for retrieving and encoding the features. */
public class MVTStreamingMapResponse extends AbstractMapResponse {

    private static final Logger LOGGER = Logging.getLogger(MVTStreamingMapResponse.class);

    public static final double DEFAULT_GENERALISATION_FACTOR = 0.1;
    public static final double DEFAULT_SMALL_GEOMETRY_THRESHOLD = 0.05;
    public static final String PARAM_GENERALISATION_FACTOR = "gen_factor";
    public static final String PARAM_GENERALISATION_LEVEL = "gen_level";
    public static final String PARAM_SMALL_GEOM_THRESHOLD = "small_geom_threshold";
    public static final String AVOID_EMPTY_PROTO = "avoid_empty_proto";

    // New ENV keys
    private static final String ENV_SMALL_GEOM_MODE = "small_geom_mode"; // drop | pixel | keep
    private static final String ENV_PIXEL_SIZE = "pixel_size"; // integer pixels
    private static final String ENV_STRIP_ATTRS = "strip_attributes"; // true | false

    private GeneralisationLevel defaultGenLevel;
    private Map<GeneralisationLevel, Map<Integer, Double>> generalisationTables;

    @PostConstruct
    public void init() {
        System.err.println("gs-mvt: MVTStreamingMapResponse bean created");
    }
    // Mode enum we pass to StreamingMVTMap
    public enum SmallGeomMode {
        DROP,
        PIXEL,
        KEEP
    }

    public MVTStreamingMapResponse() {
        super(StreamingMVTMap.class, MVT.OUTPUT_FORMATS);
    }

    @Override
    public String getMimeType(Object value, Operation operation) {
        return MVT.MIME_TYPE;
    }

    @Override
    public void write(Object value, OutputStream output, Operation operation) throws IOException, ServiceException {
        System.err.println("gs-mvt: MapResponse.write HIT");
        StreamingMVTMap map = (StreamingMVTMap) value;

        Double genFactor = null;
        Double smallGeometryThreshold = DEFAULT_SMALL_GEOMETRY_THRESHOLD;
        Boolean avoidEmptyProto = false;

        Map<Integer, Double> genFactorTable = getGenFactorForGenLevel(defaultGenLevel);

        if (operation.getParameters()[0] instanceof GetMapRequest) {
            GetMapRequest request = (GetMapRequest) operation.getParameters()[0];

            // ---- make ENV lookups case-insensitive
            Map<String, Object> rawEnv = request.getEnv();
            Map<String, Object> env = new java.util.HashMap<>();
            if (rawEnv != null) {
                for (Map.Entry<String, Object> e : rawEnv.entrySet()) {
                    env.put(e.getKey().toLowerCase(), e.getValue());
                }
            }

            // existing: gen factor / level
            Object reqGenFactor = env.get(PARAM_GENERALISATION_FACTOR); // keys are already lower
            Object reqGenLevel = env.get(PARAM_GENERALISATION_LEVEL);

            if (reqGenFactor != null && reqGenFactor instanceof String && NumberUtils.isNumber((String) reqGenFactor)) {
                genFactor = NumberUtils.toDouble((String) reqGenFactor, DEFAULT_GENERALISATION_FACTOR);
            } else if (reqGenLevel != null) {
                genFactorTable = getGenFactorForRequestedLevel(reqGenLevel);
            }

            // existing: small geom threshold
            Object reqSkipSmallGeoms = env.get(PARAM_SMALL_GEOM_THRESHOLD);
            if (reqSkipSmallGeoms != null) {
                smallGeometryThreshold =
                        NumberUtils.toDouble(String.valueOf(reqSkipSmallGeoms), DEFAULT_SMALL_GEOMETRY_THRESHOLD);
            }

            // existing: avoid empty proto
            Object reqAvoidEmptyProto = env.get(AVOID_EMPTY_PROTO);
            if (reqAvoidEmptyProto != null) {
                avoidEmptyProto = Boolean.parseBoolean(reqAvoidEmptyProto.toString());
            }

            // ---- NEW: mode/pixel/strip (read only to adjust effective threshold here;
            // actual flags are read again in StreamingMVTMap and applied to MVTWriter)
            String smallGeomMode = "drop";
            Object modeVal = env.get("small_geom_mode");
            if (modeVal != null) smallGeomMode = String.valueOf(modeVal).trim().toLowerCase();

            // optional: quick debug
            LOGGER.fine("MVT ENV -> mode="
                    + smallGeomMode
                    + ", requested_threshold="
                    + DEFAULT_SMALL_GEOMETRY_THRESHOLD
                    + ", effective_threshold="
                    + smallGeometryThreshold);

            Set<String> keepAttrs = new LinkedHashSet<>();
            Map<String, Object> envKeep = request.getEnv();
            Object keepEnv = envKeep.containsKey("KEEP_ATTRS") ? envKeep.get("KEEP_ATTRS") : envKeep.get("keep_attrs");
            if (keepEnv != null) {
                // Decode URL-encoded commas/colons first
                String decoded =
                        java.net.URLDecoder.decode(keepEnv.toString(), java.nio.charset.StandardCharsets.UTF_8);
                for (String k : decoded.split(",")) {
                    String name = k.trim();
                    if (!name.isEmpty()) keepAttrs.add(name);
                }
                LOGGER.info("Decoded KEEP_ATTRS = " + keepAttrs);
            }
        }

        try {
            // keep the original encode signatures — do NOT add new overloads
            if (genFactor != null) {
                map.encode(output, avoidEmptyProto, smallGeometryThreshold, genFactor);
            } else {
                map.encode(
                        output, avoidEmptyProto, smallGeometryThreshold, genFactorTable, DEFAULT_GENERALISATION_FACTOR);
            }
        } finally {
            map.dispose();
        }
    }

    private Map<Integer, Double> getGenFactorForGenLevel(GeneralisationLevel genLevel) {
        return generalisationTables.get(genLevel);
    }

    // private Map<Integer, Double> getGenFactorForRequestedLevel(Object reqGenLevel) {
    //     GeneralisationLevel genLevel =
    //             GeneralisationLevel.valueOf(reqGenLevel.toString().toUpperCase());
    //     if (genLevel == null) {
    //         LOGGER.warning(
    //                 "requested generalisation level "
    //                         + reqGenLevel
    //                         + " is not a valid value (use \"low\", \"mid\", \"high\"). "
    //                         + "Default generalisation level (mid) will be used");
    //         genLevel = defaultGenLevel;
    //     }
    //     return getGenFactorForGenLevel(genLevel);
    // }
    private Map<Integer, Double> getGenFactorForRequestedLevel(Object reqGenLevel) {
        String key = String.valueOf(reqGenLevel).trim().toUpperCase();

        // --- Handle non-enum "MIN" / "MAX" first
        if ("MIN".equals(key)) {
            Map<Integer, Double> low = generalisationTables.get(GeneralisationLevel.LOW);
            return scaleTable(low, 0.5); // tune factor as you like (e.g., 0.5 = less simplification than LOW)
        }
        if ("MAX".equals(key)) {
            Map<Integer, Double> high = generalisationTables.get(GeneralisationLevel.HIGH);
            return scaleTable(high, 1.5); // tune factor as you like (e.g., 1.5 = more simplification than HIGH)
        }

        // --- Fallback to the enum levels
        GeneralisationLevel genLevel;
        try {
            genLevel = GeneralisationLevel.valueOf(key);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("requested generalisation level '"
                    + reqGenLevel
                    + "' is not valid (use 'min', 'low', 'mid', 'high', 'max'). "
                    + "Default generalisation level ("
                    + defaultGenLevel
                    + ") will be used.");
            genLevel = defaultGenLevel;
        }
        return getGenFactorForGenLevel(genLevel);
    }

    private Map<Integer, Double> scaleTable(Map<Integer, Double> base, double factor) {
        java.util.Map<Integer, Double> out = new java.util.HashMap<>();
        if (base != null) {
            for (java.util.Map.Entry<Integer, Double> e : base.entrySet()) {
                out.put(e.getKey(), e.getValue() * factor);
            }
        }
        return out;
    }

    public GeneralisationLevel getDefaultGenLevel() {
        return defaultGenLevel;
    }

    public void setDefaultGenLevel(GeneralisationLevel defaultGenLevel) {
        this.defaultGenLevel = defaultGenLevel;
    }

    public Map<GeneralisationLevel, Map<Integer, Double>> getGeneralisationTables() {
        return generalisationTables;
    }

    public void setGeneralisationTables(Map<GeneralisationLevel, Map<Integer, Double>> generalisationTables) {
        this.generalisationTables = generalisationTables;
    }

    // ---------- NEW helpers for ENV parsing ----------

    private SmallGeomMode smallGeomModeFromEnv(GetMapRequest req) {
        if (req == null || req.getEnv() == null) return SmallGeomMode.DROP; // legacy default
        Object v = req.getEnv().get(ENV_SMALL_GEOM_MODE);
        if (v == null) return SmallGeomMode.DROP;
        String s = String.valueOf(v).trim().toLowerCase();
        switch (s) {
            case "pixel":
                return SmallGeomMode.PIXEL;
            case "keep":
                return SmallGeomMode.KEEP;
            default:
                return SmallGeomMode.DROP;
        }
    }

    private int pixelSizeFromEnv(GetMapRequest req) {
        if (req == null || req.getEnv() == null) return 1;
        Object v = req.getEnv().get(ENV_PIXEL_SIZE);
        if (v == null) return 1;
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(v)));
        } catch (Exception e) {
            return 1;
        }
    }

    /** Whether to emit *no* properties at all */
    private boolean stripAttributesFromEnv(GetMapRequest req) {
        if (req == null || req.getEnv() == null) return false;
        Object v = req.getEnv().get("strip_attributes");
        if (v == null) return false;
        String s = String.valueOf(v).trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }

    private double pixelSizeWorldX(GetMapRequest req) {
        Envelope bbox = req.getBbox();
        if (bbox == null || req.getWidth() <= 0) {
            return 0d;
        }
        double worldWidth = bbox.getMaxX() - bbox.getMinX();
        return worldWidth / (double) req.getWidth(); // map units per pixel
    }

    private double pixelSizeWorldY(GetMapRequest req) {
        Envelope bbox = req.getBbox();
        if (bbox == null || req.getHeight() <= 0) {
            return 0d;
        }
        double worldHeight = bbox.getMaxY() - bbox.getMinY();
        return worldHeight / (double) req.getHeight(); // map units per pixel
    }

    private Geometry placeholderSameKind(Geometry original, GetMapRequest req, int pixelPx, GeometryFactory gf) {
        // center at centroid (already in map CRS at this stage)
        Coordinate c = original.getCentroid().getCoordinate();
        double halfX = 0.5 * pixelPx * pixelSizeWorldX(req);
        double halfY = 0.5 * pixelPx * pixelSizeWorldY(req);

        if (original instanceof Polygon || original instanceof MultiPolygon) {
            // Tiny axis-aligned square polygon (closed ring)
            Coordinate[] ring = new Coordinate[] {
                new Coordinate(c.x - halfX, c.y - halfY),
                new Coordinate(c.x + halfX, c.y - halfY),
                new Coordinate(c.x + halfX, c.y + halfY),
                new Coordinate(c.x - halfX, c.y + halfY),
                new Coordinate(c.x - halfX, c.y - halfY)
            };
            LinearRing shell = gf.createLinearRing(ring);
            Polygon poly = gf.createPolygon(shell, null);
            if (original instanceof MultiPolygon) {
                return gf.createMultiPolygon(new Polygon[] {poly});
            }
            return poly;
        }

        if (original instanceof LineString || original instanceof MultiLineString) {
            // Short horizontal dash; ensure non-zero length
            Coordinate a = new Coordinate(c.x - halfX, c.y);
            Coordinate b = new Coordinate(c.x + halfX, c.y);
            LineString line = gf.createLineString(new Coordinate[] {a, b});
            if (original instanceof MultiLineString) {
                return gf.createMultiLineString(new LineString[] {line});
            }
            return line;
        }

        // Points or anything else → point (or MultiPoint with one point)
        Point p = gf.createPoint(new Coordinate(c.x, c.y));
        if (original instanceof MultiPoint) {
            return gf.createMultiPointFromCoords(new Coordinate[] {p.getCoordinate()});
        }
        return p;
    }
}
