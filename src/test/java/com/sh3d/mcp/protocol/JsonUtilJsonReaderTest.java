package com.sh3d.mcp.protocol;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for JsonUtil.JsonReader (the internal recursive-descent parser).
 * Covers: MAX_DEPTH boundary, unicode escapes, malformed JSON, number edge cases,
 * and unterminated constructs.
 */
class JsonUtilJsonReaderTest {

    // ======================== MAX_DEPTH BOUNDARY ========================

    @Nested
    class MaxDepthBoundary {

        @Test
        void testNesting32ObjectsSucceeds() {
            // 32 levels of nested objects — exactly at MAX_DEPTH, should parse
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                sb.append("{\"a\":");
            }
            sb.append("1");
            for (int i = 0; i < 32; i++) {
                sb.append("}");
            }
            Object result = JsonUtil.parse(sb.toString());
            assertNotNull(result);
            assertInstanceOf(Map.class, result);
        }

        @Test
        void testNesting33ObjectsThrows() {
            // 33 levels — exceeds MAX_DEPTH, should throw
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 33; i++) {
                sb.append("{\"a\":");
            }
            sb.append("1");
            for (int i = 0; i < 33; i++) {
                sb.append("}");
            }
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse(sb.toString()));
            assertTrue(ex.getMessage().contains("depth"));
        }

        @Test
        void testNesting32ArraysSucceeds() {
            // 32 levels of nested arrays — exactly at MAX_DEPTH
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                sb.append("[");
            }
            sb.append("1");
            for (int i = 0; i < 32; i++) {
                sb.append("]");
            }
            Object result = JsonUtil.parse(sb.toString());
            assertNotNull(result);
            assertInstanceOf(List.class, result);
        }

        @Test
        void testNesting33ArraysThrows() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 33; i++) {
                sb.append("[");
            }
            sb.append("1");
            for (int i = 0; i < 33; i++) {
                sb.append("]");
            }
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse(sb.toString()));
            assertTrue(ex.getMessage().contains("depth"));
        }

        @Test
        void testMixedNesting32LevelsSucceeds() {
            // Alternating objects and arrays: {[{[...]}]}
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                if (i % 2 == 0) {
                    sb.append("{\"a\":");
                } else {
                    sb.append("[");
                }
            }
            sb.append("1");
            for (int i = 31; i >= 0; i--) {
                if (i % 2 == 0) {
                    sb.append("}");
                } else {
                    sb.append("]");
                }
            }
            Object result = JsonUtil.parse(sb.toString());
            assertNotNull(result);
        }

        @Test
        void testMixedNesting33LevelsThrows() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 33; i++) {
                if (i % 2 == 0) {
                    sb.append("{\"a\":");
                } else {
                    sb.append("[");
                }
            }
            sb.append("1");
            for (int i = 32; i >= 0; i--) {
                if (i % 2 == 0) {
                    sb.append("}");
                } else {
                    sb.append("]");
                }
            }
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse(sb.toString()));
            assertTrue(ex.getMessage().contains("depth"));
        }
    }

    // ======================== UNICODE ESCAPES ========================

    @Nested
    class UnicodeEscapes {

        @Test
        void testUnicodeA() {
            // \u0041 -> 'A'
            Object result = JsonUtil.parse("\"\\u0041\"");
            assertEquals("A", result);
        }

        @Test
        void testUnicodeNullChar() {
            // \u0000 -> null char
            Object result = JsonUtil.parse("\"\\u0000\"");
            assertEquals("\0", result);
        }

        @Test
        void testUnicodeFFFF() {
            // \uFFFF -> valid unicode char
            Object result = JsonUtil.parse("\"\\uFFFF\"");
            assertEquals("\uFFFF", result);
        }

        @Test
        void testInvalidHexGGGG() {
            // \\uGGGG -> invalid hex
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("\"\\uGGGG\""));
            assertTrue(ex.getMessage().contains("Invalid \\u escape"));
        }

        @Test
        void testIncompleteUnicodeEscape() {
            // \\u00 -> only 2 hex digits instead of 4
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("\"\\u00\""));
            assertTrue(ex.getMessage().contains("Unterminated \\uXXXX"));
        }

        @Test
        void testUnicodeEscapeNoDigits() {
            // \\u followed by end of string
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("\"\\u\""));
            assertTrue(ex.getMessage().contains("Unterminated \\uXXXX"));
        }

        @Test
        void testUnicodeEscapeInMiddleOfString() {
            Object result = JsonUtil.parse("\"hello\\u0020world\"");
            assertEquals("hello world", result);
        }

        @Test
        void testMultipleUnicodeEscapes() {
            // \u0048\u0069 -> "Hi"
            Object result = JsonUtil.parse("\"\\u0048\\u0069\"");
            assertEquals("Hi", result);
        }
    }

    // ======================== MALFORMED JSON ========================

    @Nested
    class MalformedJson {

        @Test
        void testTrailingCommaInObject() {
            // {"a":1,} — trailing comma
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("{\"a\":1,}"));
            // Parser expects a string key after comma, gets '}'
            assertNotNull(ex.getMessage());
        }

        @Test
        void testTrailingCommaInArray() {
            // [1,2,] — trailing comma
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("[1,2,]"));
            assertNotNull(ex.getMessage());
        }

        @Test
        void testSingleQuotedStrings() {
            // {'a':1} — single quotes not valid in JSON
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("{'a':1}"));
            assertNotNull(ex.getMessage());
        }

        @Test
        void testUnquotedKeys() {
            // {a:1} — unquoted keys not valid in JSON
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("{a:1}"));
            assertNotNull(ex.getMessage());
        }

        @Test
        void testTrailingCharacters() {
            // {"a":1}extra — content after root value
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("{\"a\":1}extra"));
            assertTrue(ex.getMessage().contains("unexpected content after root value"));
        }

        @Test
        void testEmptyInput() {
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse(""));
        }

        @Test
        void testNullInput() {
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse(null));
        }

        @Test
        void testWhitespaceOnly() {
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("   "));
        }

        @Test
        void testMultipleRootValues() {
            // 1 2 — two root values
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("1 2"));
            assertTrue(ex.getMessage().contains("unexpected content"));
        }

        @Test
        void testMissingColon() {
            // {"a" 1} — missing colon
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("{\"a\" 1}"));
        }

        @Test
        void testMissingValue() {
            // {"a":} — missing value after colon
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("{\"a\":}"));
        }

        @Test
        void testInvalidEscapeCharacter() {
            // \x is not a valid JSON escape
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("\"\\x\""));
        }
    }

    // ======================== NUMBER EDGE CASES ========================

    @Nested
    class NumberEdgeCases {

        @Test
        void testLongMaxValue() {
            Object result = JsonUtil.parse("9223372036854775807");
            assertInstanceOf(Long.class, result);
            assertEquals(Long.MAX_VALUE, result);
        }

        @Test
        void testOverflowBeyondLongMaxValue() {
            // 9223372036854775808 overflows Long.MAX_VALUE
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("9223372036854775808"));
        }

        @Test
        void testExponentialNotation1e10() {
            Object result = JsonUtil.parse("1e10");
            assertInstanceOf(Double.class, result);
            assertEquals(1e10, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        void testExponentialNotation1point5eMinus3() {
            Object result = JsonUtil.parse("1.5e-3");
            assertInstanceOf(Double.class, result);
            assertEquals(0.0015, ((Number) result).doubleValue(), 1e-10);
        }

        @Test
        void testExponentialNotation1EPlus10() {
            Object result = JsonUtil.parse("1E+10");
            assertInstanceOf(Double.class, result);
            assertEquals(1E+10, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        void testNegativeZero() {
            Object result = JsonUtil.parse("-0");
            assertInstanceOf(Number.class, result);
            assertEquals(0, ((Number) result).intValue());
        }

        @Test
        void testNegativeExponential() {
            Object result = JsonUtil.parse("-1e10");
            assertInstanceOf(Double.class, result);
            assertEquals(-1e10, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        void testLeadingZero() {
            // "01" — parser reads all consecutive digits as one number (no strict leading-zero check)
            Object result = JsonUtil.parse("01");
            assertInstanceOf(Integer.class, result);
            assertEquals(1, result);
        }

        @Test
        void testDoubleLeadingZero() {
            // "00" — parser reads all consecutive digits, result is 0
            Object result = JsonUtil.parse("00");
            assertInstanceOf(Integer.class, result);
            assertEquals(0, result);
        }

        @Test
        void testIntegerInIntRange() {
            Object result = JsonUtil.parse("42");
            assertInstanceOf(Integer.class, result);
            assertEquals(42, result);
        }

        @Test
        void testLargeIntBeyondIntRange() {
            // 3000000000 > Integer.MAX_VALUE but < Long.MAX_VALUE
            Object result = JsonUtil.parse("3000000000");
            assertInstanceOf(Long.class, result);
            assertEquals(3000000000L, result);
        }

        @Test
        void testNegativeInteger() {
            Object result = JsonUtil.parse("-42");
            assertInstanceOf(Integer.class, result);
            assertEquals(-42, result);
        }

        @Test
        void testDecimalNumber() {
            Object result = JsonUtil.parse("3.14");
            assertInstanceOf(Double.class, result);
            assertEquals(3.14, ((Number) result).doubleValue(), 1e-10);
        }

        @Test
        void testZero() {
            Object result = JsonUtil.parse("0");
            assertInstanceOf(Integer.class, result);
            assertEquals(0, result);
        }
    }

    // ======================== UNTERMINATED CONSTRUCTS ========================

    @Nested
    class UnterminatedConstructs {

        @Test
        void testUnterminatedString() {
            // "hello — missing closing quote
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("\"hello"));
            assertTrue(ex.getMessage().contains("Unterminated string"));
        }

        @Test
        void testUnterminatedArray() {
            // [1, 2 — missing closing bracket
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("[1, 2"));
            assertTrue(ex.getMessage().contains("Unterminated array"));
        }

        @Test
        void testUnterminatedObject() {
            // {"a": 1 — missing closing brace
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("{\"a\": 1"));
            assertTrue(ex.getMessage().contains("Unterminated object"));
        }

        @Test
        void testUnterminatedEscape() {
            // "hello\ — backslash at end of string
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("\"hello\\"));
            assertTrue(ex.getMessage().contains("Unterminated escape"));
        }

        @Test
        void testUnterminatedNestedObject() {
            // {"a": {"b": 1} — outer object not closed
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("{\"a\": {\"b\": 1}"));
            assertTrue(ex.getMessage().contains("Unterminated object"));
        }

        @Test
        void testUnterminatedNestedArray() {
            // [[1, 2] — outer array not closed
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> JsonUtil.parse("[[1, 2]"));
            assertTrue(ex.getMessage().contains("Unterminated array"));
        }

        @Test
        void testEmptyUnterminatedObject() {
            // { — empty object not closed
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("{"));
        }

        @Test
        void testEmptyUnterminatedArray() {
            // [ — empty array not closed
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("["));
        }
    }

    // ======================== STANDARD ESCAPE SEQUENCES ========================

    @Nested
    class StandardEscapes {

        @Test
        void testNewline() {
            Object result = JsonUtil.parse("\"line1\\nline2\"");
            assertEquals("line1\nline2", result);
        }

        @Test
        void testTab() {
            Object result = JsonUtil.parse("\"col1\\tcol2\"");
            assertEquals("col1\tcol2", result);
        }

        @Test
        void testCarriageReturn() {
            Object result = JsonUtil.parse("\"a\\rb\"");
            assertEquals("a\rb", result);
        }

        @Test
        void testBackspace() {
            Object result = JsonUtil.parse("\"a\\bb\"");
            assertEquals("a\bb", result);
        }

        @Test
        void testFormFeed() {
            Object result = JsonUtil.parse("\"a\\fb\"");
            assertEquals("a\fb", result);
        }

        @Test
        void testForwardSlash() {
            Object result = JsonUtil.parse("\"a\\/b\"");
            assertEquals("a/b", result);
        }

        @Test
        void testEscapedQuote() {
            Object result = JsonUtil.parse("\"say \\\"hi\\\"\"");
            assertEquals("say \"hi\"", result);
        }

        @Test
        void testEscapedBackslash() {
            Object result = JsonUtil.parse("\"C:\\\\Users\"");
            assertEquals("C:\\Users", result);
        }
    }

    // ======================== BOOLEAN AND NULL ========================

    @Nested
    class BooleanAndNull {

        @Test
        void testTrue() {
            assertEquals(Boolean.TRUE, JsonUtil.parse("true"));
        }

        @Test
        void testFalse() {
            assertEquals(Boolean.FALSE, JsonUtil.parse("false"));
        }

        @Test
        void testNull() {
            assertNull(JsonUtil.parse("null"));
        }

        @Test
        void testTruncatedTrue() {
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("tru"));
        }

        @Test
        void testTruncatedFalse() {
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("fals"));
        }

        @Test
        void testTruncatedNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtil.parse("nul"));
        }
    }
}
