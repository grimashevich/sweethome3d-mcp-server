package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for GenerateShapeHandler.
 * Focuses on input validation and parameter parsing.
 * Actual 3D generation requires Java3D runtime (tested live).
 */
class GenerateShapeHandlerTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    // ======================== MODE VALIDATION ========================

    @Test
    void testMissingMode() {
        Request req = new Request("generate_shape", new LinkedHashMap<>());
        Response resp = handler.execute(req, accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mode"));
    }

    @Test
    void testEmptyMode() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "");
        Request req = new Request("generate_shape", params);
        Response resp = handler.execute(req, accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mode"));
    }

    @Test
    void testUnknownMode() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "unknown");
        Request req = new Request("generate_shape", params);
        Response resp = handler.execute(req, accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("unknown"));
    }

    // ======================== EXTRUDE VALIDATION ========================

    @Nested
    class ExtrudeMode {

        @Test
        void testMissingPolygon() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("height", 250.0);
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("polygon"));
        }

        @Test
        void testPolygonNotArray() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", "not an array");
            params.put("height", 250.0);
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("polygon"));
        }

        @Test
        void testPolygonTooFewPoints() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0)
            ));
            params.put("height", 250.0);
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("at least 3"));
        }

        @Test
        void testPolygonInvalidPointFormat() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0),  // only 1 coord
                    Arrays.asList(100.0, 100.0)
            ));
            params.put("height", 250.0);
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid polygon"));
        }

        @Test
        void testMissingHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            ));
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testNegativeHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            ));
            params.put("height", -10.0);
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testZeroHeight() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            ));
            params.put("height", 0.0);
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("height"));
        }

        @Test
        void testInvalidTransparency() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            ));
            params.put("height", 250.0);
            params.put("transparency", 1.5);
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("transparency"));
        }

        @Test
        void testNegativeTransparency() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("polygon", Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(100.0, 100.0)
            ));
            params.put("height", 250.0);
            params.put("transparency", -0.1);
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("transparency"));
        }
    }

    // ======================== MESH VALIDATION ========================

    @Nested
    class MeshMode {

        @Test
        void testMissingVertices() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("triangles", Arrays.asList(Arrays.asList(0, 1, 2)));
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("vertices"));
        }

        @Test
        void testVerticesNotArray() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", "not array");
            params.put("triangles", Arrays.asList(Arrays.asList(0, 1, 2)));
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("vertices"));
        }

        @Test
        void testTooFewVertices() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", Arrays.asList(
                    Arrays.asList(0.0, 0.0, 0.0),
                    Arrays.asList(100.0, 0.0, 0.0)
            ));
            params.put("triangles", Arrays.asList(Arrays.asList(0, 1, 0)));
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("at least 3"));
        }

        @Test
        void testInvalidVertexFormat() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", Arrays.asList(
                    Arrays.asList(0.0, 0.0),  // only 2 coords
                    Arrays.asList(100.0, 0.0, 0.0),
                    Arrays.asList(0.0, 100.0, 0.0)
            ));
            params.put("triangles", Arrays.asList(Arrays.asList(0, 1, 2)));
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid vertices"));
        }

        @Test
        void testMissingTriangles() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", Arrays.asList(
                    Arrays.asList(0.0, 0.0, 0.0),
                    Arrays.asList(100.0, 0.0, 0.0),
                    Arrays.asList(0.0, 100.0, 0.0)
            ));
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("triangles"));
        }

        @Test
        void testEmptyTriangles() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", Arrays.asList(
                    Arrays.asList(0.0, 0.0, 0.0),
                    Arrays.asList(100.0, 0.0, 0.0),
                    Arrays.asList(0.0, 100.0, 0.0)
            ));
            params.put("triangles", Arrays.asList());
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("at least 1"));
        }

        @Test
        void testTriangleIndexOutOfBounds() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", Arrays.asList(
                    Arrays.asList(0.0, 0.0, 0.0),
                    Arrays.asList(100.0, 0.0, 0.0),
                    Arrays.asList(0.0, 100.0, 0.0)
            ));
            params.put("triangles", Arrays.asList(Arrays.asList(0, 1, 5))); // index 5 out of bounds
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid triangles"));
        }

        @Test
        void testNegativeTriangleIndex() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", Arrays.asList(
                    Arrays.asList(0.0, 0.0, 0.0),
                    Arrays.asList(100.0, 0.0, 0.0),
                    Arrays.asList(0.0, 100.0, 0.0)
            ));
            params.put("triangles", Arrays.asList(Arrays.asList(0, -1, 2)));
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid triangles"));
        }

        @Test
        void testTriangleWrongSize() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", Arrays.asList(
                    Arrays.asList(0.0, 0.0, 0.0),
                    Arrays.asList(100.0, 0.0, 0.0),
                    Arrays.asList(0.0, 100.0, 0.0)
            ));
            params.put("triangles", Arrays.asList(Arrays.asList(0, 1))); // only 2 indices
            Request req = new Request("generate_shape", params);
            Response resp = handler.execute(req, accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid triangles"));
        }
    }

    // ======================== DESCRIPTOR ========================

    @Test
    void testDescriptionNotEmpty() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
    }

    @Test
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));
        assertNotNull(schema.get("properties"));
        assertTrue(schema.get("properties") instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("mode"));
        assertTrue(props.containsKey("polygon"));
        assertTrue(props.containsKey("height"));
        assertTrue(props.containsKey("vertices"));
        assertTrue(props.containsKey("triangles"));
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("transparency"));
        assertTrue(props.containsKey("elevation"));
        assertTrue(props.containsKey("color"));
    }

    @Test
    void testSchemaRequiredFields() {
        Map<String, Object> schema = handler.getSchema();
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("mode"));
    }
}
