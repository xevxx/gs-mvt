package org.geoserver.wms.mvt.runtime;

import java.io.IOException;
import java.nio.file.Path;

/** Temp and storage abstraction for runtime-specific filesystem policies. */
public interface StorageAccess {

    Path tempDir() throws IOException;

    Path createTempFile(String prefix, String suffix) throws IOException;
}
