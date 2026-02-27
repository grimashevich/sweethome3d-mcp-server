package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SetEnvironmentHandlerTest {

    private SetEnvironmentHandler handler;
    private Home home;
    private HomeAccessor accessor;
    private HomeAccessor accessorWithTextures;

    @BeforeEach
    void setUp() {
        handler = new SetEnvironmentHandler();
        home = new Home();
        accessor = createAccessor(home);

        // Setup texture catalog for texture tests
        TexturesCatalog catalog = new TexturesCatalog();
        TexturesCategory nature = new TexturesCategory("Nature");
        TexturesCategory sky = new TexturesCategory("Sky");

        CatalogTexture grass = new CatalogTexture("Green Grass", null, 100f, 100f);
        CatalogTexture sand = new CatalogTexture("Golden Sand", null, 80f, 80f);
        CatalogTexture clouds = new CatalogTexture("Blue Clouds", null, 200f, 200f);

        catalog.add(nature, grass);
        catalog.add(nature, sand);
        catalog.add(sky, clouds);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getTexturesCatalog()).thenReturn(catalog);
        accessorWithTextures = new HomeAccessor(home, prefs);
    }

    private Request makeRequest(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new Request("set_environment", params);
    }

    // --- Ground color ---

    @Test
    void testSetGroundColor() {
        Response resp = handler.execute(makeRequest("groundColor", "#00FF00"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0x00FF00, home.getEnvironment().getGroundColor());
        assertEquals("#00FF00", resp.getData().get("groundColor"));
    }

    @Test
    void testSetGroundColorUpperCase() {
        Response resp = handler.execute(makeRequest("groundColor", "#AABBCC"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xAABBCC, home.getEnvironment().getGroundColor());
    }

    @Test
    void testSetGroundColorLowerCase() {
        Response resp = handler.execute(makeRequest("groundColor", "#aabbcc"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xAABBCC, home.getEnvironment().getGroundColor());
    }

    @Test
    void testInvalidGroundColorFormat() {
        Response resp = handler.execute(makeRequest("groundColor", "red"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("Invalid groundColor format"));
    }

    @Test
    void testGroundColorNullNotAllowed() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groundColor", null);
        Response resp = handler.execute(new Request("set_environment", params), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("cannot be null"));
    }

    // --- Sky color ---

    @Test
    void testSetSkyColor() {
        Response resp = handler.execute(makeRequest("skyColor", "#FF8800"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFF8800, home.getEnvironment().getSkyColor());
        assertEquals("#FF8800", resp.getData().get("skyColor"));
    }

    @Test
    void testInvalidSkyColorFormat() {
        Response resp = handler.execute(makeRequest("skyColor", "#GGG"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("Invalid skyColor format"));
    }

    @Test
    void testSkyColorNullNotAllowed() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("skyColor", null);
        Response resp = handler.execute(new Request("set_environment", params), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("cannot be null"));
    }

    // --- Light color ---

    @Test
    void testSetLightColor() {
        Response resp = handler.execute(makeRequest("lightColor", "#F0F0F0"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xF0F0F0, home.getEnvironment().getLightColor());
        assertEquals("#F0F0F0", resp.getData().get("lightColor"));
    }

    @Test
    void testInvalidLightColorFormat() {
        Response resp = handler.execute(makeRequest("lightColor", "xyz"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("Invalid lightColor format"));
    }

    // --- Ceiling light color ---

    @Test
    void testSetCeilingLightColor() {
        Response resp = handler.execute(makeRequest("ceilingLightColor", "#E0E0E0"), accessor);

        assertTrue(resp.isOk());
        // Note: SH3D API has typo "ceilling" (double 'l')
        assertEquals(0xE0E0E0, home.getEnvironment().getCeillingLightColor());
        assertEquals("#E0E0E0", resp.getData().get("ceilingLightColor"));
    }

    @Test
    void testInvalidCeilingLightColorFormat() {
        Response resp = handler.execute(makeRequest("ceilingLightColor", "#ZZZ"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("Invalid ceilingLightColor format"));
    }

    // --- Walls alpha ---

    @Test
    void testSetWallsAlphaZero() {
        Response resp = handler.execute(makeRequest("wallsAlpha", 0.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(0f, home.getEnvironment().getWallsAlpha(), 0.001f);
        assertEquals(0.0, (double) resp.getData().get("wallsAlpha"), 0.01);
    }

    @Test
    void testSetWallsAlphaHalf() {
        Response resp = handler.execute(makeRequest("wallsAlpha", 0.5), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.5f, home.getEnvironment().getWallsAlpha(), 0.001f);
    }

    @Test
    void testSetWallsAlphaOne() {
        Response resp = handler.execute(makeRequest("wallsAlpha", 1.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(1f, home.getEnvironment().getWallsAlpha(), 0.001f);
    }

    @Test
    void testWallsAlphaOutOfRangeHigh() {
        Response resp = handler.execute(makeRequest("wallsAlpha", 1.5), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("between 0.0 and 1.0"));
    }

    @Test
    void testWallsAlphaNegative() {
        Response resp = handler.execute(makeRequest("wallsAlpha", -0.1), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("between 0.0 and 1.0"));
    }

    // --- Drawing mode ---

    @Test
    void testSetDrawingModeFill() {
        Response resp = handler.execute(makeRequest("drawingMode", "FILL"), accessor);

        assertTrue(resp.isOk());
        assertEquals(HomeEnvironment.DrawingMode.FILL, home.getEnvironment().getDrawingMode());
        assertEquals("FILL", resp.getData().get("drawingMode"));
    }

    @Test
    void testSetDrawingModeOutline() {
        Response resp = handler.execute(makeRequest("drawingMode", "OUTLINE"), accessor);

        assertTrue(resp.isOk());
        assertEquals(HomeEnvironment.DrawingMode.OUTLINE, home.getEnvironment().getDrawingMode());
    }

    @Test
    void testSetDrawingModeFillAndOutline() {
        Response resp = handler.execute(makeRequest("drawingMode", "FILL_AND_OUTLINE"), accessor);

        assertTrue(resp.isOk());
        assertEquals(HomeEnvironment.DrawingMode.FILL_AND_OUTLINE, home.getEnvironment().getDrawingMode());
    }

    @Test
    void testInvalidDrawingMode() {
        Response resp = handler.execute(makeRequest("drawingMode", "WIREFRAME"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("Invalid drawingMode"));
        assertTrue(resp.getMessage().contains("FILL"));
    }

    // --- All levels visible ---

    @Test
    void testSetAllLevelsVisibleTrue() {
        Response resp = handler.execute(makeRequest("allLevelsVisible", true), accessor);

        assertTrue(resp.isOk());
        assertTrue(home.getEnvironment().isAllLevelsVisible());
        assertEquals(true, resp.getData().get("allLevelsVisible"));
    }

    @Test
    void testSetAllLevelsVisibleFalse() {
        home.getEnvironment().setAllLevelsVisible(true);

        Response resp = handler.execute(makeRequest("allLevelsVisible", false), accessor);

        assertTrue(resp.isOk());
        assertFalse(home.getEnvironment().isAllLevelsVisible());
    }

    // --- Ground texture ---

    @Test
    void testSetGroundTexture() {
        Response resp = handler.execute(
                makeRequest("groundTexture", "Green Grass"), accessorWithTextures);

        assertTrue(resp.isOk());
        assertNotNull(home.getEnvironment().getGroundTexture());
        assertEquals("Green Grass", home.getEnvironment().getGroundTexture().getName());
        assertEquals("Green Grass", resp.getData().get("groundTexture"));
    }

    @Test
    void testClearGroundTexture() {
        // Set a texture first
        handler.execute(makeRequest("groundTexture", "Green Grass"), accessorWithTextures);
        assertNotNull(home.getEnvironment().getGroundTexture());

        // Clear it
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groundTexture", null);
        Response resp = handler.execute(new Request("set_environment", params), accessorWithTextures);

        assertTrue(resp.isOk());
        assertNull(home.getEnvironment().getGroundTexture());
        assertNull(resp.getData().get("groundTexture"));
    }

    @Test
    void testGroundTextureNotFound() {
        Response resp = handler.execute(
                makeRequest("groundTexture", "Nonexistent"), accessorWithTextures);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("Ground texture not found"));
    }

    @Test
    void testGroundTextureWithCategory() {
        Response resp = handler.execute(
                makeRequest("groundTexture", "Green Grass", "groundTextureCategory", "Nature"),
                accessorWithTextures);

        assertTrue(resp.isOk());
        assertEquals("Green Grass", home.getEnvironment().getGroundTexture().getName());
    }

    @Test
    void testGroundTextureCatalogNotAvailable() {
        Response resp = handler.execute(
                makeRequest("groundTexture", "Green Grass"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("not available"));
    }

    // --- Sky texture ---

    @Test
    void testSetSkyTexture() {
        Response resp = handler.execute(
                makeRequest("skyTexture", "Blue Clouds"), accessorWithTextures);

        assertTrue(resp.isOk());
        assertNotNull(home.getEnvironment().getSkyTexture());
        assertEquals("Blue Clouds", home.getEnvironment().getSkyTexture().getName());
    }

    @Test
    void testClearSkyTexture() {
        handler.execute(makeRequest("skyTexture", "Blue Clouds"), accessorWithTextures);
        assertNotNull(home.getEnvironment().getSkyTexture());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("skyTexture", null);
        Response resp = handler.execute(new Request("set_environment", params), accessorWithTextures);

        assertTrue(resp.isOk());
        assertNull(home.getEnvironment().getSkyTexture());
    }

    @Test
    void testSkyTextureNotFound() {
        Response resp = handler.execute(
                makeRequest("skyTexture", "Nonexistent"), accessorWithTextures);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("Sky texture not found"));
    }

    @Test
    void testSkyTextureCatalogNotAvailable() {
        Response resp = handler.execute(
                makeRequest("skyTexture", "Blue Clouds"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("not available"));
    }

    // --- Validation ---

    @Test
    void testNoModifiableProperties() {
        Response resp = handler.execute(
                makeRequest("groundTextureCategory", "Nature"), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    @Test
    void testEmptyParams() {
        Response resp = handler.execute(
                new Request("set_environment", Collections.emptyMap()), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    // --- Partial update ---

    @Test
    void testPartialUpdatePreservesOtherProperties() {
        // Set ground color
        handler.execute(makeRequest("groundColor", "#FF0000"), accessor);
        assertEquals(0xFF0000, home.getEnvironment().getGroundColor());

        // Now set sky color — ground should be preserved
        int groundBefore = home.getEnvironment().getGroundColor();
        handler.execute(makeRequest("skyColor", "#0000FF"), accessor);

        assertEquals(groundBefore, home.getEnvironment().getGroundColor());
        assertEquals(0x0000FF, home.getEnvironment().getSkyColor());
    }

    @Test
    void testMultiplePropertiesAtOnce() {
        Response resp = handler.execute(
                makeRequest("groundColor", "#112233",
                        "skyColor", "#445566",
                        "wallsAlpha", 0.3,
                        "drawingMode", "OUTLINE"),
                accessor);

        assertTrue(resp.isOk());
        HomeEnvironment env = home.getEnvironment();
        assertEquals(0x112233, env.getGroundColor());
        assertEquals(0x445566, env.getSkyColor());
        assertEquals(0.3f, env.getWallsAlpha(), 0.01f);
        assertEquals(HomeEnvironment.DrawingMode.OUTLINE, env.getDrawingMode());
    }

    // --- Response ---

    @Test
    @SuppressWarnings("unchecked")
    void testResponseContainsAllFields() {
        Response resp = handler.execute(makeRequest("groundColor", "#AABBCC"), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertTrue(data.containsKey("groundColor"));
        assertTrue(data.containsKey("groundTexture"));
        assertTrue(data.containsKey("skyColor"));
        assertTrue(data.containsKey("skyTexture"));
        assertTrue(data.containsKey("lightColor"));
        assertTrue(data.containsKey("ceilingLightColor"));
        assertTrue(data.containsKey("wallsAlpha"));
        assertTrue(data.containsKey("drawingMode"));
        assertTrue(data.containsKey("allLevelsVisible"));
    }

    // --- Descriptor ---

    @Test
    void testDescription() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("environment"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoRequired() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("groundColor"));
        assertTrue(props.containsKey("skyColor"));
        assertTrue(props.containsKey("lightColor"));
        assertTrue(props.containsKey("ceilingLightColor"));
        assertTrue(props.containsKey("wallsAlpha"));
        assertTrue(props.containsKey("drawingMode"));
        assertTrue(props.containsKey("allLevelsVisible"));
        assertTrue(props.containsKey("groundTexture"));
        assertTrue(props.containsKey("skyTexture"));
    }
}
