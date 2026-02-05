package org.geoserver.wms.mvt;

import java.util.Set;

/** MVT constants (mimetype and output formats) */
public interface MVT {
    String MIME_TYPE = "application/x-mvt-custom";
    Set<String> OUTPUT_FORMATS = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(MIME_TYPE, "application/x-mvt-pbf")));
}
