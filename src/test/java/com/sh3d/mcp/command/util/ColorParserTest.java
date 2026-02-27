package com.sh3d.mcp.command.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColorParserTest {

    /** Creates a params map with a single key-value pair (supports null values). */
    private static Map<String, Object> paramsOf(String key, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(key, value);
        return params;
    }

    /** Creates an empty params map. */
    private static Map<String, Object> emptyParams() {
        return new LinkedHashMap<>();
    }

    // ==================== parseRequired ====================

    @Nested
    class ParseRequired {

        @Test
        void returnsNullWhenKeyNotPresent() {
            assertNull(ColorParser.parseRequired(emptyParams(), "color"));
        }

        @ParameterizedTest(name = "parseRequired(\"{0}\") = {1}")
        @CsvSource({
                "#FF0000, 16711680",
                "#000000, 0",
                "#FFFFFF, 16777215",
                "#abcdef, 11259375"
        })
        void parsesValidHexColors(String hex, int expected) {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", hex), "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(expected, result.value);
            assertFalse(result.clear);
            assertNull(result.error);
        }

        @Test
        void returnsErrorForNullValue() {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", null), "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("color"));
            assertTrue(result.error.contains("cannot be null"));
        }

        @ParameterizedTest(name = "parseRequired(\"{0}\") returns error")
        @ValueSource(strings = {"red", "FF0000", "#FFF", ""})
        void returnsErrorForInvalidFormats(String input) {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", input), "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("Invalid"));
        }

        @Test
        void invalidFormatErrorContainsKeyAndValue() {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", "red"), "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("color"));
            assertTrue(result.error.contains("red"));
            assertTrue(result.error.contains("#RRGGBB"));
        }

        @Test
        void usesKeyNameInNullErrorMessage() {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("wallColor", null), "wallColor");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("wallColor"));
        }

        @Test
        void usesKeyNameInInvalidFormatErrorMessage() {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("floorColor", "invalid"), "floorColor");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("floorColor"));
        }

        @Test
        void convertsNonStringObjectToString() {
            // params.get(key) returns an Integer, toString() is called
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", 12345), "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            // 12345.toString() = "12345" which doesn't match #RRGGBB
        }

        @Test
        void neverSetsClearOnRequired() {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", "#FF0000"), "color");

            assertNotNull(result);
            assertFalse(result.clear);
        }

        @Test
        void returnsNullForKeyNotPresentNotForNullValue() {
            // Distinguish between key-not-present (returns null) and key-present-with-null-value (returns error)
            assertNull(ColorParser.parseRequired(emptyParams(), "color"));
            assertNotNull(ColorParser.parseRequired(paramsOf("color", null), "color"));
        }
    }

    // ==================== parseNullable ====================

    @Nested
    class ParseNullable {

        @Test
        void returnsNullWhenKeyNotPresent() {
            assertNull(ColorParser.parseNullable(emptyParams(), "color"));
        }

        @ParameterizedTest(name = "parseNullable(\"{0}\") = {1}")
        @CsvSource({
                "#00FF00, 65280",
                "#aabbcc, 11189196",
                "#AaBbCc, 11189196"
        })
        void parsesValidHexColors(String hex, int expected) {
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("color", hex), "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(expected, result.value);
            assertFalse(result.clear);
            assertNull(result.error);
        }

        @Test
        void returnsClearForNullValue() {
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("color", null), "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertTrue(result.clear);
            assertEquals(0, result.value);
            assertNull(result.error);
        }

        @ParameterizedTest(name = "parseNullable(\"{0}\") returns error")
        @ValueSource(strings = {"not-a-color", "00FF00", ""})
        void returnsErrorForInvalidFormats(String input) {
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("color", input), "color");

            assertNotNull(result);
            assertTrue(result.hasError());
        }

        @Test
        void invalidFormatErrorContainsDetails() {
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("color", "not-a-color"), "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("Invalid"));
            assertTrue(result.error.contains("color"));
            assertTrue(result.error.contains("not-a-color"));
        }

        @Test
        void usesKeyNameInErrorMessage() {
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("ceilingColor", "bad"), "ceilingColor");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("ceilingColor"));
        }

        @Test
        void neverSetsClearForValidColor() {
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("color", "#123456"), "color");

            assertNotNull(result);
            assertFalse(result.clear);
        }

        @Test
        void returnsNullForKeyNotPresentNotForNullValue() {
            // key-not-present -> null (param was not sent)
            // key-present-with-null -> ColorResult with clear=true (user wants to clear)
            assertNull(ColorParser.parseNullable(emptyParams(), "color"));

            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("color", null), "color");
            assertNotNull(result);
            assertTrue(result.clear);
        }
    }

    // ==================== ColorResult ====================

    @Nested
    class ColorResultBehavior {

        @Test
        void hasErrorReturnsTrueWhenErrorIsPresent() {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", "invalid"), "color");

            assertNotNull(result);
            assertTrue(result.hasError());
        }

        @Test
        void hasErrorReturnsFalseWhenNoError() {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", "#FF0000"), "color");

            assertNotNull(result);
            assertFalse(result.hasError());
        }

        @Test
        void hasErrorReturnsFalseForClearResult() {
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("color", null), "color");

            assertNotNull(result);
            assertFalse(result.hasError());
        }
    }

    // ==================== Comparison: parseRequired vs parseNullable for null ====================

    @Nested
    class RequiredVsNullableNullHandling {

        @Test
        void requiredReturnsErrorForNull() {
            ColorParser.ColorResult result = ColorParser.parseRequired(paramsOf("color", null), "color");
            assertNotNull(result);
            assertTrue(result.hasError());
            assertFalse(result.clear);
        }

        @Test
        void nullableReturnsClearForNull() {
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsOf("color", null), "color");
            assertNotNull(result);
            assertFalse(result.hasError());
            assertTrue(result.clear);
        }

        @Test
        void bothReturnNullWhenKeyAbsent() {
            Map<String, Object> params = emptyParams();
            assertNull(ColorParser.parseRequired(params, "color"));
            assertNull(ColorParser.parseNullable(params, "color"));
        }

        @Test
        void bothParseValidColorIdentically() {
            ColorParser.ColorResult required = ColorParser.parseRequired(paramsOf("color", "#ABCDEF"), "color");
            ColorParser.ColorResult nullable = ColorParser.parseNullable(paramsOf("color", "#ABCDEF"), "color");

            assertNotNull(required);
            assertNotNull(nullable);
            assertFalse(required.hasError());
            assertFalse(nullable.hasError());
            assertEquals(required.value, nullable.value);
            assertFalse(required.clear);
            assertFalse(nullable.clear);
        }

        @Test
        void bothReturnErrorForInvalidFormat() {
            ColorParser.ColorResult required = ColorParser.parseRequired(paramsOf("color", "xyz"), "color");
            ColorParser.ColorResult nullable = ColorParser.parseNullable(paramsOf("color", "xyz"), "color");

            assertNotNull(required);
            assertNotNull(nullable);
            assertTrue(required.hasError());
            assertTrue(nullable.hasError());
        }
    }
}
