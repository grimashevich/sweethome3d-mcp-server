package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
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

class SetSelectedLevelHandlerTest {

    private SetSelectedLevelHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new SetSelectedLevelHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testSelectLevel() {
        addLevel("Ground", 0, 12, 250);
        Level upper = addLevel("Upper", 250, 12, 250);
        home.setSelectedLevel(home.getLevels().get(0));

        Response resp = handler.execute(makeRequest(upper.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("Upper", resp.getData().get("name"));
        assertEquals("Upper", home.getSelectedLevel().getName());
    }

    @Test
    void testSelectFirst() {
        Level ground = addLevel("Ground", 0, 12, 250);
        addLevel("Upper", 250, 12, 250);
        home.setSelectedLevel(home.getLevels().get(1));

        Response resp = handler.execute(makeRequest(ground.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("Ground", resp.getData().get("name"));
        assertEquals("Ground", home.getSelectedLevel().getName());
    }

    @Test
    void testResponseContainsLevelData() {
        Level level = addLevel("Test Floor", 100, 15, 300);

        Response resp = handler.execute(makeRequest(level.getId()), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertTrue(data.get("id") instanceof String, "id should be a String");
        assertEquals("Test Floor", data.get("name"));
        assertEquals(100.0, ((Number) data.get("elevation")).doubleValue(), 0.01);
        assertEquals(300.0, ((Number) data.get("height")).doubleValue(), 0.01);
        assertEquals(15.0, ((Number) data.get("floorThickness")).doubleValue(), 0.01);
    }

    @Test
    void testSelectSameLevel() {
        Level only = addLevel("Only", 0, 12, 250);
        home.setSelectedLevel(home.getLevels().get(0));

        Response resp = handler.execute(makeRequest(only.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("Only", resp.getData().get("name"));
    }

    @Test
    void testIdNotFound() {
        addLevel("Ground", 0, 12, 250);

        Response resp = handler.execute(makeRequest("nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testEmptyLevels() {
        Response resp = handler.execute(makeRequest("any-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testViewableFlag() {
        Level level = addLevel("Floor", 0, 12, 250);
        level.setViewable(false);

        Response resp = handler.execute(makeRequest(level.getId()), accessor);

        assertTrue(resp.isOk());
        assertFalse((Boolean) resp.getData().get("viewable"));
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
        assertTrue(handler.getDescription().toLowerCase().contains("level"));

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> idProp = (Map<String, Object>) props.get("id");
        assertEquals("string", idProp.get("type"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }

    private Level addLevel(String name, float elevation, float floorThickness, float height) {
        Level level = new Level(name, elevation, floorThickness, height);
        home.addLevel(level);
        return level;
    }

    private Request makeRequest(String id) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        return new Request("set_selected_level", params);
    }
}
