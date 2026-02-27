package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for GenerateShapeHandler dispatch to parametric primitive generators.
 * Verifies mode routing, error handling, and schema correctness.
 * Actual 3D generation requires JOGL runtime (tested live in TEST2).
 */
class GenerateShapeHandlerPrimitiveTest {

    private GenerateShapeHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new GenerateShapeHandler();
        Home home = new Home();
        UserPreferences prefs = mock(UserPreferences.class);
        accessor = new HomeAccessor(home, prefs);
    }

    // ======================== MODE DISPATCH (VALIDATION) ========================

    @Nested
    class ModeDispatch {

        @Test
        void testBoxModeReachesBoxValidator() {
            // No width — should get box-specific error
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "box");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("width"),
                    "box mode should check for width: " + resp.getMessage());
        }

        @Test
        void testSphereModeReachesSphereValidator() {
            // No radius — should get sphere-specific error
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "sphere");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"),
                    "sphere mode should check for radius: " + resp.getMessage());
        }

        @Test
        void testCylinderModeReachesCylinderValidator() {
            // No radius — should get cylinder-specific error
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cylinder");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radius"),
                    "cylinder mode should check for radius: " + resp.getMessage());
        }

        @Test
        void testConeModeReachesConeValidator() {
            // No radiusBottom — should get cone-specific error
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "cone");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("radiusBottom"),
                    "cone mode should check for radiusBottom: " + resp.getMessage());
        }

        @Test
        void testExtrudeModeReachesExtrudeValidator() {
            // No polygon — should get extrude-specific error
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "extrude");
            params.put("height", 250.0);
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("polygon"),
                    "extrude mode should check for polygon: " + resp.getMessage());
        }

        @Test
        void testMeshModeReachesMeshValidator() {
            // No vertices — should get mesh-specific error
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "mesh");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("vertices"),
                    "mesh mode should check for vertices: " + resp.getMessage());
        }
    }

    // ======================== ERROR CASES ========================

    @Nested
    class ErrorCases {

        @Test
        void testUnknownMode() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "pyramid");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Unknown mode"));
            assertTrue(resp.getMessage().contains("pyramid"));
        }

        @Test
        void testNullMode() {
            Map<String, Object> params = new LinkedHashMap<>();
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("mode"));
        }

        @Test
        void testEmptyMode() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("mode"));
        }

        @Test
        void testWhitespaceMode() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "   ");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("mode"));
        }

        @Test
        void testModeWithTypo() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "bpx");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Unknown mode"));
        }

        @Test
        void testModeCaseSensitive() {
            // "Box" (uppercase B) should not match "box"
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "Box");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Unknown mode"));
        }

        @Test
        void testUnknownModeErrorListsAvailable() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mode", "invalid");
            Response resp = handler.execute(new Request("generate_shape", params), accessor);
            assertTrue(resp.isError());
            // Error message should list available modes
            String msg = resp.getMessage();
            assertTrue(msg.contains("extrude"));
            assertTrue(msg.contains("mesh"));
            assertTrue(msg.contains("box"));
            assertTrue(msg.contains("sphere"));
            assertTrue(msg.contains("cylinder"));
            assertTrue(msg.contains("cone"));
        }
    }

    // ======================== SCHEMA ========================

    @Nested
    class Schema {

        @Test
        void testSchemaContainsPrimitiveProperties() {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");

            assertTrue(props.containsKey("width"), "Schema should have 'width'");
            assertTrue(props.containsKey("depth"), "Schema should have 'depth'");
            assertTrue(props.containsKey("radius"), "Schema should have 'radius'");
            assertTrue(props.containsKey("radiusBottom"), "Schema should have 'radiusBottom'");
            assertTrue(props.containsKey("radiusTop"), "Schema should have 'radiusTop'");
            assertTrue(props.containsKey("divisions"), "Schema should have 'divisions'");
        }

        @Test
        void testSchemaContainsPositionAndAngle() {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
            assertTrue(props.containsKey("x"), "Schema should have 'x'");
            assertTrue(props.containsKey("y"), "Schema should have 'y'");
            assertTrue(props.containsKey("angle"), "Schema should have 'angle'");
        }

        @Test
        void testModeEnumIncludesPrimitives() {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> modeProp = (Map<String, Object>) props.get("mode");
            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) modeProp.get("enum");

            assertTrue(enumValues.contains("box"), "Mode enum should include 'box'");
            assertTrue(enumValues.contains("sphere"), "Mode enum should include 'sphere'");
            assertTrue(enumValues.contains("cylinder"), "Mode enum should include 'cylinder'");
            assertTrue(enumValues.contains("cone"), "Mode enum should include 'cone'");
            assertTrue(enumValues.contains("extrude"), "Mode enum should include 'extrude'");
            assertTrue(enumValues.contains("mesh"), "Mode enum should include 'mesh'");
        }

        @Test
        void testModeEnumExactSize() {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> modeProp = (Map<String, Object>) props.get("mode");
            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) modeProp.get("enum");
            assertEquals(13, enumValues.size(), "Should have exactly 13 modes (extrude, mesh, box, sphere, cylinder, cone, wedge, arch, stairs, torus, hemisphere, pipe, csg)");
        }

        @Test
        void testSchemaContainsCommonProperties() {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
            assertTrue(props.containsKey("name"));
            assertTrue(props.containsKey("transparency"));
            assertTrue(props.containsKey("elevation"));
            assertTrue(props.containsKey("color"));
        }

        @Test
        void testDescriptionMentionsPrimitives() {
            String desc = handler.getDescription();
            assertTrue(desc.contains("box"));
            assertTrue(desc.contains("sphere"));
            assertTrue(desc.contains("cylinder"));
            assertTrue(desc.contains("cone"));
        }
    }
}
