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
 * Tests for SphereShapeGenerator via GenerateShapeHandler (mode="sphere").
 * Focuses on input validation and parameter parsing.
 * Actual 3D generation requires JOGL runtime (tested live in TEST2).
 */
class SphereShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> sphereParams(double radius) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "sphere");
        params.put("radius", radius);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingRadius() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "sphere");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testOnlyModeProvided() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "sphere");
            params.put("name", "MySphere");
            params.put("elevation", 10.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }
    }

    // ======================== INVALID VALUES ========================

    @Nested
    class InvalidValues {

        @Test
        void testZeroRadius() {
            Response resp = handler.execute(new Request("generate_shape", sphereParams(0)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeRadius() {
            Response resp = handler.execute(new Request("generate_shape", sphereParams(-10)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testVerySmallNegativeRadius() {
            Response resp = handler.execute(new Request("generate_shape", sphereParams(-0.001)), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }
    }

    // ======================== DIVISIONS VALIDATION ========================

    @Nested
    class DivisionsValidation {

        @Test
        void testDivisionsBelowMin() {
            Map<String, Object> params = sphereParams(50);
            params.put("divisions", 4);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            // SphereShapeGenerator returns error for out-of-range divisions
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsAboveMax() {
            Map<String, Object> params = sphereParams(50);
            params.put("divisions", 100);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsZero() {
            Map<String, Object> params = sphereParams(50);
            params.put("divisions", 0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsNegative() {
            Map<String, Object> params = sphereParams(50);
            params.put("divisions", -1);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsJustBelowMin() {
            Map<String, Object> params = sphereParams(50);
            params.put("divisions", 7); // min is 8
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsJustAboveMax() {
            Map<String, Object> params = sphereParams(50);
            params.put("divisions", 65); // max is 64
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }

        @Test
        void testDivisionsErrorMessageContainsBounds() {
            Map<String, Object> params = sphereParams(50);
            params.put("divisions", 2);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            // Message should mention the valid range (8-64)
            assertTrue(resp.getMessage().contains("8"));
            assertTrue(resp.getMessage().contains("64"));
        }
    }

    // ======================== TRANSPARENCY VALIDATION ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = sphereParams(50);
            params.put("transparency", 1.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = sphereParams(50);
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
            Map<String, Object> params = sphereParams(-5);
            params.put("divisions", 200);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"));
        }

        @Test
        void testDivisionsCheckedBeforeTransparency() {
            Map<String, Object> params = sphereParams(50);
            params.put("divisions", 2);
            params.put("transparency", 5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("divisions"));
        }
    }
}
