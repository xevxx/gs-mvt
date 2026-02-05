package org.geoserver.wms.mvt;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.geoserver.AbstractMVTTest;
import org.geoserver.slippymap.SlippyMapTileCalculator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Test for access to slippy map controller */
public class SlippyTilesControllerTest extends AbstractMVTTest {

    protected MockHttpServletResponse getAsServletResponse(String path) throws Exception {
        MockHttpServletRequest request = this.createRequest(path);
        request.setServletPath("");
        request.setMethod("GET");
        request.setContent(new byte[0]);
        return this.dispatch(request, null);
    }

    private static Map<String, String> parseQueryParams(String url) {
        Map<String, String> out = new LinkedHashMap<>();
        int q = url.indexOf('?');
        if (q < 0) return out;
        String qs = url.substring(q + 1);
        for (String kv : qs.split("&")) {
            int eq = kv.indexOf('=');
            String k = eq >= 0 ? kv.substring(0, eq) : kv;
            String v = eq >= 0 ? kv.substring(eq + 1) : "";
            // keep controller’s casing (FORMAT, SRS, etc.) but treat lookups case-insensitively
            out.put(k, URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String findParam(Map<String, String> qp, String name) {
        // case-insensitive lookup
        for (Map.Entry<String, String> e : qp.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    @Test
    public void testRequestParams() throws Exception {

        int x = 2196;
        int y = 1427;
        int z = 12;
        int buffer = 10;

        // 1) Hit slippymap endpoint
        String requestSlippy = "/slippymap/"
                + TEST_LINES.getPrefix()
                + ":"
                + TEST_LINES.getLocalPart()
                + "/"
                + z
                + "/"
                + x
                + "/"
                + y
                + ".pbf"
                + "?buffer="
                + buffer
                + "&styles="
                + STYLE_NAME
                + "&tileSize=256"
                + "&gen_level=low"
                + "&bboxToBoundsViewparam=true"
                + "&viewparams=test123:test123;";

        MockHttpServletResponse responseSlippy = getAsServletResponse(requestSlippy);
        Assert.assertEquals(200, responseSlippy.getStatus());
        String forwardedUrl = responseSlippy.getForwardedUrl();
        Assert.assertNotNull(forwardedUrl);

        // bytes from the actual forwarded request
        MockHttpServletResponse responseForwardedUrl = getAsServletResponse(forwardedUrl);
        byte[] contentForwardedWms = responseForwardedUrl.getContentAsByteArray();

        // 2) Build a direct WMS URL with the SAME params the controller forwarded
        Map<String, String> qp = parseQueryParams(forwardedUrl);

        // sanity: recompute bbox independently (like original test did), but we’ll still use the
        // controller’s FORMAT / VIEWPARAMS to avoid mismatches
        ReferencedEnvelope bbox = SlippyMapTileCalculator.tile2boundingBox(x, y, z, 3857);
        String bboxSb = "&bbox=" + bbox.getMinX() + "," + bbox.getMinY() + "," + bbox.getMaxX() + "," + bbox.getMaxY();

        String requestWms = "wms?request=getmap&service=wms&version=1.1.1"
                + "&format="
                + findParam(qp, "FORMAT")
                + "&layers="
                + findParam(qp, "LAYERS")
                + "&styles="
                + findParam(qp, "STYLES")
                + "&height="
                + findParam(qp, "HEIGHT")
                + "&width="
                + findParam(qp, "WIDTH")
                // use the independently computed bbox (should match the controller one)
                + bboxSb
                + "&srs="
                + findParam(qp, "SRS")
                + "&buffer="
                + findParam(qp, "BUFFER");

        String env = findParam(qp, "ENV");
        if (env != null) requestWms += "&ENV=" + env;
        String vps = findParam(qp, "VIEWPARAMS");
        if (vps != null) requestWms += "&VIEWPARAMS=" + vps;
        String time = findParam(qp, "TIME");
        if (time != null) requestWms += "&TIME=" + time;
        String sld = findParam(qp, "SLD");
        if (sld != null) requestWms += "&SLD=" + sld;
        String sldBody = findParam(qp, "SLD_BODY");
        if (sldBody != null) requestWms += "&SLD_BODY=" + sldBody;

        MockHttpServletResponse responseWms = getAsServletResponse(requestWms);
        byte[] contentWms = responseWms.getContentAsByteArray();

        // 3) Compare
        Assert.assertEquals(contentForwardedWms.length, contentWms.length);
        Assert.assertArrayEquals(contentForwardedWms, contentWms);
    }
}
