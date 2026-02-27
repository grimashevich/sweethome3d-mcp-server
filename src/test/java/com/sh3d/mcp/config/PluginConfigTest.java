package com.sh3d.mcp.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {

    @Test
    void testDefaultValues() {
        PluginConfig config = PluginConfig.load();
        assertEquals(PluginConfig.DEFAULT_PORT, config.getPort());
        assertTrue(config.isAutoStart());
    }

    @Test
    void testSystemPropertyOverride() {
        System.setProperty("sh3d.mcp.port", "9999");
        try {
            PluginConfig config = PluginConfig.load();
            assertEquals(9999, config.getPort());
        } finally {
            System.clearProperty("sh3d.mcp.port");
        }
    }

    @Test
    void testResolveConfigPathReturnsValidPath() {
        Path path = PluginConfig.resolveConfigPath();
        assertNotNull(path);
        String pathStr = path.toString();
        // На Windows будет APPDATA путь, на Linux/macOS — user.home путь
        assertTrue(pathStr.endsWith("sh3d-mcp.properties"),
                "Config path should end with sh3d-mcp.properties, got: " + pathStr);
    }

    @Test
    void testDefaultLogLevel() {
        PluginConfig config = PluginConfig.load();
        assertEquals("INFO", config.getLogLevel());
    }

    @Test
    void testLogLevelSystemPropertyOverride() {
        System.setProperty("sh3d.mcp.logLevel", "FINE");
        try {
            PluginConfig config = PluginConfig.load();
            assertEquals("FINE", config.getLogLevel());
        } finally {
            System.clearProperty("sh3d.mcp.logLevel");
        }
    }

    @Test
    void testResolveLogPath() {
        Path path = PluginConfig.resolveLogPath();
        assertNotNull(path);
        String pathStr = path.toString();
        assertTrue(pathStr.endsWith("sh3d-mcp.log"),
                "Log path should end with sh3d-mcp.log, got: " + pathStr);
    }
}
