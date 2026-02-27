package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.command.handler.GenerateShapeHandler;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for ExtrudeShapeGenerator validation (mode="extrude").
 * Covers polygon validation, point format, height checks, and edge cases.
 * Actual 3D generation (including earclipping triangulation via POLYGON_ARRAY)
 * requires JOGL runtime (tested live in TEST2).
 */
class ExtrudeTriangulationTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> extrudeParams(List<?> polygon, double height) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "extrude");
        params.put("polygon", polygon);
        params.put("height", height);
        return params;
    }

    // ======================== POLYGON POINT COUNT ========================

    @Nested
    class PolygonPointCount {

        @Test
        void testEmptyPolygon() {
            Map<String, Object> params = extrudeParams(Collections.emptyList(), 50);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("at least 3"));
        }

        @Test
        void testOnePoint() {
            List<?> onePoint = Arrays.asList(
                    Arrays.asList(0.0, 0.0)
            );
            Map<String, Object> params = extrudeParams(onePoint, 50);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("at least 3"));
        }

        @Test
        void testTwoPoints() {
            List<?> twoPoints = Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0)
            );
            Map<String, Object> params = extrudeParams(twoPoints, 50);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("at least 3"));
        }
    }

    // ======================== POLYGON FORMAT ========================

    @Nested
    class PolygonFormat {

        @Test
        void testPolygonNotArray() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", "not an array");
            params.put("height", 50.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("polygon"));
        }

        @Test
        void testPolygonPointSingleCoord() {
            List<?> badPoints = Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0),       // only 1 coord
                    Arrays.asList(100.0, 100.0)
            );
            Map<String, Object> params = extrudeParams(badPoints, 50);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid polygon"));
        }

        @Test
        void testPolygonPointThreeCoords() {
            List<?> badPoints = Arrays.asList(
                    Arrays.asList(0.0, 0.0, 0.0),  // 3 coords instead of 2
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            );
            Map<String, Object> params = extrudeParams(badPoints, 50);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid polygon"));
        }

        @Test
        void testPolygonPointNotList() {
            List<?> badPoints = Arrays.asList(
                    "not a point",
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            );
            Map<String, Object> params = extrudeParams(badPoints, 50);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid polygon"));
        }

        @Test
        void testPolygonPointNonNumeric() {
            List<?> badPoints = Arrays.asList(
                    Arrays.asList("abc", 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            );
            Map<String, Object> params = extrudeParams(badPoints, 50);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid polygon"));
        }
    }

    // ======================== MISSING POLYGON ========================

    @Nested
    class MissingPolygon {

        @Test
        void testMissingPolygon() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("height", 250.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("polygon"));
        }
    }

    // ======================== HEIGHT VALIDATION ========================

    @Nested
    class HeightValidation {

        private List<?> validTriangle() {
            return Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            );
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", validTriangle());
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testZeroHeight() {
            Map<String, Object> params = extrudeParams(validTriangle(), 0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeHeight() {
            Map<String, Object> params = extrudeParams(validTriangle(), -10);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }

    // ======================== TRANSPARENCY VALIDATION ========================

    @Nested
    class TransparencyValidation {

        private List<?> validTriangle() {
            return Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            );
        }

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = extrudeParams(validTriangle(), 50);
            params.put("transparency", 1.5);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = extrudeParams(validTriangle(), 50);
            params.put("transparency", -0.1);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("transparency"));
        }
    }

    // ======================== CONCAVE POLYGON SHAPES ========================
    // NOTE: These tests verify that complex polygon shapes pass validation.
    // They catch NoClassDefFoundError (JOGL not available in test classpath)
    // because valid polygons proceed to Java3D geometry creation which requires JOGL.

    @Nested
    class ConcavePolygonValidation {

        private void assertPassesValidation(List<?> polygon, double height, String desc) {
            Map<String, Object> params = extrudeParams(polygon, height);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("polygon"),
                            desc + " should pass polygon validation: " + resp.getMessage());
                    assertFalse(resp.getMessage().contains("at least 3"),
                            desc + " should pass point count: " + resp.getMessage());
                    assertFalse(resp.getMessage().contains("Invalid polygon"),
                            desc + " should pass format validation: " + resp.getMessage());
                }
            } catch (NoClassDefFoundError e) {
                // Expected: valid polygon passed validation, reached Java3D which is not available
                assertTrue(e.getMessage().contains("jogamp") || e.getMessage().contains("j3d")
                                || e.getMessage().contains("GL") || e.getMessage().contains("javax/media"),
                        desc + ": unexpected NoClassDefFoundError: " + e.getMessage());
            }
        }

        @Test
        void testUShapedPolygonPassesValidation() {
            List<?> uShape = Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0),
                    Arrays.asList(80.0, 100.0),
                    Arrays.asList(80.0, 20.0),
                    Arrays.asList(20.0, 20.0),
                    Arrays.asList(20.0, 100.0),
                    Arrays.asList(0.0, 100.0)
            );
            assertPassesValidation(uShape, 50, "U-shaped polygon (8 points)");
        }

        @Test
        void testLShapedPolygonPassesValidation() {
            List<?> lShape = Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 50.0),
                    Arrays.asList(50.0, 50.0),
                    Arrays.asList(50.0, 100.0),
                    Arrays.asList(0.0, 100.0)
            );
            assertPassesValidation(lShape, 50, "L-shaped polygon (6 points)");
        }

        @Test
        void testCollinearPointsPassesValidation() {
            List<?> collinear = Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(50.0, 0.0),
                    Arrays.asList(100.0, 0.0)
            );
            assertPassesValidation(collinear, 50, "Collinear points (degenerate)");
        }

        @Test
        void testPentagonPassesValidation() {
            List<?> pentagon = Arrays.asList(
                    Arrays.asList(50.0, 0.0),
                    Arrays.asList(100.0, 35.0),
                    Arrays.asList(80.0, 100.0),
                    Arrays.asList(20.0, 100.0),
                    Arrays.asList(0.0, 35.0)
            );
            assertPassesValidation(pentagon, 30, "Pentagon (5 points)");
        }

        @Test
        void testComplexConcavePassesValidation() {
            List<?> star = Arrays.asList(
                    Arrays.asList(50.0, 0.0),
                    Arrays.asList(60.0, 30.0),
                    Arrays.asList(100.0, 35.0),
                    Arrays.asList(70.0, 55.0),
                    Arrays.asList(80.0, 100.0),
                    Arrays.asList(50.0, 70.0),
                    Arrays.asList(20.0, 100.0),
                    Arrays.asList(30.0, 55.0),
                    Arrays.asList(0.0, 35.0),
                    Arrays.asList(40.0, 30.0)
            );
            assertPassesValidation(star, 25, "Star polygon (10 points, concave)");
        }
    }

    // ======================== POLYGON WITH INTEGER COORDS ========================

    @Nested
    class PolygonCoordTypes {

        @Test
        void testIntegerCoordinatesPassValidation() {
            List<?> intPoints = Arrays.asList(
                    Arrays.asList(0, 0),
                    Arrays.asList(100, 0),
                    Arrays.asList(100, 100)
            );
            Map<String, Object> params = extrudeParams(intPoints, 50);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("Invalid polygon"),
                            "Integer coordinates should be parsed: " + resp.getMessage());
                }
            } catch (NoClassDefFoundError e) {
                // Expected: passed validation, hit Java3D (JOGL not available)
            }
        }

        @Test
        void testMixedCoordinateTypesPassValidation() {
            List<?> mixedPoints = Arrays.asList(
                    Arrays.asList(0, 0.0),
                    Arrays.asList(100.0, 0),
                    Arrays.asList(100, 100.0)
            );
            Map<String, Object> params = extrudeParams(mixedPoints, 50);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("Invalid polygon"),
                            "Mixed int/double should be parsed: " + resp.getMessage());
                }
            } catch (NoClassDefFoundError e) {
                // Expected: passed validation, hit Java3D (JOGL not available)
            }
        }
    }

    // ======================== VALIDATION ORDER ========================

    @Nested
    class ValidationOrder {

        @Test
        void testPolygonCheckedBeforeHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            // No polygon, no height
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("polygon"));
        }

        @Test
        void testHeightCheckedBeforeTransparency() {
            List<?> validTriangle = Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            );
            Map<String, Object> params = extrudeParams(validTriangle, -10);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testPointCountCheckedBeforePointFormat() {
            // 2 points (less than 3) — point count error, not format error
            List<?> twoPoints = Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0)
            );
            Map<String, Object> params = extrudeParams(twoPoints, 50);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("at least 3"));
        }
    }
}
