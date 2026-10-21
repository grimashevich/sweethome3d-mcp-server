package com.sh3d.mcp.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract S, T9: every one of the 18 locale files {@code McpPlugin*.properties} carries the same keys as the base
 * file, so the token UI strings (checkbox, token row, copy, rotate, messages) are translated, not only inherited
 * from the base bundle. Compares the {@code .properties} files directly, not through {@link java.util.ResourceBundle}
 * (which would hide a key that is missing from a locale file but present in the base).
 */
class TokenI18nSpecTest {

    private static final String BASE = "/com/sh3d/mcp/plugin/McpPlugin.properties";

    /** The locale suffixes shipped besides the base file. */
    private static final String[] LOCALE_SUFFIXES = {
        "_bg", "_cs", "_de", "_el", "_es", "_fr", "_hu", "_it", "_ja",
        "_nl", "_pl", "_pt", "_pt_BR", "_ru", "_sv", "_vi", "_zh_CN", "_zh_TW"
    };

    private static final String[] TOKEN_KEY_FRAGMENTS = {"token"};

    static Stream<String> localeFiles() {
        List<String> files = new ArrayList<>();
        for (String suffix : LOCALE_SUFFIXES) {
            files.add("/com/sh3d/mcp/plugin/McpPlugin" + suffix + ".properties");
        }
        return files.stream();
    }

    private static Properties load(String resource) throws IOException {
        Properties props = new Properties();
        try (InputStream in = TokenI18nSpecTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource " + resource);
            props.load(in);
        }
        return props;
    }

    private static Set<String> keys(Properties props) {
        return new TreeSet<>(props.stringPropertyNames());
    }

    // T9: there are exactly 18 locale files.
    @Test
    void t9EighteenLocaleFilesExist() throws IOException {
        for (String resource : (Iterable<String>) localeFiles()::iterator) {
            assertNotNull(TokenI18nSpecTest.class.getResourceAsStream(resource), "missing " + resource);
        }
        assertEquals(18, LOCALE_SUFFIXES.length);
    }

    // T9: the base file defines token UI keys (if it did not, the parity check below would be vacuous).
    @Test
    void t9BaseHasTokenKeys() throws IOException {
        Set<String> tokenKeys = tokenKeys(keys(load(BASE)));
        assertFalse(tokenKeys.isEmpty(), "the base file should define token UI strings");
    }

    /** Ключи строк диалога управления токеном. */
    private static final String[] TOKEN_DIALOG_KEYS = {
        "dialog.token.require", "dialog.token.require.error",
        "dialog.token.label", "dialog.button.copyToken", "dialog.button.rotateToken",
        "dialog.token.copied.message", "dialog.token.rotate.title",
        "dialog.token.rotate.confirm", "dialog.token.rotate.error"
    };

    static Stream<String> baseAndLocaleFiles() {
        return Stream.concat(Stream.of(BASE), localeFiles());
    }

    // T9: each named token string is in the base file and in every one of the 18 locale files, non-empty.
    @ParameterizedTest(name = "{0}")
    @MethodSource("baseAndLocaleFiles")
    void t9NamedTokenKeysPresentAndNonEmpty(String resource) throws IOException {
        Properties props = load(resource);
        List<String> problems = new ArrayList<>();
        for (String key : TOKEN_DIALOG_KEYS) {
            String value = props.getProperty(key);
            if (value == null) {
                problems.add("missing " + key);
            } else if (value.trim().isEmpty()) {
                problems.add("empty " + key);
            }
        }
        assertEquals(new ArrayList<String>(), problems, resource);
    }

    // T9: the switch-on error carries the cause as {0} in every file, and is a valid MessageFormat pattern.
    @ParameterizedTest(name = "{0}")
    @MethodSource("baseAndLocaleFiles")
    void t9RequireErrorHasPlaceholder(String resource) throws IOException {
        String value = load(resource).getProperty("dialog.token.require.error");
        assertNotNull(value, resource + " is missing dialog.token.require.error");
        assertTrue(value.contains("{0}"), resource + ": dialog.token.require.error must contain {0}: " + value);
        assertDoesNotThrow(() -> new java.text.MessageFormat(value), resource);
    }

    // T9: every locale file has exactly the base file's keys — none missing (and none extra).
    @ParameterizedTest(name = "{0}")
    @MethodSource("localeFiles")
    void t9LocaleKeysMatchBase(String resource) throws IOException {
        Set<String> base = keys(load(BASE));
        Set<String> locale = keys(load(resource));

        Set<String> missing = new TreeSet<>(base);
        missing.removeAll(locale);
        Set<String> extra = new TreeSet<>(locale);
        extra.removeAll(base);

        assertEquals(Set.of(), missing, resource + " is missing keys present in the base file");
        assertEquals(Set.of(), extra, resource + " has keys absent from the base file");
    }

    // T9: specifically, every token UI key of the base file is present in every locale file, with a non-empty value.
    @ParameterizedTest(name = "{0}")
    @MethodSource("localeFiles")
    void t9TokenKeysTranslatedInEveryLocale(String resource) throws IOException {
        Set<String> tokenKeys = tokenKeys(keys(load(BASE)));
        Properties locale = load(resource);
        for (String key : tokenKeys) {
            String value = locale.getProperty(key);
            assertNotNull(value, resource + " is missing token key " + key);
            assertFalse(value.trim().isEmpty(), resource + " has an empty value for " + key);
        }
    }

    private static Set<String> tokenKeys(Set<String> keys) {
        Set<String> result = new TreeSet<>();
        for (String key : keys) {
            for (String fragment : TOKEN_KEY_FRAGMENTS) {
                if (key.toLowerCase().contains(fragment)) {
                    result.add(key);
                }
            }
        }
        return result;
    }
}
