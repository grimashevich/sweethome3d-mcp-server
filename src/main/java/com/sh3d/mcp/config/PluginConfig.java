package com.sh3d.mcp.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Конфигурация плагина. Приоритет: System properties > файл > defaults.
 */
public class PluginConfig {

    public static final String PLUGIN_VERSION = "0.9.0";

    public static final int DEFAULT_PORT = 9877;
    public static final boolean DEFAULT_AUTO_START = true;
    public static final String DEFAULT_LOG_LEVEL = "INFO";

    private final int port;
    private final boolean autoStart;
    private final String logLevel;

    private PluginConfig(int port, boolean autoStart, String logLevel) {
        this.port = port;
        this.autoStart = autoStart;
        this.logLevel = logLevel;
    }

    /**
     * Загружает конфигурацию: System properties -> sh3d-mcp.properties -> defaults.
     */
    public static PluginConfig load() {
        Properties fileProps = loadPropertiesFile();

        int port = getInt("sh3d.mcp.port", fileProps, DEFAULT_PORT);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "Invalid port: " + port + " (must be 1-65535)");
        }
        boolean autoStart = getBoolean("sh3d.mcp.autoStart", fileProps, DEFAULT_AUTO_START);
        String logLevel = getString("sh3d.mcp.logLevel", fileProps, DEFAULT_LOG_LEVEL);

        return new PluginConfig(port, autoStart, logLevel);
    }

    public int getPort() {
        return port;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public String getLogLevel() {
        return logLevel;
    }

    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        Path configPath = resolveConfigPath();
        if (configPath != null && Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            } catch (IOException e) {
                // Игнорируем — используем defaults
            }
        }
        return props;
    }

    static Path resolveConfigPath() {
        Path dir = resolvePluginDir();
        return dir == null ? null : dir.resolve("sh3d-mcp.properties");
    }

    public static Path resolveLogPath() {
        Path dir = resolvePluginDir();
        return dir == null ? null : dir.resolve("sh3d-mcp.log");
    }

    private static Path resolvePluginDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isEmpty()) {
            return Paths.get(appData, "eTeks", "Sweet Home 3D", "plugins");
        }
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            return null;
        }
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.startsWith("mac")) {
            return Paths.get(home, "Library", "Application Support",
                    "eTeks", "Sweet Home 3D", "plugins");
        }
        return Paths.get(home, ".eteks", "sweethome3d", "plugins");
    }

    private static int getInt(String key, Properties fileProps, int defaultValue) {
        String sysVal = System.getProperty(key);
        if (sysVal != null) {
            try {
                return Integer.parseInt(sysVal);
            } catch (NumberFormatException e) {
                // fallthrough
            }
        }
        String fileVal = fileProps.getProperty(key);
        if (fileVal != null) {
            try {
                return Integer.parseInt(fileVal);
            } catch (NumberFormatException e) {
                // fallthrough
            }
        }
        return defaultValue;
    }

    private static String getString(String key, Properties fileProps, String defaultValue) {
        String sysVal = System.getProperty(key);
        if (sysVal != null) {
            return sysVal;
        }
        String fileVal = fileProps.getProperty(key);
        if (fileVal != null) {
            return fileVal;
        }
        return defaultValue;
    }

    private static boolean getBoolean(String key, Properties fileProps, boolean defaultValue) {
        String sysVal = System.getProperty(key);
        if (sysVal != null) {
            return Boolean.parseBoolean(sysVal);
        }
        String fileVal = fileProps.getProperty(key);
        if (fileVal != null) {
            return Boolean.parseBoolean(fileVal);
        }
        return defaultValue;
    }
}
