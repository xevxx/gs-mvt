package org.geoserver.wms.mvt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/**
 * Reads service-level configuration from environment variables and an optional mounted properties file.
 *
 * <p>Resolution order: environment variable -> mounted config file -> default value.
 */
public final class EnvironmentConfig {

    private static final Logger LOGGER = Logging.getLogger(EnvironmentConfig.class);
    private static final String CONFIG_FILE_ENV = "GS_MVT_CONFIG_FILE";

    private static final Properties FILE_CONFIG = loadFileConfig();

    private EnvironmentConfig() {}

    public static String getString(String envKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        String fileValue = FILE_CONFIG.getProperty(envKey);
        if (fileValue != null && !fileValue.trim().isEmpty()) {
            return fileValue.trim();
        }
        return defaultValue;
    }

    public static int getInt(String envKey, int defaultValue) {
        String raw = getString(envKey, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid integer for " + envKey + ": " + raw + ", using default " + defaultValue);
            return defaultValue;
        }
    }

    private static Properties loadFileConfig() {
        Properties props = new Properties();
        Optional<Path> configPath = findConfigPath();
        if (!configPath.isPresent()) {
            return props;
        }
        try (InputStream in = Files.newInputStream(configPath.get())) {
            props.load(in);
            LOGGER.info("Loaded gs-mvt config from " + configPath.get());
        } catch (IOException e) {
            LOGGER.warning("Could not read gs-mvt config file " + configPath.get() + ": " + e.getMessage());
        }
        return props;
    }

    private static Optional<Path> findConfigPath() {
        String explicit = System.getenv(CONFIG_FILE_ENV);
        if (explicit != null && !explicit.trim().isEmpty()) {
            Path p = Paths.get(explicit.trim());
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return Optional.of(p);
            }
            LOGGER.warning("Configured " + CONFIG_FILE_ENV + " path does not exist: " + p);
            return Optional.empty();
        }

        Path defaultPath = Paths.get("/etc/gs-mvt/gs-mvt.properties");
        if (Files.exists(defaultPath) && Files.isRegularFile(defaultPath)) {
            return Optional.of(defaultPath);
        }
        return Optional.empty();
    }
}
