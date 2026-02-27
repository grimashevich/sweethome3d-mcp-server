package com.sh3d.mcp.command.shape;

import com.sh3d.mcp.command.handler.GenerateShapeHandler;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for ExtrudeShapeGenerator via GenerateShapeHandler (mode="extrude").
 * Focuses on input validation. Actual 3D generation requires JOGL runtime.
 */
class ExtrudeShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    private Map<String, Object> extrudeParams(List<List<Number>> polygon, double height) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "extrude");
        List<Object> polyList = new ArrayList<>();
        for (List<Number> pt : polygon) {
            polyList.add(new ArrayList<>(pt));
        }
        params.put("polygon", polyList);
        params.put("height", height);
        return params;
    }

    private List<List<Number>> triangle() {
        return Arrays.asList(
                Arrays.asList(0, 0),
                Arrays.asList(100, 0),
                Arrays.asList(50, 100)
        );
    }

    // ======================== MISSING POLYGON ========================

    @Nested
    class MissingPolygon {

        @Test
        void testMissingPolygon() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("polygon"));
        }

        @Test
        void testPolygonLessThan3Points() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            List<Object> poly = new ArrayList<>();
            poly.add(Arrays.asList(0, 0));
            poly.add(Arrays.asList(100, 0));
            params.put("polygon", poly);
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("at least 3"));
        }

        @Test
        void testPolygonNotArray() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", "not-an-array");
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("polygon"));
        }
    }

    // ======================== INVALID POLYGON FORMAT ========================

    @Nested
    class InvalidPolygonFormat {

        @Test
        void testPolygonPointNotArray() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            List<Object> poly = new ArrayList<>();
            poly.add("not-a-point");
            poly.add(Arrays.asList(100, 0));
            poly.add(Arrays.asList(50, 100));
            params.put("polygon", poly);
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid polygon format"));
        }

        @Test
        void testPolygonPointWrongSize() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            List<Object> poly = new ArrayList<>();
            poly.add(Arrays.asList(0, 0, 0)); // 3 coords instead of 2
            poly.add(Arrays.asList(100, 0));
            poly.add(Arrays.asList(50, 100));
            params.put("polygon", poly);
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid polygon format"));
        }

        @Test
        void testPolygonPointNonNumeric() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            List<Object> poly = new ArrayList<>();
            poly.add(Arrays.asList("a", "b"));
            poly.add(Arrays.asList(100, 0));
            poly.add(Arrays.asList(50, 100));
            params.put("polygon", poly);
            params.put("height", 100.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid polygon format"));
        }
    }

    // ======================== MISSING / INVALID HEIGHT ========================

    @Nested
    class HeightValidation {

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            List<Object> poly = new ArrayList<>();
            poly.add(Arrays.asList(0, 0));
            poly.add(Arrays.asList(100, 0));
            poly.add(Arrays.asList(50, 100));
            params.put("polygon", poly);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testZeroHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", extrudeParams(triangle(), 0)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testNegativeHeight() {
            Response resp = handler.execute(
                    new Request("generate_shape", extrudeParams(triangle(), -50)),
                    accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
            assertTrue(resp.getMessage().contains("positive"));
        }
    }

    // ======================== TRANSPARENCY ========================

    @Nested
    class TransparencyValidation {

        @Test
        void testTransparencyTooHigh() {
            Map<String, Object> params = extrudeParams(triangle(), 100);
            params.put("transparency", 1.5);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("transparency"));
        }

        @Test
        void testTransparencyNegative() {
            Map<String, Object> params = extrudeParams(triangle(), 100);
            params.put("transparency", -0.1);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("transparency"));
        }
    }
}
