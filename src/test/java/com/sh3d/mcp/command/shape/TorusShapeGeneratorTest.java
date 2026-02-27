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
 * Tests for TorusShapeGenerator via GenerateShapeHandler (mode="torus").
 * Focuses on input validation. Actual 3D generation requires JOGL runtime.
 */
class TorusShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> torusParams(double majorRadius, double minorRadius) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "torus");
        params.put("majorRadius", majorRadius);
        params.put("minorRadius", minorRadius);
        return params;
    }

    // ======================== MISSING REQUIRED PARAMS ========================

    @Nested
    class MissingRequired {

        @Test
        void testMissingMajorRadius() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "torus");
            params.put("minorRadius", 10.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("majorRadius"));
        }

        @Test
        void testMissingMinorRadius() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "torus");
            params.put("majorRadius", 50.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorRadius"));
        }

        @Test
        void testMissingBothRadii() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "torus");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("majorRadius"));
        }
    }

    // ======================== INVALID RADII ========================

    @Nested
    class InvalidRadii {

        @Test
        void testNegativeMajorRadius() {
            Response resp = handler.execute(
                    new Request("generate_shape", torusParams(-50, 10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("majorRadius"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testZeroMajorRadius() {
            Response resp = handler.execute(
                    new Request("generate_shape", torusParams(0, 10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("majorRadius"));
        }

        @Test
        void testNegativeMinorRadius() {
            Response resp = handler.execute(
                    new Request("generate_shape", torusParams(50, -10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorRadius"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testZeroMinorRadius() {
            Response resp = handler.execute(
                    new Request("generate_shape", torusParams(50, 0)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorRadius"));
        }

        @Test
        void testMinorRadiusEqualsMajorRadius() {
            Response resp = handler.execute(
                    new Request("generate_shape", torusParams(50, 50)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorRadius"));
            assertTrue(resp.getMessage().contains("less than"));
        }

        @Test
        void testMinorRadiusGreaterThanMajorRadius() {
            Response resp = handler.execute(
                    new Request("generate_shape", torusParams(50, 60)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorRadius"));
            assertTrue(resp.getMessage().contains("less than"));
        }
    }

    // ======================== MAJOR DIVISIONS ========================

    @Nested
    class MajorDivisionsValidation {

        @Test
        void testMajorDivisionsLessThan8() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("majorDivisions", 7);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("majorDivisions"));
            assertTrue(resp.getMessage().contains("between"));
        }

        @Test
        void testMajorDivisionsGreaterThan64() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("majorDivisions", 65);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("majorDivisions"));
            assertTrue(resp.getMessage().contains("between"));
        }

        @Test
        void testMajorDivisionsExactly8() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("majorDivisions", 8);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("majorDivisions"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testMajorDivisionsExactly64() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("majorDivisions", 64);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("majorDivisions"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testMajorDivisionsInvalidType() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("majorDivisions", "not-a-number");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("majorDivisions"));
            assertTrue(resp.getMessage().contains("integer"));
        }
    }

    // ======================== MINOR DIVISIONS ========================

    @Nested
    class MinorDivisionsValidation {

        @Test
        void testMinorDivisionsLessThan8() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("minorDivisions", 7);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorDivisions"));
            assertTrue(resp.getMessage().contains("between"));
        }

        @Test
        void testMinorDivisionsGreaterThan32() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("minorDivisions", 33);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorDivisions"));
            assertTrue(resp.getMessage().contains("between"));
        }

        @Test
        void testMinorDivisionsExactly8() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("minorDivisions", 8);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("minorDivisions"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testMinorDivisionsExactly32() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("minorDivisions", 32);
            try {
                Response resp = handler.execute(new Request("generate_shape", params), accessor);
                if (resp.isError()) {
                    assertFalse(resp.getMessage().contains("minorDivisions"));
                }
            } catch (NoClassDefFoundError e) {
                // Expected: validation passed, hit Java3D code
            }
        }

        @Test
        void testMinorDivisionsInvalidType() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("minorDivisions", "abc");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorDivisions"));
            assertTrue(resp.getMessage().contains("integer"));
        }
    }

    // ======================== TRANSPARENCY ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = torusParams(50, 10);
            params.put("transparency", 1.5);
            CommandException ex = assertThrows(CommandException.class,
                    () -> handler.execute(new Request("generate_shape", params), accessor));
            assertTrue(ex.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = torusParams(50, 10);
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
        void testMajorRadiusCheckedBeforeMinorRadius() {
            Response resp = handler.execute(
                    new Request("generate_shape", torusParams(-50, -10)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("majorRadius"));
        }

        @Test
        void testRadiiCheckedBeforeDivisions() {
            Map<String, Object> params = torusParams(50, 60); // minorRadius >= majorRadius
            params.put("majorDivisions", 3); // also invalid
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("minorRadius"));
        }
    }
}
