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
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // ======================== TEXTURE VALIDATION ========================

    @Nested
    class TextureValidation {

        @TempDir
        Path tempDir;

        private Map<String, Object> texturedParams(String texture) {
            Map<String, Object> params = meshParams();
            params.put("texture", texture);
            params.put("uvs", Arrays.asList(
                    Arrays.asList(0.0, 0.0),
                    Arrays.asList(100.0, 0.0),
                    Arrays.asList(0.0, 100.0)
            ));
            return params;
        }

        private Response execute(Map<String, Object> params) {
            return handler.execute(new Request("generate_shape", params), accessor);
        }

        @Test
        void testTextureWithoutUvs() {
            Map<String, Object> params = meshParams();
            params.put("texture", "/tmp/tiles.png");
            Response resp = execute(params);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("'uvs'"));
        }

        @Test
        void testUvsWithoutTexture() {
            Map<String, Object> params = texturedParams("x");
            params.remove("texture");
            Response resp = execute(params);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("'texture'"));
        }

        @Test
        void testUvsCountMustMatchVertices() {
            Map<String, Object> params = texturedParams("/tmp/tiles.png");
            params.put("uvs", Arrays.asList(Arrays.asList(0.0, 0.0), Arrays.asList(1.0, 0.0)));
            Response resp = execute(params);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("3 expected, got 2"));
        }

        @Test
        void testUvsMustBePairs() {
            Map<String, Object> params = texturedParams("/tmp/tiles.png");
            params.put("uvs", Arrays.asList(Arrays.asList(0.0), Arrays.asList(1.0, 0.0), Arrays.asList(0.0, 1.0)));
            Response resp = execute(params);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Invalid uvs"));
        }

        @Test
        void testTextureAndColorAreExclusive() {
            Map<String, Object> params = texturedParams("/tmp/tiles.png");
            params.put("color", "#FF0000");
            Response resp = execute(params);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("either 'color' or 'texture'"));
        }

        @Test
        void testTextureAndWallThicknessAreExclusive() {
            Map<String, Object> params = texturedParams("/tmp/tiles.png");
            params.put("wallThickness", 5.0);
            Response resp = execute(params);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("wallThickness"));
        }

        @Test
        void testUnknownTexture() {
            Response resp = execute(texturedParams(tempDir.resolve("missing.png").toString()));
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("Texture not found"));
        }

        @Test
        void testRelativePathIsRejected() {
            Response resp = execute(texturedParams("tiles.png"));
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("absolute path"));
        }

        @Test
        void testFileThatIsNotAnImage() throws Exception {
            File notImage = Files.write(tempDir.resolve("tiles.png"), "not an image".getBytes()).toFile();
            Response resp = execute(texturedParams(notImage.getAbsolutePath()));
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("not a readable image"));
        }

        @Test
        void testNonPositiveTextureSize() throws Exception {
            File image = tempDir.resolve("tiles.png").toFile();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 4,
                    java.awt.image.BufferedImage.TYPE_INT_RGB), "png", image);
            Map<String, Object> params = texturedParams(image.getAbsolutePath());
            params.put("textureWidth", 0);
            Response resp = execute(params);
            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("textureWidth"));
        }

        @Test
        void testNonFiniteUvs() {
            Map<String, Object> params = texturedParams("/tmp/tiles.png");
            params.put("uvs", Arrays.asList(Arrays.asList(0.0, Double.NaN),
                    Arrays.asList(1.0, 0.0), Arrays.asList(0.0, 1.0)));
            Response resp = execute(params);
            assertTrue(resp.isError());
        }

        @Test
        @SuppressWarnings("unchecked")
        void testSchemaContainsTextureParams() {
            Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
            for (String key : Arrays.asList("texture", "uvs", "textureWidth", "textureHeight")) {
                assertTrue(props.containsKey(key), "Schema should contain " + key);
            }
        }
    }
}
