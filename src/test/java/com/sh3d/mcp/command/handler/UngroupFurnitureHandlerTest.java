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

class UngroupFurnitureHandlerTest {

    private UngroupFurnitureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new UngroupFurnitureHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testUngroupBasic() {
        HomeFurnitureGroup group = createGroup("Table", "Top", "Leg1", "Leg2");

        Response resp = handler.execute(makeRequest(group.getId()), accessor);

        assertTrue(resp.isOk());
        // Group removed, 3 children restored
        assertEquals(3, home.getFurniture().size());
        for (HomePieceOfFurniture p : home.getFurniture()) {
            assertFalse(p instanceof HomeFurnitureGroup);
        }
    }

    @Test
    void testUngroupReturnsItemInfo() {
        HomeFurnitureGroup group = createGroup("Shelf", "Board", "Support");

        Response resp = handler.execute(makeRequest(group.getId()), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals("Shelf", data.get("groupName"));
        assertEquals(2, data.get("itemCount"));

        @SuppressWarnings("unchecked")
        List<String> itemIds = (List<String>) data.get("itemIds");
        assertEquals(2, itemIds.size());
        assertFalse(itemIds.get(0).isEmpty());
    }

    @Test
    void testUngroupPreservesOtherFurniture() {
        HomePieceOfFurniture standalone = addFurniture("Lamp", 300, 300);
        HomeFurnitureGroup group = createGroup("Table", "A", "B");

        handler.execute(makeRequest(group.getId()), accessor);

        // standalone + 2 ungrouped children
        assertEquals(3, home.getFurniture().size());
        boolean foundLamp = false;
        for (HomePieceOfFurniture p : home.getFurniture()) {
            if ("Lamp".equals(p.getName())) foundLamp = true;
        }
        assertTrue(foundLamp);
    }

    @Test
    void testUngroupNotAGroup() {
        HomePieceOfFurniture piece = addFurniture("Chair", 100, 100);

        Response resp = handler.execute(makeRequest(piece.getId()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not a group"));
        // Piece should remain
        assertEquals(1, home.getFurniture().size());
    }

    @Test
    void testUngroupNotFound() {
        Response resp = handler.execute(makeRequest("nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testUngroupMissingId() {
        Response resp = handler.execute(
                new Request("ungroup_furniture", new LinkedHashMap<>()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("id"));
    }

    @Test
    void testUngroupChildrenAreAccessible() {
        HomeFurnitureGroup group = createGroup("Desk", "Surface", "Leg");

        Response resp = handler.execute(makeRequest(group.getId()), accessor);

        @SuppressWarnings("unchecked")
        List<String> itemIds = (List<String>) resp.getData().get("itemIds");

        // Children should be findable in home
        for (String childId : itemIds) {
            boolean found = false;
            for (HomePieceOfFurniture p : home.getFurniture()) {
                if (p.getId().equals(childId)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Child " + childId + " should be in home furniture list");
        }
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
        assertTrue(handler.getDescription().contains("ungroup"));

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }

    @Test
    void testRoundTrip() {
        // Group → ungroup → items should be same as original
        HomePieceOfFurniture a = addFurniture("A", 100, 200);
        HomePieceOfFurniture b = addFurniture("B", 150, 250);
        String aId = a.getId();
        String bId = b.getId();

        // Group
        GroupFurnitureHandler groupHandler = new GroupFurnitureHandler();
        Map<String, Object> groupParams = new LinkedHashMap<>();
        groupParams.put("ids", Arrays.asList(aId, bId));
        groupParams.put("name", "Test");
        Response groupResp = groupHandler.execute(new Request("group_furniture", groupParams), accessor);
        assertTrue(groupResp.isOk());
        String groupId = (String) groupResp.getData().get("id");

        // Ungroup
        Response ungroupResp = handler.execute(makeRequest(groupId), accessor);
        assertTrue(ungroupResp.isOk());

        // Verify items are back
        assertEquals(2, home.getFurniture().size());
        @SuppressWarnings("unchecked")
        List<String> returnedIds = (List<String>) ungroupResp.getData().get("itemIds");
        assertTrue(returnedIds.contains(aId));
        assertTrue(returnedIds.contains(bId));
    }

    private HomePieceOfFurniture addFurniture(String name, float x, float y) {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(name, null, null, 50f, 50f, 50f, true, false));
        piece.setX(x);
        piece.setY(y);
        home.addPieceOfFurniture(piece);
        return piece;
    }

    private HomeFurnitureGroup createGroup(String groupName, String... itemNames) {
        List<HomePieceOfFurniture> items = new java.util.ArrayList<>();
        for (String name : itemNames) {
            HomePieceOfFurniture piece = new HomePieceOfFurniture(
                    new CatalogPieceOfFurniture(name, null, null, 50f, 50f, 50f, true, false));
            piece.setX(100);
            piece.setY(100);
            items.add(piece);
        }
        HomeFurnitureGroup group = new HomeFurnitureGroup(items, groupName);
        home.addPieceOfFurniture(group);
        return group;
    }

    private Request makeRequest(String id) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        return new Request("ungroup_furniture", params);
    }
}
