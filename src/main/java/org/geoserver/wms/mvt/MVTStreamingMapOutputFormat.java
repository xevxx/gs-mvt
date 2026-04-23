package org.geoserver.wms.mvt;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.mvt.runtime.RuntimeAdapters;
import org.geoserver.wms.GetMapOutputFormat;
import org.geoserver.wms.MapProducerCapabilities;
import org.geoserver.wms.WMSMapContent;
import org.geotools.util.logging.Logging;

/** The Streaming map outputFormat class for WMS PBF files */
public class MVTStreamingMapOutputFormat implements GetMapOutputFormat {

    private static final Logger LOGGER = Logging.getLogger(MVTStreamingMapOutputFormat.class);

    private static MapProducerCapabilities CAPABILITIES =
            // new MapProducerCapabilities(false, false, false, false, null);
            new MapProducerCapabilities(false, false, false);

    /**
     * @return {@code ["application/x-protobuf", "application/pbf", "application/mvt"]}
     * @see org.geoserver.wms.GetMapOutputFormat#getOutputFormatNames()
     */
    public Set<String> getOutputFormatNames() {
        LOGGER.info("Registering MVT formats: " + MVT.OUTPUT_FORMATS);
        return MVT.OUTPUT_FORMATS;
    }

    @PostConstruct
    public void init() {
        try {
            LOGGER.info("gs-mvt: MVTStreamingMapOutputFormat bean created (adapter="
                    + RuntimeAdapters.current().flavor()
                    + ", tempDir="
                    + RuntimeAdapters.current().storage().tempDir()
                    + ")");
        } catch (IOException e) {
            LOGGER.warning("gs-mvt: unable to resolve adapter temp directory: " + e.getMessage());
        }
    }
    /**
     * @return {@code "application/x-protobuf"}
     * @see org.geoserver.wms.GetMapOutputFormat#getMimeType()
     */
    public String getMimeType() {
        return MVT.MIME_TYPE;
    }

    /** @see org.geoserver.wms.GetMapOutputFormat#produceMap(org.geoserver.wms.WMSMapContent) */
    public StreamingMVTMap produceMap(WMSMapContent mapContent) throws ServiceException, IOException {
        LOGGER.info("MVT produceMap HIT; reqFormat="
                + (mapContent.getRequest() != null ? mapContent.getRequest().getFormat() : "null")
                + " mime=" + getMimeType());
        StreamingMVTMap mvt = new StreamingMVTMap(mapContent);
        mvt.setMimeType(getMimeType());
        mvt.setContentDispositionHeader(mapContent, ".pbf", false);
        return mvt;
    }

    public MapProducerCapabilities getCapabilities(String format) {
        return CAPABILITIES;
    }
}
