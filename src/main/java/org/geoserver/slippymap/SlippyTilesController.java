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
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geotools.api.referencing.ReferenceIdentifier;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Polygon;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.geoserver.wms.mvt.EnvironmentConfig;

/**
 * Slippy Map Tiles controller that converts /slippymap/{layers}/{z}/{x}/{y}.{ext} into a WMS GetMap forward, so WMS
 * security and output formats are reused.
 *
 * <p>This version is annotation-free to avoid requiring <mvc:annotation-driven/>, which can interfere with GeoServer
 * REST mappings.
 */
public class SlippyTilesController implements Controller {

    private int defaultBuffer = EnvironmentConfig.getInt("GS_MVT_DEFAULT_BUFFER", 10);
    private String defaultFormat = org.geoserver.wms.mvt.MVT.MIME_TYPE; // e.g. application/x-mvt-custom
    private String defaultStyles = EnvironmentConfig.getString("GS_MVT_DEFAULT_STYLES", "");
    private int defaultPbfTileSize = EnvironmentConfig.getInt("GS_MVT_DEFAULT_TILE_SIZE", 256);

    /** Mapping of file extensions (e.g. "pbf") to MIME types (e.g. application/x-mvt-custom). */
    private Map<String, String> supportedOutputFormats;

    /** Mapping of file extensions (e.g. "pbf") to default tile size (string, e.g. "256"). */
    private Map<String, String> defaultTileSize;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        // Expect path: /slippymap/{layers}/{z}/{x}/{y}.{ext}
        String uri = request.getRequestURI();
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        String servlet = request.getServletPath() == null ? "" : request.getServletPath();
        String path = uri.substring((ctx + servlet).length()); // should start with /slippymap/...

        String[] parts = path.split("/");
        if (parts.length < 6 || !"/slippymap".equals("/" + parts[1])) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        String layersEnc = parts[2];
        String layers = urlDecode(layersEnc);

        int z, x, y;
        String ext;
        try {
            z = Integer.parseInt(parts[3]);
            x = Integer.parseInt(parts[4]);

            String yext = parts[5];
            int dot = yext.lastIndexOf('.');
            if (dot < 0) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }
            y = Integer.parseInt(yext.substring(0, dot));
            ext = yext.substring(dot + 1).toLowerCase();
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        // Optional query params
        Integer buffer = optInt(request.getParameter("buffer"));
        Integer tileSize = optInt(request.getParameter("tileSize"));
        String styles = opt(request.getParameter("styles"), defaultStyles);
        String time = request.getParameter("time");
        String sld = request.getParameter("sld");
        String sld_body = request.getParameter("sld_body");
        String cql_filter = request.getParameter("cql_filter");
        boolean bboxToBoundsViewparam =
                "true".equalsIgnoreCase(opt(request.getParameter("bboxToBoundsViewparam"), "false"));
        String viewParams = request.getParameter("viewparams");

        // ENV parameters (plain strings)
        String gen_factor = request.getParameter(PARAM_GENERALISATION_FACTOR); // e.g. "0.7"
        String gen_level = request.getParameter(PARAM_GENERALISATION_LEVEL); // e.g. "low"
        String small_geom_threshold = request.getParameter(PARAM_SMALL_GEOM_THRESHOLD);
        String avoid_empty_proto = request.getParameter(AVOID_EMPTY_PROTO);

        // MIME by extension mapping, fallback to default
        String mime = (supportedOutputFormats != null) ? supportedOutputFormats.get(ext) : null;
        if (mime == null) mime = defaultFormat;

        // Compute bbox in EPSG:3857
        ReferencedEnvelope bbox = SlippyMapTileCalculator.tile2boundingBox(x, y, z, 3857);

