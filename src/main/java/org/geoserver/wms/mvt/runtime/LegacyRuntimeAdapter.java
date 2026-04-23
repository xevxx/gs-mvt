package org.geoserver.wms.mvt.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.geoserver.wms.GetMapRequest;

/** Current single-node/on-prem behavior used as default adapter. */
public class LegacyRuntimeAdapter implements RuntimeAdapter {

    private final ConfigurationSource configurationSource = new LegacyConfigurationSource();
    private final StorageAccess storageAccess = new LegacyStorageAccess();
    private final ClusterStateCoordinator clusterCoordinator = new LocalClusterStateCoordinator();

    @Override
    public String flavor() {
        return "legacy";
    }

    @Override
    public ConfigurationSource configuration() {
        return configurationSource;
    }

    @Override
    public StorageAccess storage() {
        return storageAccess;
    }

    @Override
    public ClusterStateCoordinator clusterState() {
        return clusterCoordinator;
    }

    static class LegacyConfigurationSource implements ConfigurationSource {

        @Override
        public String getProperty(String key, String defaultValue) {
            String value = System.getProperty(key);
            if (value == null || value.trim().isEmpty()) {
                value = System.getenv(toEnvKey(key));
            }
            return value == null ? defaultValue : value;
        }

        @Override
        public Map<String, Object> requestEnv(GetMapRequest request) {
            if (request == null || request.getEnv() == null) {
                return Collections.emptyMap();
            }
            Map<String, Object> normalized = new HashMap<>();
            for (Map.Entry<String, Object> entry : request.getEnv().entrySet()) {
                normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
            return normalized;
        }

        private String toEnvKey(String key) {
            return key.replace('.', '_').toUpperCase(Locale.ROOT);
        }
    }

    static class LegacyStorageAccess implements StorageAccess {

        @Override
        public Path tempDir() throws IOException {
            return Path.of(System.getProperty("java.io.tmpdir"));
        }

        @Override
        public Path createTempFile(String prefix, String suffix) throws IOException {
            return Files.createTempFile(tempDir(), prefix, suffix);
        }
    }

    static class LocalClusterStateCoordinator implements ClusterStateCoordinator {

        private final ConcurrentMap<String, Object> cache = new ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrCompute(String key, Supplier<T> loader) {
            return (T) cache.computeIfAbsent(key, k -> loader.get());
        }

        @Override
        public void invalidate(String key) {
            cache.remove(key);
        }
    }
}
