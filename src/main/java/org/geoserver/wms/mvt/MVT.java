package org.geoserver.wms.mvt;

import java.util.Set;

/** MVT constants (mimetype and output formats) */
public final class MVT {
    public static final String MIME_TYPE =
            EnvironmentConfig.getString("GS_MVT_MIME_TYPE", "application/x-mvt-custom");
    public static final Set<String> OUTPUT_FORMATS = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(MIME_TYPE, "application/x-mvt-pbf")));

    private MVT() {}
}