        // Build WMS forward URL
        StringBuilder sb = new StringBuilder("/wms?request=GetMap&service=WMS&version=1.1.1");
        sb.append("&format=").append(urlEncode(mime));
        sb.append("&layers=").append(urlEncode(layers));
        sb.append("&styles=").append(urlEncode(styles));
        sb.append("&width=")
                .append(
                        tileSize != null
                                ? tileSize
                                : (defaultTileSize != null
                                        ? defaultTileSize.getOrDefault(ext, String.valueOf(defaultPbfTileSize))
                                        : String.valueOf(defaultPbfTileSize)));
        sb.append("&height=")
                .append(
                        tileSize != null
                                ? tileSize
                                : (defaultTileSize != null
                                        ? defaultTileSize.getOrDefault(ext, String.valueOf(defaultPbfTileSize))
                                        : String.valueOf(defaultPbfTileSize)));
        sb.append("&srs=").append(getCRSIdentifier(bbox.getCoordinateReferenceSystem()));
        sb.append("&bbox=")
                .append(bbox.getMinX())
                .append(',')
                .append(bbox.getMinY())
                .append(',')
                .append(bbox.getMaxX())
                .append(',')
                .append(bbox.getMaxY());

        if (buffer != null) sb.append("&buffer=").append(buffer);
        if (time != null) sb.append("&time=").append(urlEncode(time));
        if (sld != null) sb.append("&sld=").append(urlEncode(sld));
        if (sld_body != null) sb.append("&sld_body=").append(urlEncode(sld_body));
        if (cql_filter != null) sb.append("&cql_filter=").append(urlEncode(cql_filter));

        // ENV=param1:val;param2:val...
        StringBuilder env = new StringBuilder();
        if (gen_factor != null) appendEnv(env, PARAM_GENERALISATION_FACTOR, gen_factor);
        if (gen_level != null) appendEnv(env, PARAM_GENERALISATION_LEVEL, gen_level);
        if (small_geom_threshold != null) appendEnv(env, PARAM_SMALL_GEOM_THRESHOLD, small_geom_threshold);
        if (avoid_empty_proto != null) appendEnv(env, AVOID_EMPTY_PROTO, avoid_empty_proto);
        if (env.length() > 0) sb.append("&env=").append(urlEncode(env.toString()));

        // VIEWPARAMS merging — mirrors the original behavior
        if (bboxToBoundsViewparam && viewParams == null) {
            sb.append("&viewparams=").append(buildBoundsViewparam(bbox)); // already encoded inside
        } else if (!bboxToBoundsViewparam && viewParams != null) {
            sb.append("&viewparams=").append(urlEncode(viewParams));
        } else if (bboxToBoundsViewparam && viewParams != null) {
            sb.append("&viewparams=").append(viewParams);
            if (!viewParams.endsWith(";")) {
                sb.append(";");
            }
            sb.append(buildBoundsViewparam(bbox)); // encoded block appended
        }

        RequestDispatcher dispatcher = request.getRequestDispatcher(response.encodeRedirectURL(sb.toString()));
        dispatcher.forward(request, response);
        return null;
    }

    // --------- helpers ---------

    private static Integer optInt(String s) {
        try {
            return (s == null || s.isEmpty()) ? null : Integer.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String opt(String v, String d) {
        return (v == null || v.isEmpty()) ? d : v;
    }

    private static String urlEncode(String s) throws UnsupportedEncodingException {
        return s == null ? "" : URLEncoder.encode(s, "UTF-8");
    }

    private static String urlDecode(String s) throws UnsupportedEncodingException {
        return s == null ? "" : URLDecoder.decode(s, "UTF-8");
    }

    private static void appendEnv(StringBuilder env, String key, String value) {
        if (value == null) return;
        if (env.length() > 0) env.append(';');
        env.append(key).append(':').append(value);
    }

    private String buildBoundsViewparam(ReferencedEnvelope bbox) throws UnsupportedEncodingException {
        Polygon poly = JTS.toGeometry(bbox);
        String boundsWKT = "bounds:'" + poly.toText() + "';"; // e.g. bounds:'POLYGON(...)';
        boundsWKT = boundsWKT.replaceAll(", ", "|");
        return URLEncoder.encode(boundsWKT, "UTF-8");
    }

    private String getCRSIdentifier(CoordinateReferenceSystem crs) {
        String name = "";
        if (crs.getIdentifiers() == null || crs.getIdentifiers().isEmpty()) {
            name = crs.getName() != null ? crs.getName().toString() : "";
        } else {
            for (ReferenceIdentifier identifier : crs.getIdentifiers()) {
                name = identifier.toString();
                break;
            }
        }
        return name;
    }

    // --------- setters for Spring wiring ---------

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
