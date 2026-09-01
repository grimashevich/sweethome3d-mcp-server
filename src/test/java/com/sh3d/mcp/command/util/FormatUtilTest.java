package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

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

    // ==================== levelName ====================

    @Nested
    class LevelName {

        @Test
        void returnsNullWhenObjectHasNoLevel() {
            assertNull(FormatUtil.levelName(null));
        }

        @Test
        void returnsTheLevelName() {
            assertEquals("Ground floor",
                    FormatUtil.levelName(new Level("Ground floor", 0, 12, 250)));
        }
    }

    // ==================== build*Info key contracts ====================

    /**
     * These builders are shared by several commands, and each command's JSON response
     * is the builder's map plus fields the caller appends. Asserting the exact key list
     * in order pins both the field set and the field order that callers depend on.
     */
    @Nested
    class BuilderKeyContracts {

        private java.util.List<String> keysOf(Map<String, Object> map) {
            return new ArrayList<>(map.keySet());
        }

        @Test
        void wallInfoStartsWithIdAndCoordinatesAndEndsWithLevel() {
            Wall wall = new Wall(0, 0, 500, 0, 10, 250);
            java.util.List<String> keys = keysOf(FormatUtil.buildWallInfo(wall));

            assertEquals(Arrays.asList("id", "xStart", "yStart", "xEnd", "yEnd"),
                    keys.subList(0, 5), "segment prefix must come first, in this order");
            assertEquals("level", keys.get(keys.size() - 1));
        }

        @Test
        void dimensionLineInfoHasExactKeysInOrder() {
            DimensionLine dim = new DimensionLine(100, 200, 600, 200, 30);
            assertEquals(
                    Arrays.asList("id", "xStart", "yStart", "xEnd", "yEnd", "offset", "length", "level"),
                    keysOf(FormatUtil.buildDimensionLineInfo(dim)));
        }

        @Test
        void dimensionLineInfoValues() {
            DimensionLine dim = new DimensionLine(100, 200, 600, 200, 30);
            Map<String, Object> info = FormatUtil.buildDimensionLineInfo(dim);

            assertEquals(dim.getId(), info.get("id"));
            assertEquals(100.0, ((Number) info.get("xStart")).doubleValue(), 0.01);
            assertEquals(200.0, ((Number) info.get("yStart")).doubleValue(), 0.01);
            assertEquals(600.0, ((Number) info.get("xEnd")).doubleValue(), 0.01);
            assertEquals(200.0, ((Number) info.get("yEnd")).doubleValue(), 0.01);
            assertEquals(30.0, ((Number) info.get("offset")).doubleValue(), 0.01);
            assertEquals(500.0, ((Number) info.get("length")).doubleValue(), 0.01);
            assertNull(info.get("level"));
        }

        @Test
        void dimensionLineInfoReportsAssignedLevel() {
            DimensionLine dim = new DimensionLine(0, 0, 500, 0, 25);
            dim.setLevel(new Level("First floor", 250, 12, 250));

            assertEquals("First floor", FormatUtil.buildDimensionLineInfo(dim).get("level"));
        }

        @Test
        void labelInfoHasExactKeysInOrder() {
            assertEquals(
                    Arrays.asList("id", "text", "x", "y", "angle", "color"),
                    keysOf(FormatUtil.buildLabelInfo(new Label("Kitchen", 100, 200))));
        }

        @Test
        void labelInfoValuesConvertAngleToDegrees() {
            Label label = new Label("Kitchen", 100, 200);
            label.setAngle((float) Math.toRadians(90));
            label.setColor(0xFF0000);
            Map<String, Object> info = FormatUtil.buildLabelInfo(label);

            assertEquals("Kitchen", info.get("text"));
            assertEquals(100.0, ((Number) info.get("x")).doubleValue(), 0.01);
            assertEquals(200.0, ((Number) info.get("y")).doubleValue(), 0.01);
            assertEquals(90.0, ((Number) info.get("angle")).doubleValue(), 0.01);
            assertEquals("#FF0000", info.get("color"));
        }

        @Test
        void levelInfoHasExactKeysInOrder() {
            Level level = new Level("Ground floor", 0, 12, 250);
            assertEquals(
                    Arrays.asList("id", "name", "elevation", "height", "floorThickness"),
                    keysOf(FormatUtil.buildLevelInfo(level.getId(), level)));
        }

        @Test
        void levelInfoValues() {
            Level level = new Level("Ground floor", 0, 12, 250);
            Map<String, Object> info = FormatUtil.buildLevelInfo(level.getId(), level);

            assertEquals(level.getId(), info.get("id"));
            assertEquals("Ground floor", info.get("name"));
            assertEquals(0.0, ((Number) info.get("elevation")).doubleValue(), 0.01);
            assertEquals(250.0, ((Number) info.get("height")).doubleValue(), 0.01);
            assertEquals(12.0, ((Number) info.get("floorThickness")).doubleValue(), 0.01);
        }

        @Test
        void levelInfoPassesTheIdThroughVerbatim() {
            // list_levels reports a positional index while get_state and add_level report
            // the stable HomeObject id, so the builder must not impose either.
            Level level = new Level("Ground floor", 0, 12, 250);

            assertEquals(0, FormatUtil.buildLevelInfo(0, level).get("id"));
            assertEquals(3, FormatUtil.buildLevelInfo(3, level).get("id"));
            assertEquals(level.getId(), FormatUtil.buildLevelInfo(level.getId(), level).get("id"));
            assertEquals("caller-supplied", FormatUtil.buildLevelInfo("caller-supplied", level).get("id"));
        }

        @Test
        void environmentInfoHasExactKeysInOrder() {
            assertEquals(
                    Arrays.asList("groundColor", "groundTexture", "skyColor", "skyTexture",
                            "lightColor", "ceilingLightColor", "wallsAlpha", "drawingMode",
                            "allLevelsVisible"),
                    keysOf(FormatUtil.buildEnvironmentInfo(new HomeEnvironment())));
        }

        @Test
        void environmentInfoValues() {
            HomeEnvironment env = new HomeEnvironment();
            env.setGroundColor(0xD0CC9B);
            env.setSkyColor(0xCCE4FC);
            env.setWallsAlpha(0.5f);
            env.setAllLevelsVisible(true);
            Map<String, Object> info = FormatUtil.buildEnvironmentInfo(env);

            assertEquals("#D0CC9B", info.get("groundColor"));
            assertEquals("#CCE4FC", info.get("skyColor"));
            assertNull(info.get("groundTexture"));
            assertNull(info.get("skyTexture"));
            assertEquals(0.5, ((Number) info.get("wallsAlpha")).doubleValue(), 0.01);
            assertEquals(true, info.get("allLevelsVisible"));
            assertNotNull(info.get("drawingMode"));
        }
    }
}
