package org.geoserver.slippymap;

import static org.geoserver.wms.mvt.MVTStreamingMapResponse.AVOID_EMPTY_PROTO;
import static org.geoserver.wms.mvt.MVTStreamingMapResponse.PARAM_GENERALISATION_FACTOR;
import static org.geoserver.wms.mvt.MVTStreamingMapResponse.PARAM_GENERALISATION_LEVEL;
import static org.geoserver.wms.mvt.MVTStreamingMapResponse.PARAM_SMALL_GEOM_THRESHOLD;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import org.geoserver.wms.mvt.EnvironmentConfig;
import org.geotools.api.referencing.ReferenceIdentifier;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Polygon;

/**
 * Shared slippy-map request parsing that stays free of servlet namespace imports so the same logic
 * can be used from both GeoServer 2 (`javax.servlet`) and GeoServer 3 (`jakarta.servlet`).
 */
public abstract class AbstractSlippyTilesController {

    protected interface RequestAdapter {
        String getRequestURI();

        String getContextPath();

        String getServletPath();

        String getParameter(String name);
    }

    private int defaultBuffer = EnvironmentConfig.getInt("GS_MVT_DEFAULT_BUFFER", 10);
    private String defaultFormat = org.geoserver.wms.mvt.MVT.MIME_TYPE;
    private String defaultStyles = EnvironmentConfig.getString("GS_MVT_DEFAULT_STYLES", "");
    private int defaultPbfTileSize = EnvironmentConfig.getInt("GS_MVT_DEFAULT_TILE_SIZE", 256);
    private Map<String, String> supportedOutputFormats;
    private Map<String, String> defaultTileSize;

    protected String buildForwardPath(RequestAdapter request) throws IOException {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        String servlet = request.getServletPath() == null ? "" : request.getServletPath();
        String path = uri.substring((ctx + servlet).length());

        String[] parts = path.split("/");
        if (parts.length < 6 || !"/slippymap".equals("/" + parts[1])) {
            return null;
        }

        String layers = urlDecode(parts[2]);

        int z;
        int x;
        int y;
        String ext;
        try {
            z = Integer.parseInt(parts[3]);
            x = Integer.parseInt(parts[4]);

            String yext = parts[5];
            int dot = yext.lastIndexOf('.');
            if (dot < 0) {
                return null;
            }

            y = Integer.parseInt(yext.substring(0, dot));
            ext = yext.substring(dot + 1).toLowerCase();
        } catch (RuntimeException e) {
            return null;
        }

        Integer buffer = optInt(request.getParameter("buffer"));
        Integer tileSize = optInt(request.getParameter("tileSize"));
        String styles = opt(request.getParameter("styles"), defaultStyles);
        String time = request.getParameter("time");
        String sld = request.getParameter("sld");
        String sldBody = request.getParameter("sld_body");
        String cqlFilter = request.getParameter("cql_filter");
        boolean bboxToBoundsViewparam =
                "true".equalsIgnoreCase(opt(request.getParameter("bboxToBoundsViewparam"), "false"));
        String viewParams = request.getParameter("viewparams");

        String genFactor = request.getParameter(PARAM_GENERALISATION_FACTOR);
        String genLevel = request.getParameter(PARAM_GENERALISATION_LEVEL);
        String smallGeomThreshold = request.getParameter(PARAM_SMALL_GEOM_THRESHOLD);
        String avoidEmptyProto = request.getParameter(AVOID_EMPTY_PROTO);

        String mime = supportedOutputFormats != null ? supportedOutputFormats.get(ext) : null;
        if (mime == null) {
            mime = defaultFormat;
        }

        ReferencedEnvelope bbox = SlippyMapTileCalculator.tile2boundingBox(x, y, z, 3857);

        String resolvedTileSize =
                tileSize != null
                        ? String.valueOf(tileSize)
                        : defaultTileSize != null
                                ? defaultTileSize.getOrDefault(ext, String.valueOf(defaultPbfTileSize))
                                : String.valueOf(defaultPbfTileSize);

        StringBuilder sb = new StringBuilder("/wms?request=GetMap&service=WMS&version=1.1.1");
        sb.append("&format=").append(urlEncode(mime));
        sb.append("&layers=").append(urlEncode(layers));
        sb.append("&styles=").append(urlEncode(styles));
        sb.append("&width=").append(resolvedTileSize);
        sb.append("&height=").append(resolvedTileSize);
        sb.append("&srs=").append(getCRSIdentifier(bbox.getCoordinateReferenceSystem()));
        sb.append("&bbox=")
                .append(bbox.getMinX())
                .append(',')
                .append(bbox.getMinY())
                .append(',')
                .append(bbox.getMaxX())
                .append(',')
                .append(bbox.getMaxY());

        sb.append("&buffer=").append(buffer != null ? buffer : defaultBuffer);
        if (time != null) {
            sb.append("&time=").append(urlEncode(time));
        }
        if (sld != null) {
            sb.append("&sld=").append(urlEncode(sld));
        }
        if (sldBody != null) {
            sb.append("&sld_body=").append(urlEncode(sldBody));
        }
        if (cqlFilter != null) {
            sb.append("&cql_filter=").append(urlEncode(cqlFilter));
        }

        StringBuilder env = new StringBuilder();
        appendEnv(env, PARAM_GENERALISATION_FACTOR, genFactor);
        appendEnv(env, PARAM_GENERALISATION_LEVEL, genLevel);
        appendEnv(env, PARAM_SMALL_GEOM_THRESHOLD, smallGeomThreshold);
        appendEnv(env, AVOID_EMPTY_PROTO, avoidEmptyProto);
        if (env.length() > 0) {
            sb.append("&env=").append(urlEncode(env.toString()));
        }

        if (bboxToBoundsViewparam && viewParams == null) {
            sb.append("&viewparams=").append(buildBoundsViewparam(bbox));
        } else if (!bboxToBoundsViewparam && viewParams != null) {
            sb.append("&viewparams=").append(urlEncode(viewParams));
        } else if (bboxToBoundsViewparam) {
            sb.append("&viewparams=").append(viewParams);
            if (!viewParams.endsWith(";")) {
                sb.append(';');
            }
            sb.append(buildBoundsViewparam(bbox));
        }

        return sb.toString();
    }

