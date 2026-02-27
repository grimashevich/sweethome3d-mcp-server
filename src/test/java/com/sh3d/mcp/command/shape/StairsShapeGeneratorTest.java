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
 * Tests for StairsShapeGenerator via GenerateShapeHandler (mode="stairs").
 * Focuses on input validation. Actual 3D generation requires JOGL runtime.
 */
class StairsShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> stairsParams(double width, double depth, double height, int steps) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "stairs");
        params.put("width", width);
        params.put("depth", depth);
        params.put("height", height);
        params.put("steps", steps);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingWidth() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "stairs");
            params.put("depth", 200.0);
            params.put("height", 300.0);
            params.put("steps", 10);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testMissingDepth() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "stairs");
            params.put("width", 100.0);
            params.put("height", 300.0);
            params.put("steps", 10);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "stairs");
            params.put("width", 100.0);
            params.put("depth", 200.0);
            params.put("steps", 10);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testMissingSteps() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "stairs");
            params.put("width", 100.0);
            params.put("depth", 200.0);
            params.put("height", 300.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("steps"));
        }
    }

    // ======================== INVALID VALUES ========================

    @Nested
    class InvalidValues {

        @Test
        void testNegativeWidth() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(-100, 200, 300, 10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testZeroWidth() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(0, 200, 300, 10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testNegativeDepth() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(100, -200, 300, 10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testZeroDepth() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(100, 0, 300, 10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testNegativeHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(100, 200, -300, 10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testZeroHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(100, 200, 0, 10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }

    // ======================== STEPS VALIDATION ========================

    @Nested
    class StepsValidation {

        @Test
        void testStepsLessThan1() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(100, 200, 300, 0)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("steps"));
            assertTrue(resp.getMessage().contains("between 1 and 50"));
        }

        @Test
        void testStepsNegative() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(100, 200, 300, -5)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("steps"));
        }

        @Test
        void testStepsGreaterThan50() {
            Response resp = handler.execute(
                    new Request("generate_shape", stairsParams(100, 200, 300, 51)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("steps"));
            assertTrue(resp.getMessage().contains("between 1 and 50"));
        }

        @Test
        void testStepsInvalidType() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "stairs");
            params.put("width", 100.0);
            params.put("depth", 200.0);
            params.put("height", 300.0);
            params.put("steps", "not-a-number");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("steps"));
            assertTrue(resp.getMessage().contains("integer"));
        }

        @Test
        void testStepsExactly1IsValid() {
            // steps=1 is at the lower boundary — validation should pass
            // Actual execution hits Java3D (NoClassDefFoundError), so we catch that
            try {
                Response resp = handler.execute(
                        new Request("generate_shape", stairsParams(100, 200, 300, 1)),
                        accessor);
                // If we somehow get a response, it should NOT be a validation error about steps
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("steps"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code which is unavailable in tests
            }
        }

        @Test
        void testStepsExactly50IsValid() {
            // steps=50 is at the upper boundary — validation should pass
            try {
                Response resp = handler.execute(
                        new Request("generate_shape", stairsParams(100, 200, 300, 50)),
                        accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("steps"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }
    }

    // ======================== TRANSPARENCY ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = stairsParams(100, 200, 300, 10);
            params.put("transparency", 1.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = stairsParams(100, 200, 300, 10);
            params.put("transparency", -0.1);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }
    }
}
