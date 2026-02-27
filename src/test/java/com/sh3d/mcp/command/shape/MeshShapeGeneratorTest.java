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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for MeshShapeGenerator wallThickness feature via GenerateShapeHandler (mode="mesh").
 * Focuses on input validation and parameter parsing.
 * Actual 3D generation requires JOGL runtime (tested live in TEST2).
 */
class MeshShapeGeneratorTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    /**
     * Creates a minimal valid mesh request (single triangle) with optional wallThickness.
     */
    private Map<String, Object> meshParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "mesh");
        params.put("vertices", Arrays.asList(
                Arrays.asList(0.0, 0.0, 0.0),
                Arrays.asList(100.0, 0.0, 0.0),
                Arrays.asList(0.0, 100.0, 0.0)
        ));
        params.put("triangles", Arrays.asList(Arrays.asList(0, 1, 2)));
        return params;
    }

    // ======================== WALL THICKNESS VALIDATION ========================

    @Nested
    class WallThicknessValidation {

        @Test
        void testWallThicknessZero() {
            Map<String, Object> params = meshParams();
            params.put("wallThickness", 0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("wallThickness"));
            assertTrue(resp.getMessage().contains("positive"));
        }

        @Test
        void testWallThicknessNegative() {
            Map<String, Object> params = meshParams();
            params.put("wallThickness", -5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("wallThickness"));
        }

        @Test
        void testWallThicknessInvalidType() {
            Map<String, Object> params = meshParams();
            params.put("wallThickness", "not_a_number");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("wallThickness"));
            assertTrue(resp.getMessage().contains("number"));
        }

        @Test
        void testWallThicknessVerySmallNegative() {
            Map<String, Object> params = meshParams();
            params.put("wallThickness", -0.001);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("wallThickness"));
        }
    }

    // ======================== VALIDATION ORDER ========================

    @Nested
    class ValidationOrder {

        @Test
        void testVerticesCheckedBeforeWallThickness() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("triangles", Arrays.asList(Arrays.asList(0, 1, 2)));
            params.put("wallThickness", -5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("vertices"));
        }

        @Test
        void testTrianglesCheckedBeforeWallThickness() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            params.put("vertices", Arrays.asList(
                    Arrays.asList(0.0, 0.0, 0.0),
                    Arrays.asList(100.0, 0.0, 0.0),
                    Arrays.asList(0.0, 100.0, 0.0)
            ));
            params.put("wallThickness", -5.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("triangles"));
        }

        @Test
        void testTransparencyCheckedBeforeWallThickness() {
            Map<String, Object> params = meshParams();
            params.put("transparency", 5.0);
            params.put("wallThickness", -1.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("transparency"));
        }

        @Test
        void testWallThicknessCheckedAfterTransparency() {
            Map<String, Object> params = meshParams();
            params.put("transparency", 0.5);
            params.put("wallThickness", -1.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("wallThickness"));
        }
    }

    // ======================== SCHEMA ========================

    @Nested
    class Schema {

        @Test
        @SuppressWarnings("unchecked")
        void testSchemaContainsWallThickness() {
            Map<String, Object> schema = handler.getSchema();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertTrue(props.containsKey("wallThickness"),
                    "Schema should contain wallThickness property");

            Map<String, Object> wtProp = (Map<String, Object>) props.get("wallThickness");
            assertEquals("number", wtProp.get("type"));
            assertNotNull(wtProp.get("description"));
            assertTrue(((String) wtProp.get("description")).toLowerCase().contains("mesh"),
                    "wallThickness description should mention mesh mode");
        }
    }
}
