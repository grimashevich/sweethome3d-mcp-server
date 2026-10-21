package com.sh3d.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code sh3d.mcp.requireToken} по умолчанию равен {@code false}; приоритет источников соответствует
 * {@code PluginConfig}: системное свойство, затем файл {@code sh3d-mcp.properties}, затем значение по умолчанию.
 * {@link PluginConfig#isRequireToken()} возвращает значение; {@link PluginConfig#saveRequireToken(boolean)}
 * записывает его в файл, сохраняя остальные ключи. Каталог плагинов перенаправлен во временный домашний
 * каталог; настоящий домашний каталог не изменяется.
 */
class RequireTokenConfigSpecTest {

    private static final String KEY = "sh3d.mcp.requireToken";

    /** Redirects the plugins folder to a temp home, if the environment lets us (APPDATA unset). */
    private String redirectHome(Path home) {
        assumeTrue(System.getenv("APPDATA") == null || System.getenv("APPDATA").isEmpty(),
                "APPDATA is set; cannot redirect the plugins folder by user.home");
        String saved = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        Path dir = PluginConfig.resolveConfigPath();
        assumeTrue(dir != null && dir.startsWith(home), "config path not redirected into the temp home");
        return saved;
    }

    private void writeProperties(Properties props) throws IOException {
        Path path = PluginConfig.resolveConfigPath();
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "spec");
        }
    }

    private static Properties readProperties() throws IOException {
        Properties props = new Properties();
        try (java.io.InputStream in = Files.newInputStream(PluginConfig.resolveConfigPath())) {
            props.load(in);
        }
        return props;
    }

    private static java.util.Set<String> listFolder() throws IOException {
        java.util.Set<String> names = new java.util.TreeSet<>();
        try (java.util.stream.Stream<Path> s = Files.list(PluginConfig.resolveConfigPath().getParent())) {
            s.forEach(p -> names.add(p.getFileName().toString()));
        }
        return names;
    }

    // ==================== T8: saveRequireToken ====================

    // T8: no folder and no file — both are created, the value is written, and load() sees it.
    @Test
    void t8SaveCreatesFolderAndFile(@TempDir Path home) throws IOException {
        String saved = redirectHome(home);
        System.clearProperty(KEY);
        try {
            Path file = PluginConfig.resolveConfigPath();
            assertFalse(Files.exists(file.getParent()), "precondition: the plugins folder does not exist yet");

            PluginConfig.saveRequireToken(true);

            assertTrue(Files.isRegularFile(file), "sh3d-mcp.properties must be created");
            assertEquals("true", readProperties().getProperty(KEY));
            assertTrue(PluginConfig.load().isRequireToken());
        } finally {
            System.setProperty("user.home", saved);
        }
    }

    // T8: every other key — known and unknown — keeps its value; only requireToken changes, true and back to false.
    @Test
    void t8SaveKeepsAllOtherKeysAndValues(@TempDir Path home) throws IOException {
        String saved = redirectHome(home);
        System.clearProperty(KEY);
        try {
            Properties original = new Properties();
            original.setProperty("sh3d.mcp.port", "9988");
            original.setProperty("sh3d.mcp.autoStart", "false");
            original.setProperty("sh3d.mcp.logLevel", "FINE");
            original.setProperty("custom.unknown.key", "value with spaces = and: colon");
            original.setProperty("custom.unicode", "значение å");
            original.setProperty("custom.empty", "");
            original.setProperty(KEY, "false");
            writeProperties(original);

            PluginConfig.saveRequireToken(true);

            Properties after = readProperties();
            Properties expected = new Properties();
            expected.putAll(original);
            expected.setProperty(KEY, "true");
            assertEquals(expected, after, "only requireToken may change");
            PluginConfig config = PluginConfig.load();
            assertTrue(config.isRequireToken());
            assertEquals(9988, config.getPort());
            assertFalse(config.isAutoStart());

            PluginConfig.saveRequireToken(false);

            expected.setProperty(KEY, "false");
            assertEquals(expected, readProperties(), "only requireToken may change");
            assertFalse(PluginConfig.load().isRequireToken());
        } finally {
            System.setProperty("user.home", saved);
        }
    }

    // T8: a file without the key gets it added, the rest unchanged.
    @Test
    void t8SaveAddsMissingKey(@TempDir Path home) throws IOException {
        String saved = redirectHome(home);
        System.clearProperty(KEY);
        try {
            Properties original = new Properties();
            original.setProperty("sh3d.mcp.port", "9988");
            writeProperties(original);

            PluginConfig.saveRequireToken(true);

            Properties expected = new Properties();
            expected.putAll(original);
            expected.setProperty(KEY, "true");
            assertEquals(expected, readProperties());
        } finally {
            System.setProperty("user.home", saved);
        }
    }

    // T8: the save is atomic through a temp file and a move — no stray files are left in the plugins folder.
    @Test
    void t8SaveLeavesNoStrayFiles(@TempDir Path home) throws IOException {
        String saved = redirectHome(home);
        System.clearProperty(KEY);
        try {
            Properties original = new Properties();
            original.setProperty("sh3d.mcp.port", "9988");
            writeProperties(original);
            java.util.Set<String> before = listFolder();

            PluginConfig.saveRequireToken(true);
            PluginConfig.saveRequireToken(false);
            PluginConfig.saveRequireToken(true);

            assertEquals(before, listFolder(), "temp files must not be left behind");
        } finally {
            System.setProperty("user.home", saved);
        }
    }

    // T8: saving does not touch the system property, which still wins over the file.
    @Test
    void t8SaveDoesNotTouchSystemProperty(@TempDir Path home) throws IOException {
        String saved = redirectHome(home);
        System.setProperty(KEY, "true");
        try {
            PluginConfig.saveRequireToken(false);

            assertEquals("true", System.getProperty(KEY), "the system property must not change");
            assertEquals("false", readProperties().getProperty(KEY), "the file gets the saved value");
            assertTrue(PluginConfig.load().isRequireToken(), "the system property still wins over the file");
        } finally {
            System.clearProperty(KEY);
            System.setProperty("user.home", saved);
        }
    }

    // T1: default is false (no system property, no file).
    @Test
    void t1DefaultIsFalse(@TempDir Path home) {
        String saved = redirectHome(home);
        System.clearProperty(KEY);
        try {
            assertFalse(PluginConfig.load().isRequireToken(), "requireToken must default to false");
        } finally {
            System.setProperty("user.home", saved);
        }
    }

    // T1: the file value is read when there is no system property.
    @Test
    void t1FileValueIsRead(@TempDir Path home) throws IOException {
        String saved = redirectHome(home);
        System.clearProperty(KEY);
        try {
            Properties props = new Properties();
            props.setProperty(KEY, "true");
            props.setProperty("sh3d.mcp.port", "9988");
            writeProperties(props);

            PluginConfig config = PluginConfig.load();
            assertTrue(config.isRequireToken(), "requireToken=true in the file must be honored");
            assertEquals(9988, config.getPort(), "other keys must still load");

            props.setProperty(KEY, "false");
            writeProperties(props);
            assertFalse(PluginConfig.load().isRequireToken());
        } finally {
            System.setProperty("user.home", saved);
        }
    }

    // T1/T8: the system property wins over the file (the dialog then shows that value and does not let it change).
    @Test
    void t1SystemPropertyOverridesFile(@TempDir Path home) throws IOException {
        String saved = redirectHome(home);
        try {
            Properties props = new Properties();
            props.setProperty(KEY, "false");
            writeProperties(props);

            System.setProperty(KEY, "true");
            try {
                assertTrue(PluginConfig.load().isRequireToken(), "the system property must win over the file");
            } finally {
                System.clearProperty(KEY);
            }

            props.setProperty(KEY, "true");
            writeProperties(props);
            System.setProperty(KEY, "false");
            try {
                assertFalse(PluginConfig.load().isRequireToken());
            } finally {
                System.clearProperty(KEY);
            }
        } finally {
            System.setProperty("user.home", saved);
        }
    }
}
