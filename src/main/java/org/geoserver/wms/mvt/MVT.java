package org.geoserver.wms.mvt;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** MVT constants (mimetype and output formats) */
public interface MVT {

    // Custom primary MIME type for this plugin
    String MIME_TYPE = "";

    // Supported aliases (none of these clash with the official extension)
    Set<String> OUTPUT_FORMATS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    MIME_TYPE,
                                    "application/x-mvt-custom",
                                    "application/x-mvt-pbf",
                                    "application/x-protobuf;type=mvt",
                                    "application/x-mapbox-vector-tile-custom")));
}
