package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.command.handler.GenerateShapeHandler;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.CommandException;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for PipeShapeGenerator via GenerateShapeHandler (mode="pipe").
 * Focuses on input validation and parameter parsing.
 * Actual 3D generation requires JOGL runtime (tested live in TEST2).
 */
class PipeShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> pipeParams(double outerRadius, double innerRadius, double height) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "pipe");
        params.put("outerRadius", outerRadius);
        params.put("innerRadius", innerRadius);
        params.put("height", height);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingOuterRadius() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "pipe");
            params.put("innerRadius", 8.0);
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("outerRadius"));
        }

        @Test
        void testMissingInnerRadius() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "pipe");
            params.put("outerRadius", 10.0);
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("innerRadius"));
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "pipe");
            params.put("outerRadius", 10.0);
            params.put("innerRadius", 8.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testMissingAllRequired() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "pipe");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            // outerRadius is checked first
            assertTrue(resp.getMessage().contains("outerRadius"));
        }
    }

    // ======================== INVALID VALUES ========================

    @Nested
    class InvalidValues {

        @Test
        void testZeroOuterRadius() {
            Response resp = handler.execute(new Request("generate_shape", pipeParams(0, 8, 100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("outerRadius"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeOuterRadius() {
            Response resp = handler.execute(new Request("generate_shape", pipeParams(-5, 3, 100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("outerRadius"));
        }

        @Test
        void testZeroInnerRadius() {
            Response resp = handler.execute(new Request("generate_shape", pipeParams(10, 0, 100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("innerRadius"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeInnerRadius() {
            Response resp = handler.execute(new Request("generate_shape", pipeParams(10, -3, 100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("innerRadius"));
        }

        @Test
        void testInnerRadiusEqualToOuter() {
            Response resp = handler.execute(new Request("generate_shape", pipeParams(10, 10, 100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("innerRadius"));
            assertTrue(resp.getMessage().contains("less than"));
        }

        @Test
        void testInnerRadiusGreaterThanOuter() {
            Response resp = handler.execute(new Request("generate_shape", pipeParams(5, 10, 100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("innerRadius"));
            assertTrue(resp.getMessage().contains("less than"));
        }

        @Test
        void testZeroHeight() {
            Response resp = handler.execute(new Request("generate_shape", pipeParams(10, 8, 0)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeHeight() {
            Response resp = handler.execute(new Request("generate_shape", pipeParams(10, 8, -50)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }

    // ======================== DIVISIONS VALIDATION ========================

    @Nested
    class DivisionsValidation {

        @Test
        void testDivisionsBelowMin() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("divisions", 4);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsAboveMax() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("divisions", 100);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsJustBelowMin() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("divisions", 7); // min is 8
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsJustAboveMax() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("divisions", 65); // max is 64
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsErrorMessageContainsBounds() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("divisions", 2);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("8"));
            assertTrue(resp.getMessage().contains("64"));
        }
    }

    // ======================== ARC ANGLE VALIDATION ========================

    @Nested
    class ArcAngleValidation {

        @Test
        void testArcAngleZero() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("arcAngle", 0.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("arcAngle"));
        }

        @Test
        void testArcAngleNegative() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("arcAngle", -90.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("arcAngle"));
        }

        @Test
        void testArcAngleAbove360() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("arcAngle", 361.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("arcAngle"));
        }

        @Test
        void testArcAngleBoundary360Valid() {
            // 360 is valid (full circle) — validation should pass.
            // Actual 3D generation requires JOGL (not in test classpath).
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("arcAngle", 360.0);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("arcAngle"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: passed validation, hit Java3D (JOGL not available)
            }
        }

        @Test
        void testArcAngleSmallPositiveValid() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("arcAngle", 1.0);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("arcAngle"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: passed validation, hit Java3D (JOGL not available)
            }
        }
    }

    // ======================== TRANSPARENCY VALIDATION ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("transparency", 2.0);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("transparency", -0.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencySlightlyAboveOne() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("transparency", 1.01);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencySlightlyBelowZero() {
            Map<String, Object> params = pipeParams(10, 8, 100);
            params.put("transparency", -0.01);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }
    }

    // ======================== VALIDATION ORDER ========================

    @Nested
    class ValidationOrder {

        @Test
        void testOuterRadiusCheckedFirst() {
            Response resp = handler.execute(new Request("generate_shape",
                    pipeParams(-5, -3, -100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("outerRadius"));
        }

        @Test
        void testInnerRadiusCheckedBeforeHeight() {
            Map<String, Object> params = pipeParams(10, -3, -100);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("innerRadius"));
        }

        @Test
        void testInnerLessThanOuterCheckedBeforeHeight() {
            Map<String, Object> params = pipeParams(5, 10, -100);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("innerRadius"));
            assertTrue(resp.getMessage().contains("less than"));
        }

        @Test
        void testHeightCheckedBeforeTransparency() {
            Map<String, Object> params = pipeParams(10, 8, -100);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }

    // ======================== EDGE CASES ========================

    @Nested
    class EdgeCases {

        @Test
        void testThinWallPipe() {
            // innerRadius very close to outerRadius — should pass validation
            Map<String, Object> params = pipeParams(10, 9.99, 100);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                // Validation passes; 3D generation may fail without JOGL
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("innerRadius"));
                    assertFalse(resp.getMessage().contains("outerRadius"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: passed validation, hit Java3D (JOGL not available)
            }
        }
    }
}
