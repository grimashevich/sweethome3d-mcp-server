package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class ModifyWallHandlerTest {

    private ModifyWallHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ModifyWallHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Coordinates ---

    @Test
    void testModifyXStart() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "xStart", 100.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(100f, w.getXStart(), 0.01f);
        assertEquals(100.0, ((Number) resp.getData().get("xStart")).doubleValue(), 0.01);
    }

    @Test
    void testModifyYStart() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "yStart", 50.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(50f, w.getYStart(), 0.01f);
    }

    @Test
    void testModifyXEnd() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "xEnd", 600.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(600f, w.getXEnd(), 0.01f);
    }

    @Test
    void testModifyYEnd() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "yEnd", 300.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(300f, w.getYEnd(), 0.01f);
    }

    @Test
    void testModifyAllCoordinates() {
        Wall w = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", w.getId());
        params.put("xStart", 100.0);
        params.put("yStart", 200.0);
        params.put("xEnd", 600.0);
        params.put("yEnd", 400.0);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(100f, w.getXStart(), 0.01f);
        assertEquals(200f, w.getYStart(), 0.01f);
        assertEquals(600f, w.getXEnd(), 0.01f);
        assertEquals(400f, w.getYEnd(), 0.01f);
    }

    @Test
    void testCoordinatesInSchemaNotRequired() {
        Map<String, Object> schema = handler.getSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("xStart"));
        assertTrue(props.containsKey("yStart"));
        assertTrue(props.containsKey("xEnd"));
        assertTrue(props.containsKey("yEnd"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertFalse(required.contains("xStart"));
        assertFalse(required.contains("yStart"));
        assertFalse(required.contains("xEnd"));
        assertFalse(required.contains("yEnd"));
    }

    // --- Height ---

    @Test
    void testModifyHeight() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "height", 300.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(300f, w.getHeight(), 0.01f);
        assertEquals(300.0, ((Number) resp.getData().get("height")).doubleValue(), 0.01);
    }

    @Test
    void testModifyHeightAtEnd() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "heightAtEnd", 200.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(200f, w.getHeightAtEnd(), 0.01f);
        assertEquals(200.0, ((Number) resp.getData().get("heightAtEnd")).doubleValue(), 0.01);
    }

    @Test
    void testClearHeightAtEnd() {
        Wall w = addWall(0, 0, 500, 0);
        w.setHeightAtEnd(200f);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", w.getId());
        params.put("heightAtEnd", null);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertNull(w.getHeightAtEnd());
        assertNull(resp.getData().get("heightAtEnd"));
    }

    @Test
    void testInvalidHeight() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "height", -10.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("height"));
        assertTrue(resp.getMessage().contains("positive"));
    }

    // --- Thickness ---

    @Test
    void testModifyThickness() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "thickness", 20.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(20f, w.getThickness(), 0.01f);
        assertEquals(20.0, ((Number) resp.getData().get("thickness")).doubleValue(), 0.01);
    }

    @Test
    void testInvalidThickness() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "thickness", 0.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("thickness"));
        assertTrue(resp.getMessage().contains("positive"));
    }

    // --- Arc extent ---

    @Test
    void testSetArcExtent() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "arcExtent", 45.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(Math.toRadians(45), w.getArcExtent(), 0.01);
        assertEquals(45.0, ((Number) resp.getData().get("arcExtent")).doubleValue(), 0.5);
    }

    @Test
    void testClearArcExtent() {
        Wall w = addWall(0, 0, 500, 0);
        w.setArcExtent((float) Math.toRadians(30));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", w.getId());
        params.put("arcExtent", null);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertNull(w.getArcExtent());
        assertNull(resp.getData().get("arcExtent"));
    }

    // --- Colors ---

    @Test
    void testSetLeftSideColor() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "leftSideColor", "#FF0000"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFF0000, (int) w.getLeftSideColor());
        assertEquals("#FF0000", resp.getData().get("leftSideColor"));
    }

    @Test
    void testSetRightSideColor() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "rightSideColor", "#00FF00"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0x00FF00, (int) w.getRightSideColor());
        assertEquals("#00FF00", resp.getData().get("rightSideColor"));
    }

    @Test
    void testSetTopColor() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "topColor", "#0000FF"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0x0000FF, (int) w.getTopColor());
        assertEquals("#0000FF", resp.getData().get("topColor"));
    }

    @Test
    void testColorShortcutSetsBothSidesAndTop() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "color", "#AABBCC"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xAABBCC, (int) w.getLeftSideColor());
        assertEquals(0xAABBCC, (int) w.getRightSideColor());
        assertEquals(0xAABBCC, (int) w.getTopColor());
    }

    @Test
    void testColorShortcutClearsAll() {
        Wall w = addWall(0, 0, 500, 0);
        w.setLeftSideColor(0xFF0000);
        w.setRightSideColor(0x00FF00);
        w.setTopColor(0x0000FF);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", w.getId());
        params.put("color", null);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertNull(w.getLeftSideColor());
        assertNull(w.getRightSideColor());
        assertNull(w.getTopColor());
    }

    @Test
    void testIndividualColorOverridesShortcut() {
        Wall w = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", w.getId());
        params.put("color", "#AAAAAA");
        params.put("leftSideColor", "#FF0000");
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFF0000, (int) w.getLeftSideColor());
        assertEquals(0xAAAAAA, (int) w.getRightSideColor());
        assertEquals(0xAAAAAA, (int) w.getTopColor());
    }

    @Test
    void testClearLeftSideColor() {
        Wall w = addWall(0, 0, 500, 0);
        w.setLeftSideColor(0xFF0000);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", w.getId());
        params.put("leftSideColor", null);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertNull(w.getLeftSideColor());
        assertNull(resp.getData().get("leftSideColor"));
    }

    @Test
    void testInvalidColorFormat() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "leftSideColor", "red"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("leftSideColor"));
    }

    @Test
    void testInvalidColorShortcut() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "color", "invalid"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("color"));
    }

    // --- Shininess ---

    @Test
    void testSetLeftSideShininess() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "leftSideShininess", 0.5), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.5f, w.getLeftSideShininess(), 0.01f);
        assertEquals(0.5, ((Number) resp.getData().get("leftSideShininess")).doubleValue(), 0.01);
    }

    @Test
    void testSetRightSideShininess() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "rightSideShininess", 0.8), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.8f, w.getRightSideShininess(), 0.01f);
    }

    @Test
    void testShininessShortcutSetsBothSides() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "shininess", 0.7), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.7f, w.getLeftSideShininess(), 0.01f);
        assertEquals(0.7f, w.getRightSideShininess(), 0.01f);
    }

    @Test
    void testShininessOutOfRange() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "shininess", 1.5), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("shininess"));
        assertTrue(resp.getMessage().contains("0.0"));
    }

    @Test
    void testShininessNegative() {
        Wall w = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w.getId(), "leftSideShininess", -0.1), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("leftSideShininess"));
    }

    // --- ID validation ---

    @Test
    void testIdNotFound() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest("nonexistent-id", "height", 300.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeRequest("any-id", "height", 300.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testNoModifiableProperties() {
        Wall w = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", w.getId());
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    // --- Partial updates ---

    @Test
    void testPartialUpdatePreservesOtherProperties() {
        Wall w = addWall(0, 0, 500, 0);
        w.setLeftSideColor(0xFF0000);

        Response resp = handler.execute(makeRequest(w.getId(), "height", 300.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(300f, w.getHeight(), 0.01f);
        assertEquals(0xFF0000, (int) w.getLeftSideColor());
    }

    @Test
    void testMultiplePropertiesAtOnce() {
        Wall w = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", w.getId());
        params.put("height", 300.0);
        params.put("thickness", 15.0);
        params.put("color", "#FF5500");
        params.put("shininess", 0.5);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(300f, w.getHeight(), 0.01f);
        assertEquals(15f, w.getThickness(), 0.01f);
        assertEquals(0xFF5500, (int) w.getLeftSideColor());
        assertEquals(0xFF5500, (int) w.getRightSideColor());
        assertEquals(0.5f, w.getLeftSideShininess(), 0.01f);
        assertEquals(0.5f, w.getRightSideShininess(), 0.01f);
    }

    // --- Response format ---

    @Test
    void testResponseContainsAllFields() {
        Wall w = addWall(100, 200, 600, 200);

        Response resp = handler.execute(makeRequest(w.getId(), "height", 300.0), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(w.getId(), data.get("id"));
        assertNotNull(data.get("xStart"));
        assertNotNull(data.get("yStart"));
        assertNotNull(data.get("xEnd"));
        assertNotNull(data.get("yEnd"));
        assertNotNull(data.get("thickness"));
        assertNotNull(data.get("height"));
        assertTrue(data.containsKey("heightAtEnd"));
        assertTrue(data.containsKey("arcExtent"));
        assertTrue(data.containsKey("leftSideColor"));
        assertTrue(data.containsKey("rightSideColor"));
        assertTrue(data.containsKey("topColor"));
        assertNotNull(data.get("leftSideShininess"));
        assertNotNull(data.get("rightSideShininess"));
    }

    // --- Descriptor ---

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));
        assertTrue(props.containsKey("height"));
        assertTrue(props.containsKey("heightAtEnd"));
        assertTrue(props.containsKey("thickness"));
        assertTrue(props.containsKey("arcExtent"));
        assertTrue(props.containsKey("color"));
        assertTrue(props.containsKey("leftSideColor"));
        assertTrue(props.containsKey("rightSideColor"));
        assertTrue(props.containsKey("topColor"));
        assertTrue(props.containsKey("shininess"));
        assertTrue(props.containsKey("leftSideShininess"));
        assertTrue(props.containsKey("rightSideShininess"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
        assertEquals(1, required.size());
    }

    // --- Helpers ---

    private Wall addWall(float xStart, float yStart, float xEnd, float yEnd) {
        return TestFixtures.addWall(home, xStart, yStart, xEnd, yEnd);
    }

    private Request makeRequest(String id, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new Request("modify_wall", params);
    }
}