    private static Integer optInt(String s) {
        try {
            return (s == null || s.isEmpty()) ? null : Integer.valueOf(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String opt(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private static String urlEncode(String s) throws UnsupportedEncodingException {
        return s == null ? "" : URLEncoder.encode(s, "UTF-8");
    }

    private static String urlDecode(String s) throws UnsupportedEncodingException {
        return s == null ? "" : URLDecoder.decode(s, "UTF-8");
    }

    private static void appendEnv(StringBuilder env, String key, String value) {
        if (value == null) {
            return;
        }
        if (env.length() > 0) {
            env.append(';');
        }
        env.append(key).append(':').append(value);
    }

    private String buildBoundsViewparam(ReferencedEnvelope bbox) throws UnsupportedEncodingException {
        Polygon poly = JTS.toGeometry(bbox);
        String boundsWkt = "bounds:'" + poly.toText() + "';";
        boundsWkt = boundsWkt.replaceAll(", ", "|");
        return URLEncoder.encode(boundsWkt, "UTF-8");
    }

    private String getCRSIdentifier(CoordinateReferenceSystem crs) {
        if (crs.getIdentifiers() == null || crs.getIdentifiers().isEmpty()) {
            return crs.getName() != null ? crs.getName().toString() : "";
        }

        for (ReferenceIdentifier identifier : crs.getIdentifiers()) {
            return identifier.toString();
        }

        return "";
    }

    public void setDefaultBuffer(int defaultBuffer) {
        this.defaultBuffer = defaultBuffer;
    }

    public void setDefaultFormat(String defaultFormat) {
        this.defaultFormat = defaultFormat;
    }

    public void setDefaultStyles(String defaultStyles) {
        this.defaultStyles = defaultStyles;
    }

    public void setSupportedOutputFormats(Map<String, String> supportedOutputFormats) {
        this.supportedOutputFormats = supportedOutputFormats;
    }

    public void setDefaultTileSize(Map<String, String> defaultTileSize) {
        this.defaultTileSize = defaultTileSize;
    }
}
