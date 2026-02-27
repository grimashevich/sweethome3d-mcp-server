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
 * Tests for ArchShapeGenerator via GenerateShapeHandler (mode="arch").
 * Focuses on input validation. Actual 3D generation requires JOGL runtime.
 */
class ArchShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> archParams(double width, double height, double depth) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "arch");
        params.put("width", width);
        params.put("height", height);
        params.put("depth", depth);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingWidth() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "arch");
            params.put("height", 100.0);
            params.put("depth", 30.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "arch");
            params.put("width", 200.0);
            params.put("depth", 30.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testMissingDepth() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "arch");
            params.put("width", 200.0);
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }
    }

    // ======================== INVALID DIMENSIONS ========================

    @Nested
    class InvalidDimensions {

        @Test
        void testZeroWidth() {
            Response resp = handler.execute(
                    new Request("generate_shape", archParams(0, 100, 30)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeWidth() {
            Response resp = handler.execute(
                    new Request("generate_shape", archParams(-200, 100, 30)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testZeroHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", archParams(200, 0, 30)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testNegativeHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", archParams(200, -100, 30)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testZeroDepth() {
            Response resp = handler.execute(
                    new Request("generate_shape", archParams(200, 100, 0)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testNegativeDepth() {
            Response resp = handler.execute(
                    new Request("generate_shape", archParams(200, 100, -30)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }
    }

    // ======================== ARC ANGLE VALIDATION ========================

    @Nested
    class ArcAngleValidation {

        @Test
        void testArcAngleZero() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("arcAngle", 0.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("arcAngle"));
        }

        @Test
        void testArcAngleNegative() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("arcAngle", -90.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("arcAngle"));
        }

        @Test
        void testArcAngleGreaterThan360() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("arcAngle", 361.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("arcAngle"));
        }

        @Test
        void testArcAngle360Valid() {
            // arcAngle=360 is the boundary — should be accepted (condition is <= 360)
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("arcAngle", 360.0);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("arcAngle"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testArcAngleSmallPositive() {
            // arcAngle=0.1 — small but valid
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("arcAngle", 0.1);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("arcAngle"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }
    }

    // ======================== DIVISIONS VALIDATION ========================

    @Nested
    class DivisionsValidation {

        @Test
        void testDivisionsLessThan8() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("divisions", 7);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
            assertTrue(resp.getMessage().contains("between"));
        }

        @Test
        void testDivisionsGreaterThan64() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("divisions", 65);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
            assertTrue(resp.getMessage().contains("between"));
        }

        @Test
        void testDivisionsExactly8() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("divisions", 8);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("divisions"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testDivisionsExactly64() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("divisions", 64);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("divisions"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testDivisionsInvalidType() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("divisions", "not-a-number");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
            assertTrue(resp.getMessage().contains("integer"));
        }

        @Test
        void testDivisionsZero() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("divisions", 0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }
    }

    // ======================== TRANSPARENCY ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("transparency", 1.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = archParams(200, 100, 30);
            params.put("transparency", -0.1);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }
    }
}
