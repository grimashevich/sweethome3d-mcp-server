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

class AddLevelHandlerTest {

    private AddLevelHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new AddLevelHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testAddSingleLevel() {
        Response resp = handler.execute(makeRequest("Ground Floor", 0, null, null), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals("Ground Floor", data.get("name"));
        assertEquals(0.0, ((Number) data.get("elevation")).doubleValue(), 0.01);
        assertEquals(250.0, ((Number) data.get("height")).doubleValue(), 0.01);
        assertEquals(12.0, ((Number) data.get("floorThickness")).doubleValue(), 0.01);
        assertEquals(1, ((Number) data.get("levelCount")).intValue());
    }

    @Test
    void testLevelAddedToHome() {
        handler.execute(makeRequest("Floor 1", 0, null, null), accessor);

        List<Level> levels = home.getLevels();
        assertEquals(1, levels.size());
        assertEquals("Floor 1", levels.get(0).getName());
    }

    @Test
    void testNewLevelBecomesSelected() {
        handler.execute(makeRequest("Ground", 0, null, null), accessor);
        handler.execute(makeRequest("Upper", 250, null, null), accessor);

        Level selected = home.getSelectedLevel();
        assertNotNull(selected);
        assertEquals("Upper", selected.getName());
    }

    @Test
    void testAddMultipleLevels() {
        handler.execute(makeRequest("Ground", 0, null, null), accessor);
        Response resp = handler.execute(makeRequest("Second", 250, null, null), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, ((Number) resp.getData().get("levelCount")).intValue());
    }

    @Test
    void testCustomHeight() {
        Response resp = handler.execute(makeRequest("Attic", 500, 180.0, null), accessor);

        assertTrue(resp.isOk());
        assertEquals(180.0, ((Number) resp.getData().get("height")).doubleValue(), 0.01);
    }

    @Test
    void testCustomFloorThickness() {
        Response resp = handler.execute(makeRequest("Basement", -250, null, 20.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(20.0, ((Number) resp.getData().get("floorThickness")).doubleValue(), 0.01);
    }

    @Test
    void testNegativeElevation() {
        Response resp = handler.execute(makeRequest("Basement", -300, null, null), accessor);

        assertTrue(resp.isOk());
        assertEquals(-300.0, ((Number) resp.getData().get("elevation")).doubleValue(), 0.01);
    }

    @Test
    void testMissingName() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("elevation", 0.0);
        Response resp = handler.execute(new Request("add_level", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testEmptyName() {
        Response resp = handler.execute(makeRequest("  ", 0, null, null), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testZeroHeight() {
        Response resp = handler.execute(makeRequest("Bad", 0, 0.0, null), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("height"));
    }

    @Test
    void testNegativeHeight() {
        Response resp = handler.execute(makeRequest("Bad", 0, -100.0, null), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("height"));
    }

    @Test
    void testNegativeFloorThickness() {
        Response resp = handler.execute(makeRequest("Bad", 0, null, -5.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("floorThickness"));
    }

    @Test
    void testIdIsReturnedCorrectly() {
        handler.execute(makeRequest("Ground", 0, null, null), accessor);
        Response resp = handler.execute(makeRequest("Upper", 250, null, null), accessor);

        assertTrue(resp.isOk());
        assertTrue(resp.getData().get("id") instanceof String, "id should be a String");
    }

    @Test
    void testNameTrimmed() {
        Response resp = handler.execute(makeRequest("  Trimmed Level  ", 0, null, null), accessor);

        assertTrue(resp.isOk());
        assertEquals("Trimmed Level", resp.getData().get("name"));
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
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("elevation"));
        assertTrue(props.containsKey("height"));
        assertTrue(props.containsKey("floorThickness"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("name"));
        assertTrue(required.contains("elevation"));
        assertFalse(required.contains("height"));
    }

    private Request makeRequest(String name, float elevation, Double height, Double floorThickness) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (name != null) {
            params.put("name", name);
        }
        params.put("elevation", (double) elevation);
        if (height != null) {
            params.put("height", height);
        }
        if (floorThickness != null) {
            params.put("floorThickness", floorThickness);
        }
        return new Request("add_level", params);
    }
}
