package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.bridge.CommandException;
import com.sh3d.mcp.protocol.Request;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShapeCommonParams DTO parsing and validation.
 */
class ShapeCommonParamsTest {

    private Request buildRequest(Map<String, Object> params) {
        return new Request("generate_shape", params);
    }

    // ======================== Defaults ========================

    @Nested
    class Defaults {

        @Test
        void testAllDefaults() {
            Map<String, Object> params = new LinkedHashMap<>();
            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertEquals("Box", p.name);
            assertEquals(0f, p.transparency, 0.001f);
            assertEquals(0f, p.elevation, 0.001f);
            assertNull(p.color);
            assertEquals(0f, p.x, 0.001f);
            assertEquals(0f, p.y, 0.001f);
            assertEquals(0f, p.angle, 0.001f);
        }

        @Test
        void testDefaultNameUsedWhenNull() {
            Map<String, Object> params = new LinkedHashMap<>();
            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Sphere");

            assertEquals("Sphere", p.name);
        }

        @Test
        void testDefaultNameUsedWhenEmpty() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", "");
            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Cylinder");

            assertEquals("Cylinder", p.name);
        }

        @Test
        void testDefaultNameUsedWhenWhitespace() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", "   ");
            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Cone");

            assertEquals("Cone", p.name);
        }
    }

    // ======================== Custom values ========================

    @Nested
    class CustomValues {

        @Test
        void testAllCustomValues() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", "MyShape");
            params.put("transparency", 0.5);
            params.put("elevation", 100.0);
            params.put("color", 0xFF0000);
            params.put("x", 200.0);
            params.put("y", 300.0);
            params.put("angle", 45.0);

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertEquals("MyShape", p.name);
            assertEquals(0.5f, p.transparency, 0.001f);
            assertEquals(100.0f, p.elevation, 0.001f);
            assertNotNull(p.color);
            assertEquals(0xFF0000, p.color);
            assertEquals(200.0f, p.x, 0.001f);
            assertEquals(300.0f, p.y, 0.001f);
            assertEquals(45.0f, p.angle, 0.001f);
        }

        @Test
        void testCustomNameOverridesDefault() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", "CustomBox");

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertEquals("CustomBox", p.name);
        }

        @Test
        void testTransparencyBoundaryZero() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("transparency", 0.0);

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertEquals(0f, p.transparency, 0.001f);
        }

        @Test
        void testTransparencyBoundaryOne() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("transparency", 1.0);

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertEquals(1f, p.transparency, 0.001f);
        }

        @Test
        void testNegativeElevation() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("elevation", -50.0);

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertEquals(-50f, p.elevation, 0.001f);
        }

        @Test
        void testNegativeCoordinates() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("x", -100.0);
            params.put("y", -200.0);

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertEquals(-100f, p.x, 0.001f);
            assertEquals(-200f, p.y, 0.001f);
        }
    }

    // ======================== Transparency validation ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testNegativeTransparencyThrowsException() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("transparency", -0.1);

            CommandException ex = assertThrows(CommandException.class,
                    () -> ShapeCommonParams.parse(buildRequest(params), "Box"));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyAboveOneThrowsException() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("transparency", 1.1);

            CommandException ex = assertThrows(CommandException.class,
                    () -> ShapeCommonParams.parse(buildRequest(params), "Box"));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyLargeNegativeThrowsException() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("transparency", -100.0);

            assertThrows(CommandException.class,
                    () -> ShapeCommonParams.parse(buildRequest(params), "Box"));
        }

        @Test
        void testTransparencyLargePositiveThrowsException() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("transparency", 5.0);

            assertThrows(CommandException.class,
                    () -> ShapeCommonParams.parse(buildRequest(params), "Box"));
        }
    }

    // ======================== Color parsing ========================

    @Nested
    class ColorParsing {

        @Test
        void testIntegerColor() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", 16711680); // 0xFF0000

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertNotNull(p.color);
            assertEquals(16711680, p.color);
        }

        @Test
        void testHexStringColor() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#00FF00");

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertNotNull(p.color);
            assertEquals(0x00FF00, p.color);
        }

        @Test
        void testHexStringColorWith0x() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "0x0000FF");

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertNotNull(p.color);
            assertEquals(0x0000FF, p.color);
        }

        @Test
        void testNoColorReturnsNull() {
            Map<String, Object> params = new LinkedHashMap<>();

            ShapeCommonParams p = ShapeCommonParams.parse(buildRequest(params), "Box");

            assertNull(p.color);
        }
    }
}
