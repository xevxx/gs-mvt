package org.geoserver.wms.mvt.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/** Adapter bootstrap and selection by runtime/build flavor. */
public final class RuntimeAdapters {

    private static final Logger LOGGER = Logging.getLogger(RuntimeAdapters.class);
    private static final String FLAVOR_PROP = "gs.mvt.runtime.flavor";
    private static final String FLAVOR_ENV = "GS_MVT_RUNTIME_FLAVOR";
    private static final String BUILD_PROPS_RESOURCE = "/mvt-runtime.properties";
    private static volatile RuntimeAdapter current;

    private RuntimeAdapters() {}

    public static RuntimeAdapter current() {
        RuntimeAdapter local = current;
        if (local == null) {
            synchronized (RuntimeAdapters.class) {
                local = current;
                if (local == null) {
                    local = select(flavorHint());
                    current = local;
                    LOGGER.info("gs-mvt runtime adapter selected: " + local.flavor());
                }
            }
        }
        return local;
    }

    static RuntimeAdapter select(String flavor) {
        String normalized = flavor == null ? "legacy" : flavor.trim().toLowerCase(Locale.ROOT);
        if ("cloud".equals(normalized)) {
            return new CloudRuntimeAdapter();
        }
        return new LegacyRuntimeAdapter();
    }

    static String flavorHint() {
        String runtime = System.getProperty(FLAVOR_PROP);
        if (runtime != null && !runtime.trim().isEmpty()) {
            return runtime;
        }

        runtime = System.getenv(FLAVOR_ENV);
        if (runtime != null && !runtime.trim().isEmpty()) {
            return runtime;
        }

        String buildFlavor = buildFlavor();
        if (buildFlavor != null && !buildFlavor.trim().isEmpty()) {
            return buildFlavor;
        }

        if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
            return "cloud";
        }

        return "legacy";
    }

    private static String buildFlavor() {
        Properties properties = new Properties();
        try (InputStream in = RuntimeAdapters.class.getResourceAsStream(BUILD_PROPS_RESOURCE)) {
            if (in == null) {
                return null;
            }
            properties.load(in);
            return properties.getProperty("gs.mvt.build.flavor");
        } catch (IOException e) {
            LOGGER.fine("Unable to read build flavor metadata: " + e.getMessage());
            return null;
        }
    }
}
