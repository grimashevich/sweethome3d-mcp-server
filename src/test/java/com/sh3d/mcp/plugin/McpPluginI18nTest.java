package com.sh3d.mcp.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class McpPluginI18nTest {

    private static final String BUNDLE_NAME = "com.sh3d.mcp.plugin.McpPlugin";

    private static final Locale[] LOCALES = {
        Locale.FRENCH, Locale.GERMAN, new Locale("es"), Locale.ITALIAN,
        new Locale("ru"), Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE,
        Locale.JAPANESE, new Locale("pt"), new Locale("pt", "BR"),
        new Locale("nl"), new Locale("sv"), new Locale("cs"), new Locale("pl"),
        new Locale("hu"), new Locale("el"), new Locale("bg"), new Locale("vi")
    };

    private static final String[] EXPECTED_KEYS = {
        "action.menu", "action.name",
        "dialog.title",
        "dialog.server.border", "dialog.status.label", "dialog.version.label",
        "dialog.version.text", "dialog.port.label",
        "dialog.status.running", "dialog.status.stopped",
        "dialog.status.starting", "dialog.status.stopping",
        "dialog.button.start", "dialog.button.stop",
        "dialog.button.checkUpdates", "dialog.button.checking",
        "dialog.button.close", "dialog.button.copyClipboard",
        "dialog.button.autoConfigure", "dialog.button.autoConfigure.tooltip",
        "dialog.config.border", "dialog.link.github",
        "dialog.context.copy", "dialog.context.selectAll",
        "dialog.error.title", "dialog.error.portRange",
        "dialog.error.portInvalid", "dialog.error.startFailed",
        "dialog.update.failed.title", "dialog.update.failed.message",
        "dialog.update.available.title", "dialog.update.available.message",
        "dialog.update.upToDate.title", "dialog.update.upToDate.message",
        "dialog.clipboard.title", "dialog.clipboard.message",
        "dialog.configure.confirm.title", "dialog.configure.confirm.message",
        "dialog.configure.success.title", "dialog.configure.created.message",
        "dialog.configure.updated.message", "dialog.configure.error"
    };

    private static final String[] MESSAGE_FORMAT_KEYS = {
        "dialog.version.text", "dialog.status.running",
        "dialog.error.portInvalid", "dialog.error.startFailed",
        "dialog.update.available.message", "dialog.update.upToDate.message",
        "dialog.configure.created.message",
        "dialog.configure.updated.message", "dialog.configure.error"
    };

    static Stream<Locale> allLocales() {
        return Stream.of(LOCALES);
    }

    @Test
    void baseBundleLoads() {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH);
        assertNotNull(bundle);
    }

    @Test
    void baseBundleContainsAllExpectedKeys() {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH);
        for (String key : EXPECTED_KEYS) {
            assertDoesNotThrow(() -> bundle.getString(key),
                "Missing key in base bundle: " + key);
        }
    }

    @Test
    void baseBundleHasNoEmptyValues() {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH);
        for (String key : EXPECTED_KEYS) {
            String value = bundle.getString(key);
            assertFalse(value.trim().isEmpty(),
                "Empty value for key: " + key);
        }
    }

    @ParameterizedTest
    @MethodSource("allLocales")
    void localeBundleLoads(Locale locale) {
        assertDoesNotThrow(() -> ResourceBundle.getBundle(BUNDLE_NAME, locale),
            "Bundle failed to load for locale: " + locale);
    }

    @ParameterizedTest
    @MethodSource("allLocales")
    void localeBundleContainsAllKeys(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
        for (String key : EXPECTED_KEYS) {
            assertDoesNotThrow(() -> bundle.getString(key),
                "Missing key '" + key + "' for locale: " + locale);
        }
    }

    @ParameterizedTest
    @MethodSource("allLocales")
    void localeBundleHasNoEmptyValues(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
        for (String key : EXPECTED_KEYS) {
            String value = bundle.getString(key);
            assertFalse(value.trim().isEmpty(),
                "Empty value for key '" + key + "' in locale: " + locale);
        }
    }

    @Test
    void messageFormatPatternsAreValidInBase() {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH);
        for (String key : MESSAGE_FORMAT_KEYS) {
            String pattern = bundle.getString(key);
            assertDoesNotThrow(() -> new MessageFormat(pattern),
                "Invalid MessageFormat pattern for key: " + key);
        }
    }

    @ParameterizedTest
    @MethodSource("allLocales")
    void messageFormatPatternsAreValidInLocale(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
        for (String key : MESSAGE_FORMAT_KEYS) {
            String pattern = bundle.getString(key);
            assertDoesNotThrow(() -> new MessageFormat(pattern),
                "Invalid MessageFormat pattern for key '" + key + "' in locale: " + locale);
        }
    }

    @Test
    void actionMenuIsToolsInAllLocales() {
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
            assertEquals("Tools", bundle.getString("action.menu"),
                "action.menu must be 'Tools' in locale: " + locale);
        }
    }

    @Test
    void versionInApplicationPluginMatchesPluginConfig() {
        try (java.io.InputStream in = getClass().getResourceAsStream("/ApplicationPlugin.properties")) {
            assertNotNull(in, "ApplicationPlugin.properties not found");
            Properties props = new Properties();
            props.load(in);
            assertEquals(com.sh3d.mcp.config.PluginConfig.PLUGIN_VERSION,
                props.getProperty("version"),
                "Version mismatch between ApplicationPlugin.properties and PluginConfig.PLUGIN_VERSION");
        } catch (Exception e) {
            fail("Could not read ApplicationPlugin.properties: " + e.getMessage());
        }
    }

    @Test
    void expectedKeyCountMatchesBase() {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH);
        Set<String> actualKeys = bundle.keySet();
        assertEquals(EXPECTED_KEYS.length, actualKeys.size(),
            "Key count mismatch. Expected: " + EXPECTED_KEYS.length
            + ", Actual: " + actualKeys.size());
    }
}
