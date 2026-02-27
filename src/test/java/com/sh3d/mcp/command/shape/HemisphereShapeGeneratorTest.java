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
 * Tests for HemisphereShapeGenerator via GenerateShapeHandler (mode="hemisphere").
 * Focuses on input validation and parameter parsing.
 * Actual 3D generation requires JOGL runtime (tested live in TEST2).
 */
class HemisphereShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> hemisphereParams(double radius) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "hemisphere");
        params.put("radius", radius);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingRadius() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "hemisphere");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testOnlyModeAndOptionals() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "hemisphere");
            params.put("name", "MyDome");
            params.put("elevation", 10.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }
    }

    // ======================== INVALID RADIUS ========================

    @Nested
    class InvalidRadius {

        @Test
        void testZeroRadius() {
            Response resp = handler.execute(new Request("generate_shape", hemisphereParams(0)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeRadius() {
            Response resp = handler.execute(new Request("generate_shape", hemisphereParams(-10)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testVerySmallNegativeRadius() {
            Response resp = handler.execute(new Request("generate_shape", hemisphereParams(-0.001)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }
    }

    // ======================== DIVISIONS VALIDATION ========================

    @Nested
    class DivisionsValidation {

        @Test
        void testDivisionsBelowMin() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", 4);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsAboveMax() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", 100);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsZero() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", 0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsNegative() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", -1);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsJustBelowMin() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", 7); // min is 8
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsJustAboveMax() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", 65); // max is 64
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsErrorMessageContainsBounds() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", 2);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("8"));
            assertTrue(resp.getMessage().contains("64"));
        }
    }

    // ======================== CUT ANGLE VALIDATION ========================

    @Nested
    class CutAngleValidation {

        @Test
        void testCutAngleZero() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("cutAngle", 0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("cutAngle"));
        }

        @Test
        void testCutAngleNegative() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("cutAngle", -45);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("cutAngle"));
        }

        @Test
        void testCutAngleAtMax180() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("cutAngle", 180);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("cutAngle"));
        }

        @Test
        void testCutAngleAboveMax() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("cutAngle", 200);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("cutAngle"));
        }

        @Test
        void testCutAngleErrorMessageContainsBounds() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("cutAngle", 0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("1.0"));
            assertTrue(resp.getMessage().contains("179.0"));
        }
    }

    // ======================== TRANSPARENCY VALIDATION ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("transparency", 1.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = hemisphereParams(50);
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
        void testRadiusCheckedBeforeDivisions() {
            Map<String, Object> params = hemisphereParams(-5);
            params.put("divisions", 200);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testRadiusCheckedBeforeCutAngle() {
            Map<String, Object> params = hemisphereParams(-5);
            params.put("cutAngle", 300);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testDivisionsCheckedBeforeCutAngle() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", 2);
            params.put("cutAngle", 300);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testCutAngleCheckedBeforeTransparency() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("cutAngle", 0);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("cutAngle"));
        }

        @Test
        void testDivisionsCheckedBeforeTransparency() {
            Map<String, Object> params = hemisphereParams(50);
            params.put("divisions", 2);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }
    }
}
