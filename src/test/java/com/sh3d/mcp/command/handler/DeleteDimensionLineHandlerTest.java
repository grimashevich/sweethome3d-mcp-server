package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DeleteDimensionLineHandlerTest {

    private DeleteDimensionLineHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteDimensionLineHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testDeleteSingleDimensionLine() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getDimensionLines().size());
    }

    @Test
    void testResponseContainsDeletedInfo() {
        DimensionLine dim = addDimensionLine(home, 100, 200, 600, 200, 30);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(100f, ((Number) data.get("xStart")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yStart")).floatValue(), 0.01f);
        assertEquals(600f, ((Number) data.get("xEnd")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yEnd")).floatValue(), 0.01f);
        assertEquals(30f, ((Number) data.get("offset")).floatValue(), 0.01f);
        assertEquals(500f, ((Number) data.get("length")).floatValue(), 0.01f);
        assertTrue(((String) data.get("message")).contains("deleted"));
    }

    @Test
    void testDeletesOnlyTargetFromMultiple() {
        addDimensionLine(home, 0, 0, 500, 0, 25);
        DimensionLine target = addDimensionLine(home, 0, 0, 0, 400, 25);
        addDimensionLine(home, 0, 400, 500, 400, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", target.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, home.getDimensionLines().size());
        for (DimensionLine remaining : new ArrayList<>(home.getDimensionLines())) {
            assertNotEquals(target.getId(), remaining.getId());
        }
    }

    @Test
    void testIdNotFound() {
        addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", "nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getDimensionLines().size());
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeIdRequest("delete_dimension_line", "any-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testMissingId() {
        Response resp = handler.execute(
                new Request("delete_dimension_line", Collections.emptyMap()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("id"));
    }

    @Test
    void testDoesNotTouchOtherObjectTypes() {
        addWall(home, 0, 0, 500, 0);
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getDimensionLines().size());
        assertEquals(1, home.getWalls().size(), "walls must be left untouched");
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }
}
