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
 * Tests for BoxShapeGenerator via GenerateShapeHandler (mode="box").
 * Focuses on input validation and parameter parsing.
 * Actual 3D generation requires JOGL runtime (tested live in TEST2).
 */
class BoxShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> boxParams(double width, double depth, double height) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "box");
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
            params.put("mode", "box");
            params.put("depth", 50.0);
            params.put("height", 200.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testMissingDepth() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "box");
            params.put("width", 100.0);
            params.put("height", 200.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "box");
            params.put("width", 100.0);
            params.put("depth", 50.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testMissingAllDimensions() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "box");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            // First check is width
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testMissingWidthAndDepth() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "box");
            params.put("height", 200.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            // width is checked first
            assertTrue(resp.getMessage().contains("width"));
        }
    }

    // ======================== INVALID VALUES (ZERO/NEGATIVE) ========================

    @Nested
    class InvalidValues {

        @Test
        void testZeroWidth() {
            Response resp = handler.execute(new Request("generate_shape", boxParams(0, 50, 200)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeWidth() {
            Response resp = handler.execute(new Request("generate_shape", boxParams(-10, 50, 200)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testZeroDepth() {
            Response resp = handler.execute(new Request("generate_shape", boxParams(100, 0, 200)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeDepth() {
            Response resp = handler.execute(new Request("generate_shape", boxParams(100, -5, 200)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testZeroHeight() {
            Response resp = handler.execute(new Request("generate_shape", boxParams(100, 50, 0)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeHeight() {
            Response resp = handler.execute(new Request("generate_shape", boxParams(100, 50, -100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testVerySmallNegativeWidth() {
            Response resp = handler.execute(new Request("generate_shape", boxParams(-0.001, 50, 200)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }
    }

    // ======================== TRANSPARENCY VALIDATION ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = boxParams(100, 50, 200);
            params.put("transparency", 1.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = boxParams(100, 50, 200);
            params.put("transparency", -0.1);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyWayTooHigh() {
            Map<String, Object> params = boxParams(100, 50, 200);
            params.put("transparency", 100.0);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegativeLarge() {
            Map<String, Object> params = boxParams(100, 50, 200);
            params.put("transparency", -50.0);
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
            // Both width and depth invalid — width should be reported first
            Response resp = handler.execute(new Request("generate_shape", boxParams(-1, -1, 200)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"));
        }

        @Test
        void testDepthCheckedBeforeHeight() {
            // depth and height invalid — depth should be reported first
            Response resp = handler.execute(new Request("generate_shape", boxParams(100, -1, -1)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("depth"));
        }

        @Test
        void testHeightCheckedBeforeTransparency() {
            Map<String, Object> params = boxParams(100, 50, -1);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testTransparencyCheckedAfterDimensions() {
            // All dimensions valid, but transparency invalid
            Map<String, Object> params = boxParams(100, 50, 200);
            params.put("transparency", 2.0);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }
    }
}
