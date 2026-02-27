package com.sh3d.mcp.command.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FormatUtilTest {

    // ==================== round2 ====================

    @Nested
    class Round2 {

        @Test
        void roundsPositiveValueToTwoDecimalPlaces() {
            assertEquals(3.14, FormatUtil.round2(3.14159), 0.0001);
        }

        @Test
        void roundsNegativeValueToTwoDecimalPlaces() {
            assertEquals(-3.14, FormatUtil.round2(-3.14159), 0.0001);
        }

        @Test
        void roundsZero() {
            assertEquals(0.0, FormatUtil.round2(0.0), 0.0001);
        }

        @Test
        void roundsNegativeZero() {
            assertEquals(0.0, FormatUtil.round2(-0.0), 0.0001);
        }

        @Test
        void roundsUpWhenThirdDecimalIsGe5() {
            assertEquals(1.24, FormatUtil.round2(1.235), 0.0001);
        }

        @Test
        void roundsDownWhenThirdDecimalIsLt5() {
            assertEquals(1.23, FormatUtil.round2(1.234), 0.0001);
        }

        @Test
        void roundsExactTwoDecimalValue() {
            assertEquals(5.50, FormatUtil.round2(5.50), 0.0001);
        }

        @Test
        void roundsWholeNumber() {
            assertEquals(42.0, FormatUtil.round2(42.0), 0.0001);
        }

        @Test
        void roundsVerySmallPositive() {
            assertEquals(0.0, FormatUtil.round2(0.001), 0.0001);
        }

        @Test
        void roundsVerySmallNegative() {
            assertEquals(0.0, FormatUtil.round2(-0.001), 0.0001);
        }

        @Test
        void roundsLargePositiveValue() {
            assertEquals(999999.99, FormatUtil.round2(999999.99), 0.0001);
        }

        @Test
        void roundsLargeNegativeValue() {
            assertEquals(-999999.99, FormatUtil.round2(-999999.99), 0.0001);
        }

        @Test
        void handlesNaN() {
            // Math.round(NaN * 100.0) returns 0L, so round2(NaN) = 0.0
            assertEquals(0.0, FormatUtil.round2(Double.NaN), 0.0001);
        }

        @Test
        void handlesPositiveInfinity() {
            // Math.round(+Infinity) returns Long.MAX_VALUE, division by 100.0 gives a large finite number
            double result = FormatUtil.round2(Double.POSITIVE_INFINITY);
            assertTrue(Double.isFinite(result));
            assertTrue(result > 0);
        }

        @Test
        void handlesNegativeInfinity() {
            // Math.round(-Infinity) returns Long.MIN_VALUE, division by 100.0 gives a large negative number
            double result = FormatUtil.round2(Double.NEGATIVE_INFINITY);
            assertTrue(Double.isFinite(result));
            assertTrue(result < 0);
        }

        @Test
        void handlesMaxDouble() {
            // Math.round on very large values may overflow long, producing Long.MIN_VALUE
            // The implementation uses Math.round(value * 100.0) / 100.0
            double result = FormatUtil.round2(Double.MAX_VALUE);
            // Just verify it doesn't throw
            assertTrue(Double.isFinite(result) || Double.isInfinite(result));
        }

        @ParameterizedTest
        @CsvSource({
                "0.005,  0.01",
                "0.004,  0.0",
                "0.015,  0.02",
                "1.995,  2.0",
                "2.005,  2.01",
                "-0.005, 0.0",
                "-0.015, -0.01"
        })
        void roundsBoundaryValues(double input, double expected) {
            assertEquals(expected, FormatUtil.round2(input), 0.0001);
        }
    }

    // ==================== colorToHex ====================

    @Nested
    class ColorToHex {

        @Test
        void convertsBlack() {
            assertEquals("#000000", FormatUtil.colorToHex(0x000000));
        }

        @Test
        void convertsWhite() {
            assertEquals("#FFFFFF", FormatUtil.colorToHex(0xFFFFFF));
        }

        @Test
        void convertsRed() {
            assertEquals("#FF0000", FormatUtil.colorToHex(0xFF0000));
        }

        @Test
        void convertsGreen() {
            assertEquals("#00FF00", FormatUtil.colorToHex(0x00FF00));
        }

        @Test
        void convertsBlue() {
            assertEquals("#0000FF", FormatUtil.colorToHex(0x0000FF));
        }

        @Test
        void convertsArbitraryColor() {
            assertEquals("#1A2B3C", FormatUtil.colorToHex(0x1A2B3C));
        }

        @Test
        void returnsNullForNullInput() {
            assertNull(FormatUtil.colorToHex(null));
        }

        @Test
        void padsWithLeadingZeros() {
            // 0x00000A should become "#00000A", not "#A"
            assertEquals("#00000A", FormatUtil.colorToHex(0x00000A));
        }

        @Test
        void masks24BitFromLargerInteger() {
            // Integer with high byte set (e.g. alpha channel) should be masked to 24 bits
            // 0xFF123456 & 0xFFFFFF = 0x123456
            assertEquals("#123456", FormatUtil.colorToHex(0xFF123456));
        }

        @Test
        void handlesNegativeIntegerAsColor() {
            // -1 in two's complement = 0xFFFFFFFF, masked to 0xFFFFFF
            assertEquals("#FFFFFF", FormatUtil.colorToHex(-1));
        }

        @Test
        void handlesZeroInteger() {
            assertEquals("#000000", FormatUtil.colorToHex(0));
        }

        @Test
        void outputIsUpperCase() {
            String result = FormatUtil.colorToHex(0xabcdef);
            assertEquals("#ABCDEF", result);
        }

        @Test
        void outputStartsWithHash() {
            String result = FormatUtil.colorToHex(0x123456);
            assertTrue(result.startsWith("#"));
        }

        @Test
        void outputIsExactly7Characters() {
            String result = FormatUtil.colorToHex(0x123456);
            assertEquals(7, result.length());
        }
    }

    // ==================== parseHexColor ====================

    @Nested
    class ParseHexColor {

        @Test
        void parsesBlack() {
            assertEquals(0x000000, FormatUtil.parseHexColor("#000000"));
        }

        @Test
        void parsesWhite() {
            assertEquals(0xFFFFFF, FormatUtil.parseHexColor("#FFFFFF"));
        }

        @Test
        void parsesRed() {
            assertEquals(0xFF0000, FormatUtil.parseHexColor("#FF0000"));
        }

        @Test
        void parsesLowerCase() {
            assertEquals(0xabcdef, FormatUtil.parseHexColor("#abcdef"));
        }

        @Test
        void parsesMixedCase() {
            assertEquals(0xAbCdEf, FormatUtil.parseHexColor("#AbCdEf"));
        }

        @Test
        void parsesArbitraryColor() {
            assertEquals(0x1A2B3C, FormatUtil.parseHexColor("#1A2B3C"));
        }

        @Test
        void returnsNullForMissingHash() {
            assertNull(FormatUtil.parseHexColor("FF0000"));
        }

        @Test
        void returnsNullForShortHex() {
            assertNull(FormatUtil.parseHexColor("#FFF"));
        }

        @Test
        void returnsNullForLongHex() {
            assertNull(FormatUtil.parseHexColor("#FF00001"));
        }

        @Test
        void returnsNullForInvalidHexCharacters() {
            assertNull(FormatUtil.parseHexColor("#GGGGGG"));
        }

        @Test
        void returnsNullForEmptyString() {
            assertNull(FormatUtil.parseHexColor(""));
        }

        @Test
        void returnsNullForHashOnly() {
            assertNull(FormatUtil.parseHexColor("#"));
        }

        @Test
        void returnsNullFor8DigitHex() {
            // #AARRGGBB format should be rejected
            assertNull(FormatUtil.parseHexColor("#FF112233"));
        }

        @Test
        void returnsNullForHexWithSpaces() {
            assertNull(FormatUtil.parseHexColor("# FF0000"));
        }

        @Test
        void returnsNullForHexWithSpecialChars() {
            assertNull(FormatUtil.parseHexColor("#12-456"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"#ZZZZZZ", "#12345G", "#G12345", "000000", "##123456", "#12345"})
        void returnsNullForVariousInvalidFormats(String input) {
            assertNull(FormatUtil.parseHexColor(input));
        }

        @Test
        void throwsNullPointerExceptionForNullInput() {
            // parseHexColor calls hex.matches() which throws NPE on null
            assertThrows(NullPointerException.class, () -> FormatUtil.parseHexColor(null));
        }
    }

    // ==================== Round-trip: colorToHex <-> parseHexColor ====================

    @Nested
    class RoundTrip {

        @ParameterizedTest
        @ValueSource(ints = {0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x1A2B3C, 0xABCDEF, 0x010101})
        void colorToHexThenParseReturnsOriginal(int color) {
            String hex = FormatUtil.colorToHex(color);
            Integer parsed = FormatUtil.parseHexColor(hex);
            assertNotNull(parsed);
            assertEquals(color, parsed.intValue());
        }

        @ParameterizedTest
        @ValueSource(strings = {"#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#1A2B3C"})
        void parseHexColorThenColorToHexReturnsUpperCase(String hex) {
            Integer parsed = FormatUtil.parseHexColor(hex);
            assertNotNull(parsed);
            String roundTripped = FormatUtil.colorToHex(parsed);
            assertEquals(hex.toUpperCase(), roundTripped);
        }
    }
}
