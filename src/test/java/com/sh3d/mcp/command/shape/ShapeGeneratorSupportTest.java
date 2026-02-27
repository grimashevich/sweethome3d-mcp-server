package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.protocol.Request;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShapeGeneratorSupport utility methods.
 * These are pure functions without Java3D dependencies.
 */
class ShapeGeneratorSupportTest {

    // ======================== parseColor ========================

    @Nested
    class ParseColor {

        private Request requestWithColor(Object color) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", color);
            return new Request("generate_shape", params);
        }

        @Test
        void testNullColor() {
            Map<String, Object> params = new LinkedHashMap<>();
            Request req = new Request("generate_shape", params);
            assertNull(ShapeGeneratorSupport.parseColor(req));
        }

        @Test
        void testIntegerColor() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor(16711680));
            assertNotNull(result);
            assertEquals(16711680, result); // 0xFF0000
        }

        @Test
        void testIntegerColorZero() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor(0));
            assertNotNull(result);
            assertEquals(0, result);
        }

        @Test
        void testHexStringWithHash() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("#FF0000"));
            assertNotNull(result);
            assertEquals(0xFF0000, result);
        }

        @Test
        void testHexStringWithoutHash() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("FF0000"));
            assertNotNull(result);
            assertEquals(0xFF0000, result);
        }

        @Test
        void testHexStringWith0x() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("0xFF0000"));
            assertNotNull(result);
            assertEquals(0xFF0000, result);
        }

        @Test
        void testHexStringWith0X() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("0XFF0000"));
            assertNotNull(result);
            assertEquals(0xFF0000, result);
        }

        @Test
        void testHexStringGreen() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("#00FF00"));
            assertNotNull(result);
            assertEquals(0x00FF00, result);
        }

        @Test
        void testHexStringBlue() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("#0000FF"));
            assertNotNull(result);
            assertEquals(0x0000FF, result);
        }

        @Test
        void testHexStringWhite() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("#FFFFFF"));
            assertNotNull(result);
            assertEquals(0xFFFFFF, result);
        }

        @Test
        void testHexStringBlack() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("#000000"));
            assertNotNull(result);
            assertEquals(0, result);
        }

        @Test
        void testHexStringLowercase() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("#ff5500"));
            assertNotNull(result);
            assertEquals(0xFF5500, result);
        }

        @Test
        void testHexStringWithWhitespace() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor("  #FF0000  "));
            assertNotNull(result);
            assertEquals(0xFF0000, result);
        }

        @Test
        void testDoubleColor() {
            // Some JSON parsers represent numbers as Double
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor(255.0));
            assertNotNull(result);
            assertEquals(255, result);
        }

        @Test
        void testLongColor() {
            Integer result = ShapeGeneratorSupport.parseColor(requestWithColor(16711680L));
            assertNotNull(result);
            assertEquals(16711680, result);
        }

        @Test
        void testInvalidHexReturnsNull() {
            assertNull(ShapeGeneratorSupport.parseColor(requestWithColor("not_a_color")));
        }

        @Test
        void testEmptyStringReturnsNull() {
            assertNull(ShapeGeneratorSupport.parseColor(requestWithColor("")));
        }

        @Test
        void testHashOnlyReturnsNull() {
            assertNull(ShapeGeneratorSupport.parseColor(requestWithColor("#")));
        }

        @Test
        void testNonStringNonNumberReturnsNull() {
            assertNull(ShapeGeneratorSupport.parseColor(requestWithColor(true)));
        }
    }

    // ======================== toFloat ========================

    @Nested
    class ToFloat {

        @Test
        void testIntToFloat() {
            assertEquals(42.0f, ShapeGeneratorSupport.toFloat(42), 0.001f);
        }

        @Test
        void testDoubleToFloat() {
            assertEquals(3.14f, ShapeGeneratorSupport.toFloat(3.14), 0.01f);
        }

        @Test
        void testStringToFloat() {
            assertEquals(100.5f, ShapeGeneratorSupport.toFloat("100.5"), 0.01f);
        }

        @Test
        void testLongToFloat() {
            assertEquals(1000000.0f, ShapeGeneratorSupport.toFloat(1000000L), 0.01f);
        }

        @Test
        void testInvalidStringThrows() {
            assertThrows(NumberFormatException.class,
                    () -> ShapeGeneratorSupport.toFloat("not_a_number"));
        }
    }

    // ======================== toInt ========================

    @Nested
    class ToInt {

        @Test
        void testIntToInt() {
            assertEquals(42, ShapeGeneratorSupport.toInt(42));
        }

        @Test
        void testDoubleToInt() {
            assertEquals(3, ShapeGeneratorSupport.toInt(3.9));
        }

        @Test
        void testStringToInt() {
            assertEquals(100, ShapeGeneratorSupport.toInt("100"));
        }

        @Test
        void testLongToInt() {
            assertEquals(1000, ShapeGeneratorSupport.toInt(1000L));
        }

        @Test
        void testInvalidStringThrows() {
            assertThrows(NumberFormatException.class,
                    () -> ShapeGeneratorSupport.toInt("not_a_number"));
        }

        @Test
        void testFloatStringThrows() {
            assertThrows(NumberFormatException.class,
                    () -> ShapeGeneratorSupport.toInt("3.14"));
        }
    }
}
