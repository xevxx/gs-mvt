package org.geoserver.wms.mvt.runtime;

import java.util.Locale;
import java.util.Map;
import org.geoserver.wms.GetMapRequest;

/** Environment configuration abstraction used by runtime adapters. */
public interface ConfigurationSource {

    String getProperty(String key, String defaultValue);

    default boolean getBoolean(String key, boolean defaultValue) {
        String value = getProperty(key, null);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "yes".equals(normalized)
                || "on".equals(normalized);
    }

    /**
     * Returns request ENV values normalized for consistent key lookups regardless of source casing.
     */
    Map<String, Object> requestEnv(GetMapRequest request);
}
