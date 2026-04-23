package org.geoserver.wms.mvt.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.geoserver.wms.GetMapRequest;

/**
 * Cloud-native adapter: defaults to ephemeral writable volume paths and namespace-prefixed shared keys.
 */
public class CloudRuntimeAdapter implements RuntimeAdapter {

    private final ConfigurationSource configurationSource = new CloudConfigurationSource();
    private final StorageAccess storageAccess = new CloudStorageAccess(configurationSource);
    private final ClusterStateCoordinator clusterCoordinator = new NamespacedClusterStateCoordinator(configurationSource);

    @Override
    public String flavor() {
        return "cloud";
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

    static class CloudConfigurationSource extends LegacyRuntimeAdapter.LegacyConfigurationSource {

        @Override
        public String getProperty(String key, String defaultValue) {
            if ("gs.mvt.temp.dir".equals(key)) {
                String cloudDefault = super.getProperty("GS_MVT_TEMP_DIR", null);
                if (cloudDefault != null) {
                    return cloudDefault;
                }
                return super.getProperty(key, "/tmp/gs-mvt");
            }
            return super.getProperty(key, defaultValue);
        }

        @Override
        public Map<String, Object> requestEnv(GetMapRequest request) {
            return super.requestEnv(request);
        }
    }

    static class CloudStorageAccess implements StorageAccess {
        private final ConfigurationSource config;

        CloudStorageAccess(ConfigurationSource config) {
            this.config = config;
        }

        @Override
        public Path tempDir() throws IOException {
            Path base = Path.of(config.getProperty("gs.mvt.temp.dir", "/tmp/gs-mvt"));
            Files.createDirectories(base);
            return base;
        }

        @Override
        public Path createTempFile(String prefix, String suffix) throws IOException {
            return Files.createTempFile(tempDir(), prefix, suffix);
        }
    }

    static class NamespacedClusterStateCoordinator implements ClusterStateCoordinator {
        private final String namespace;
        private final ConcurrentMap<String, Object> localState = new ConcurrentHashMap<>();

        NamespacedClusterStateCoordinator(ConfigurationSource config) {
            this.namespace = config.getProperty("gs.mvt.cluster.namespace", "default").toLowerCase(Locale.ROOT);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrCompute(String key, Supplier<T> loader) {
            return (T) localState.computeIfAbsent(namespaced(key), k -> loader.get());
        }

        @Override
        public void invalidate(String key) {
            localState.remove(namespaced(key));
        }

        private String namespaced(String key) {
            return namespace + ':' + key;
        }
    }
}
