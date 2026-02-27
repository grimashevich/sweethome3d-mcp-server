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
 * Tests for ConeShapeGenerator via GenerateShapeHandler (mode="cone").
 * Covers pointed cone, truncated cone (frustum), and all validation paths.
 * Actual 3D generation requires JOGL runtime (tested live in TEST2).
 */
class ConeShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> coneParams(double radiusBottom, double height) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "cone");
        params.put("radiusBottom", radiusBottom);
        params.put("height", height);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingRadiusBottom() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cone");
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusBottom"));
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cone");
            params.put("radiusBottom", 30.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testMissingBothRequired() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cone");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            // radiusBottom is checked first
            assertTrue(resp.getMessage().contains("radiusBottom"));
        }
    }

    // ======================== INVALID VALUES ========================

    @Nested
    class InvalidValues {

        @Test
        void testZeroRadiusBottom() {
            Response resp = handler.execute(new Request("generate_shape", coneParams(0, 100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusBottom"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeRadiusBottom() {
            Response resp = handler.execute(new Request("generate_shape", coneParams(-10, 100)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusBottom"));
        }

        @Test
        void testNegativeRadiusTop() {
            Map<String, Object> params = coneParams(30, 100);
            params.put("radiusTop", -5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusTop"));
            assertTrue(resp.getMessage().contains("non-negative"));
        }

        @Test
        void testZeroHeight() {
            Response resp = handler.execute(new Request("generate_shape", coneParams(30, 0)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeHeight() {
            Response resp = handler.execute(new Request("generate_shape", coneParams(30, -50)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testVerySmallNegativeRadiusTop() {
            Map<String, Object> params = coneParams(30, 100);
            params.put("radiusTop", -0.001);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusTop"));
        }
    }

    // ======================== TRANSPARENCY VALIDATION ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = coneParams(30, 100);
            params.put("transparency", 1.1);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = coneParams(30, 100);
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
        void testRadiusBottomCheckedFirst() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cone");
            params.put("radiusBottom", -10.0);
            params.put("radiusTop", -5.0);
            params.put("height", -50.0);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusBottom"));
        }

        @Test
        void testRadiusTopCheckedBeforeHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cone");
            params.put("radiusBottom", 30.0);
            params.put("radiusTop", -5.0);
            params.put("height", -50.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusTop"));
        }

        @Test
        void testHeightCheckedBeforeTransparency() {
            Map<String, Object> params = coneParams(30, -100);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }
    }

    // ======================== RADIUS TOP BOUNDARY (VALIDATION ONLY) ========================

    @Nested
    class RadiusTopBoundary {

        @Test
        void testRadiusTopNegativeOneIsInvalid() {
            Map<String, Object> params = coneParams(30, 100);
            params.put("radiusTop", -1.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusTop"));
        }

        @Test
        void testRadiusTopVeryNegativeIsInvalid() {
            Map<String, Object> params = coneParams(30, 100);
            params.put("radiusTop", -100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusTop"));
        }
    }
}
