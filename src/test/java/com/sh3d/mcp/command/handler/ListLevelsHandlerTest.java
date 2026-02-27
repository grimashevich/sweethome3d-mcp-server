package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class ListLevelsHandlerTest {

    private ListLevelsHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ListLevelsHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testEmptyLevels() {
        Response resp = handler.execute(makeRequest(), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, ((Number) resp.getData().get("levelCount")).intValue());
        @SuppressWarnings("unchecked")
        List<Object> levels = (List<Object>) resp.getData().get("levels");
        assertTrue(levels.isEmpty());
    }

    @Test
    void testSingleLevel() {
        addLevel("Ground Floor", 0, 12, 250);

        Response resp = handler.execute(makeRequest(), accessor);

        assertTrue(resp.isOk());
        assertEquals(1, ((Number) resp.getData().get("levelCount")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) resp.getData().get("levels");
        Map<String, Object> level = levels.get(0);
        assertEquals(0, ((Number) level.get("id")).intValue());
        assertEquals("Ground Floor", level.get("name"));
        assertEquals(0.0, ((Number) level.get("elevation")).doubleValue(), 0.01);
        assertEquals(250.0, ((Number) level.get("height")).doubleValue(), 0.01);
        assertEquals(12.0, ((Number) level.get("floorThickness")).doubleValue(), 0.01);
    }

    @Test
    void testMultipleLevels() {
        addLevel("Ground", 0, 12, 250);
        addLevel("Second", 250, 12, 250);
        addLevel("Attic", 500, 10, 180);

        Response resp = handler.execute(makeRequest(), accessor);

        assertTrue(resp.isOk());
        assertEquals(3, ((Number) resp.getData().get("levelCount")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) resp.getData().get("levels");
        assertEquals(3, levels.size());
    }

    @Test
    void testSelectedLevelFlag() {
        Level ground = addLevel("Ground", 0, 12, 250);
        Level upper = addLevel("Upper", 250, 12, 250);
        home.setSelectedLevel(upper);

        Response resp = handler.execute(makeRequest(), accessor);

        assertTrue(resp.isOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) resp.getData().get("levels");

        assertFalse((Boolean) levels.get(0).get("selected"));
        assertTrue((Boolean) levels.get(1).get("selected"));
    }

    @Test
    void testViewableFlag() {
        Level level = addLevel("Floor", 0, 12, 250);
        assertTrue((Boolean) getLevelData(0).get("viewable"));

        level.setViewable(false);
        assertFalse((Boolean) getLevelData(0).get("viewable"));
    }

    @Test
    void testLevelIds() {
        addLevel("A", 0, 12, 250);
        addLevel("B", 250, 12, 250);
        addLevel("C", 500, 12, 250);

        Response resp = handler.execute(makeRequest(), accessor);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) resp.getData().get("levels");
        assertEquals(0, ((Number) levels.get(0).get("id")).intValue());
        assertEquals(1, ((Number) levels.get(1).get("id")).intValue());
        assertEquals(2, ((Number) levels.get(2).get("id")).intValue());
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
        assertTrue(handler.getDescription().toLowerCase().contains("level"));

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.isEmpty());
    }

    private Level addLevel(String name, float elevation, float floorThickness, float height) {
        Level level = new Level(name, elevation, floorThickness, height);
        home.addLevel(level);
        return level;
    }

    private Map<String, Object> getLevelData(int index) {
        Response resp = handler.execute(makeRequest(), accessor);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> levels = (List<Map<String, Object>>) resp.getData().get("levels");
        return levels.get(index);
    }

    private Request makeRequest() {
        return new Request("list_levels", Collections.emptyMap());
    }
}
