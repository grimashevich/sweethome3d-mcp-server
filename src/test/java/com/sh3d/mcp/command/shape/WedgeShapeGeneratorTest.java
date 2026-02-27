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
 * Tests for WedgeShapeGenerator via GenerateShapeHandler (mode="wedge").
 * Focuses on input validation. Actual 3D generation requires JOGL runtime.
 */
class WedgeShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> wedgeParams(double width, double depth, double height) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "wedge");
        params.put("width", width);
        params.put("depth", depth);
        params.put("height", height);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingWidth() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "wedge");
            params.put("depth", 50.0);
            params.put("height", 200.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testMissingDepth() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "wedge");
            params.put("width", 100.0);
            params.put("height", 200.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "wedge");
            params.put("width", 100.0);
            params.put("depth", 50.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }

    // ======================== INVALID DIMENSIONS ========================

    @Nested
    class InvalidDimensions {

        @Test
        void testZeroWidth() {
            Response resp = handler.execute(
                    new Request("generate_shape", wedgeParams(0, 50, 200)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeWidth() {
            Response resp = handler.execute(
                    new Request("generate_shape", wedgeParams(-100, 50, 200)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testZeroDepth() {
            Response resp = handler.execute(
                    new Request("generate_shape", wedgeParams(100, 0, 200)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testNegativeDepth() {
            Response resp = handler.execute(
                    new Request("generate_shape", wedgeParams(100, -50, 200)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testZeroHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", wedgeParams(100, 50, 0)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testNegativeHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", wedgeParams(100, 50, -200)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }

    // ======================== TAPER VALIDATION ========================

    @Nested
    class TaperValidation {

        @Test
        void testInvalidTaperValue() {
            Map<String, Object> params = wedgeParams(100, 50, 200);
            params.put("taper", "z");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("taper"));
            assertTrue(resp.getMessage().contains("'x' or 'y'"));
        }

        @Test
        void testInvalidTaperValueNumeric() {
            Map<String, Object> params = wedgeParams(100, 50, 200);
            params.put("taper", "123");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("taper"));
        }

        @Test
        void testTaperXValid() {
            Map<String, Object> params = wedgeParams(100, 50, 200);
            params.put("taper", "x");
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("taper"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testTaperYValid() {
            Map<String, Object> params = wedgeParams(100, 50, 200);
            params.put("taper", "y");
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("taper"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testDefaultTaperWhenNotProvided() {
            // When taper not provided, should default to "y" (no validation error)
            Map<String, Object> params = wedgeParams(100, 50, 200);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("taper"));
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
            Map<String, Object> params = wedgeParams(100, 50, 200);
            params.put("transparency", 1.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = wedgeParams(100, 50, 200);
            params.put("transparency", -0.1);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }
    }

    // ======================== VALIDATION ORDER ========================

    @Nested
    class ValidationOrder {

        @Test
        void testWidthCheckedBeforeDepth() {
            Response resp = handler.execute(
                    new Request("generate_shape", wedgeParams(-1, -1, 200)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testDepthCheckedBeforeHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", wedgeParams(100, -1, -1)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testHeightCheckedBeforeTaper() {
            Map<String, Object> params = wedgeParams(100, 50, -1);
            params.put("taper", "invalid");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }
}
