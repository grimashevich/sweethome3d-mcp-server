package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DeleteFurnitureHandlerTest {

    private DeleteFurnitureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteFurnitureHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testDeleteSingleFurniture() {
        HomePieceOfFurniture piece = addFurniture(home, "Table", 100, 200);

        Response resp = handler.execute(makeIdRequest("delete_furniture", piece.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testResponseContainsDeletedInfo() {
        HomePieceOfFurniture piece = addFurniture(home, "Sofa", 150, 250);

        Response resp = handler.execute(makeIdRequest("delete_furniture", piece.getId()), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals("Sofa", data.get("name"));
        assertEquals(150f, ((Number) data.get("x")).floatValue(), 0.01f);
        assertEquals(250f, ((Number) data.get("y")).floatValue(), 0.01f);
        assertTrue(((String) data.get("message")).contains("Sofa"));
    }

    @Test
    void testDeleteFromMultiple() {
        addFurniture(home, "Chair", 0, 0);
        HomePieceOfFurniture table = addFurniture(home, "Table", 100, 100);
        addFurniture(home, "Lamp", 200, 200);

        Response resp = handler.execute(makeIdRequest("delete_furniture", table.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("Table", resp.getData().get("name"));
        assertEquals(2, home.getFurniture().size());

        List<HomePieceOfFurniture> remaining = home.getFurniture();
        assertEquals("Chair", remaining.get(0).getName());
        assertEquals("Lamp", remaining.get(1).getName());
    }

    @Test
    void testDeleteFirst() {
        HomePieceOfFurniture a = addFurniture(home, "A", 0, 0);
        addFurniture(home, "B", 100, 100);

        Response resp = handler.execute(makeIdRequest("delete_furniture", a.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("A", resp.getData().get("name"));
        assertEquals(1, home.getFurniture().size());
        assertEquals("B", home.getFurniture().get(0).getName());
    }

    @Test
    void testDeleteLast() {
        addFurniture(home, "A", 0, 0);
        HomePieceOfFurniture b = addFurniture(home, "B", 100, 100);

        Response resp = handler.execute(makeIdRequest("delete_furniture", b.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("B", resp.getData().get("name"));
        assertEquals(1, home.getFurniture().size());
        assertEquals("A", home.getFurniture().get(0).getName());
    }

    @Test
    void testIdNotFound() {
        addFurniture(home, "Table", 0, 0);

        Response resp = handler.execute(makeIdRequest("delete_furniture", "nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getFurniture().size());
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeIdRequest("delete_furniture", "any-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
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
        Map<String, Object> idProp = (Map<String, Object>) props.get("id");
        assertEquals("string", idProp.get("type"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }
}
