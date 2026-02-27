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
 * Tests for CylinderShapeGenerator via GenerateShapeHandler (mode="cylinder").
 * Focuses on input validation and parameter parsing.
 * Actual 3D generation requires JOGL runtime (tested live in TEST2).
 */
class CylinderShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> cylinderParams(double radius, double height) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "cylinder");
        params.put("radius", radius);
        params.put("height", height);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingRadius() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cylinder");
            params.put("height", 250.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cylinder");
            params.put("radius", 20.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testMissingBothRequired() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cylinder");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            // radius is checked first
            assertTrue(resp.getMessage().contains("radius"));
        }
    }

    // ======================== INVALID VALUES ========================

    @Nested
    class InvalidValues {

        @Test
        void testZeroRadius() {
            Response resp = handler.execute(new Request("generate_shape", cylinderParams(0, 250)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeRadius() {
            Response resp = handler.execute(new Request("generate_shape", cylinderParams(-5, 250)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testZeroHeight() {
            Response resp = handler.execute(new Request("generate_shape", cylinderParams(20, 0)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeHeight() {
            Response resp = handler.execute(new Request("generate_shape", cylinderParams(20, -100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testVerySmallNegativeRadius() {
            Response resp = handler.execute(new Request("generate_shape", cylinderParams(-0.001, 250)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }
    }

    // ======================== TRANSPARENCY VALIDATION ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = cylinderParams(20, 250);
            params.put("transparency", 2.0);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = cylinderParams(20, 250);
            params.put("transparency", -0.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencySlightlyAboveOne() {
            Map<String, Object> params = cylinderParams(20, 250);
            params.put("transparency", 1.01);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencySlightlyBelowZero() {
            Map<String, Object> params = cylinderParams(20, 250);
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
        void testRadiusCheckedBeforeHeight() {
            Response resp = handler.execute(new Request("generate_shape", cylinderParams(-5, -100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testHeightCheckedBeforeTransparency() {
            Map<String, Object> params = cylinderParams(20, -100);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }
}
