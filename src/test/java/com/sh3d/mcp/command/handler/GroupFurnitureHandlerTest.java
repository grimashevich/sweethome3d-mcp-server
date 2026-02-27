package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class GroupFurnitureHandlerTest {

    private GroupFurnitureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new GroupFurnitureHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testGroupTwoItems() {
        HomePieceOfFurniture a = addFurniture("Tabletop", 100, 100);
        HomePieceOfFurniture b = addFurniture("Leg", 100, 100);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(a.getId(), b.getId()), "Table"), accessor);

        assertTrue(resp.isOk());
        // After grouping: 1 group in home instead of 2 items
        assertEquals(1, home.getFurniture().size());
        assertTrue(home.getFurniture().get(0) instanceof HomeFurnitureGroup);

        Map<String, Object> data = resp.getData();
        assertNotNull(data.get("id"));
        assertEquals("Table", data.get("name"));
        assertEquals(2, data.get("itemCount"));
    }

    @Test
    void testGroupThreeItems() {
        HomePieceOfFurniture a = addFurniture("Top", 100, 100);
        HomePieceOfFurniture b = addFurniture("Leg1", 80, 80);
        HomePieceOfFurniture c = addFurniture("Leg2", 120, 120);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(a.getId(), b.getId(), c.getId()), "Desk"), accessor);

        assertTrue(resp.isOk());
        assertEquals(1, home.getFurniture().size());
        HomeFurnitureGroup group = (HomeFurnitureGroup) home.getFurniture().get(0);
        assertEquals(3, group.getFurniture().size());
        assertEquals("Desk", group.getName());
    }

    @Test
    void testGroupDefaultName() {
        HomePieceOfFurniture a = addFurniture("A", 0, 0);
        HomePieceOfFurniture b = addFurniture("B", 50, 50);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(a.getId(), b.getId()), null), accessor);

        assertTrue(resp.isOk());
        assertEquals("Group", resp.getData().get("name"));
    }

    @Test
    void testGroupReturnsPositionAndSize() {
        HomePieceOfFurniture a = addFurniture("A", 100, 200);
        HomePieceOfFurniture b = addFurniture("B", 300, 400);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(a.getId(), b.getId()), "G"), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertNotNull(data.get("x"));
        assertNotNull(data.get("y"));
        assertNotNull(data.get("width"));
        assertNotNull(data.get("depth"));
        assertNotNull(data.get("height"));
    }

    @Test
    void testGroupPreservesOtherFurniture() {
        HomePieceOfFurniture a = addFurniture("A", 0, 0);
        HomePieceOfFurniture b = addFurniture("B", 50, 50);
        HomePieceOfFurniture c = addFurniture("C", 200, 200);

        handler.execute(makeRequest(Arrays.asList(a.getId(), b.getId()), "AB"), accessor);

        // C should remain, plus the group
        assertEquals(2, home.getFurniture().size());
        boolean foundGroup = false;
        boolean foundC = false;
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            if (piece instanceof HomeFurnitureGroup) foundGroup = true;
            if ("C".equals(piece.getName())) foundC = true;
        }
        assertTrue(foundGroup);
        assertTrue(foundC);
    }

    @Test
    void testGroupIdNotFound() {
        HomePieceOfFurniture a = addFurniture("A", 0, 0);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(a.getId(), "nonexistent"), "G"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testGroupMissingIds() {
        Response resp = handler.execute(new Request("group_furniture", new LinkedHashMap<>()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("ids"));
    }

    @Test
    void testGroupSingleId() {
        HomePieceOfFurniture a = addFurniture("A", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", Arrays.asList(a.getId()));
        Response resp = handler.execute(new Request("group_furniture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("at least 2"));
    }

    @Test
    void testGroupEmptyIds() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", Arrays.asList());
        Response resp = handler.execute(new Request("group_furniture", params), accessor);

        assertTrue(resp.isError());
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
        assertTrue(handler.getDescription().contains("group_furniture"));

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("ids"));
        assertTrue(props.containsKey("name"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("ids"));
    }

    @Test
    void testGroupIdIsStable() {
        HomePieceOfFurniture a = addFurniture("A", 0, 0);
        HomePieceOfFurniture b = addFurniture("B", 50, 50);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(a.getId(), b.getId()), "G"), accessor);

        String groupId = (String) resp.getData().get("id");
        assertNotNull(groupId);
        assertFalse(groupId.isEmpty());

        // Group should be findable by this ID
        HomePieceOfFurniture found = null;
        for (HomePieceOfFurniture p : home.getFurniture()) {
            if (p.getId().equals(groupId)) {
                found = p;
            }
        }
        assertNotNull(found);
        assertTrue(found instanceof HomeFurnitureGroup);
    }

    private HomePieceOfFurniture addFurniture(String name, float x, float y) {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(name, null, null, 50f, 50f, 50f, true, false));
        piece.setX(x);
        piece.setY(y);
        home.addPieceOfFurniture(piece);
        return piece;
    }

    private Request makeRequest(List<String> ids, String name) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", ids);
        if (name != null) {
            params.put("name", name);
        }
        return new Request("group_furniture", params);
    }
}
