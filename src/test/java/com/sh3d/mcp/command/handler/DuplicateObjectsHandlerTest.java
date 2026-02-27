package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
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

class DuplicateObjectsHandlerTest {

    private DuplicateObjectsHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DuplicateObjectsHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testDuplicateSingleFurniture() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 200);

        Response resp = handler.execute(makeRequest(Arrays.asList(piece.getId())), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(1, data.get("count"));
        assertEquals(2, home.getFurniture().size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duplicated = (List<Map<String, Object>>) data.get("duplicated");
        assertEquals(1, duplicated.size());
        assertEquals(piece.getId(), duplicated.get(0).get("originalId"));
        assertNotEquals(piece.getId(), duplicated.get(0).get("duplicateId"));
        assertEquals("furniture", duplicated.get(0).get("type"));
        assertEquals("Table", duplicated.get(0).get("name"));
    }

    @Test
    void testDefaultOffset() {
        HomePieceOfFurniture piece = addFurniture("Chair", 100, 200);

        handler.execute(makeRequest(Arrays.asList(piece.getId())), accessor);

        HomePieceOfFurniture copy = home.getFurniture().get(1);
        assertEquals(120f, copy.getX(), 0.01f);  // 100 + 20 default offset
        assertEquals(220f, copy.getY(), 0.01f);  // 200 + 20 default offset
    }

    @Test
    void testCustomOffset() {
        HomePieceOfFurniture piece = addFurniture("Lamp", 50, 50);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", Arrays.asList(piece.getId()));
        params.put("offsetX", 100);
        params.put("offsetY", -50);

        Response resp = handler.execute(new Request("duplicate_objects", params), accessor);

        assertTrue(resp.isOk());
        HomePieceOfFurniture copy = home.getFurniture().get(1);
        assertEquals(150f, copy.getX(), 0.01f);
        assertEquals(0f, copy.getY(), 0.01f);
    }

    @Test
    void testZeroOffset() {
        HomePieceOfFurniture piece = addFurniture("Sofa", 300, 400);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", Arrays.asList(piece.getId()));
        params.put("offsetX", 0);
        params.put("offsetY", 0);

        handler.execute(new Request("duplicate_objects", params), accessor);

        HomePieceOfFurniture copy = home.getFurniture().get(1);
        assertEquals(300f, copy.getX(), 0.01f);
        assertEquals(400f, copy.getY(), 0.01f);
    }

    @Test
    void testDuplicateMultipleFurniture() {
        HomePieceOfFurniture a = addFurniture("A", 0, 0);
        HomePieceOfFurniture b = addFurniture("B", 100, 100);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(a.getId(), b.getId())), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, resp.getData().get("count"));
        assertEquals(4, home.getFurniture().size());
    }

    @Test
    void testDuplicateWall() {
        Wall wall = new Wall(0, 0, 500, 0, 10);
        home.addWall(wall);

        Response resp = handler.execute(makeRequest(Arrays.asList(wall.getId())), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, home.getWalls().size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duplicated = (List<Map<String, Object>>) resp.getData().get("duplicated");
        assertEquals("wall", duplicated.get(0).get("type"));
    }

    @Test
    void testDuplicateConnectedWalls() {
        Wall w1 = new Wall(0, 0, 500, 0, 10);
        Wall w2 = new Wall(500, 0, 500, 400, 10);
        w1.setWallAtEnd(w2);
        w2.setWallAtStart(w1);
        home.addWall(w1);
        home.addWall(w2);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(w1.getId(), w2.getId())), accessor);

        assertTrue(resp.isOk());
        assertEquals(4, home.getWalls().size());

        // Find duplicated walls (the last two added)
        Wall[] allWalls = home.getWalls().toArray(new Wall[0]);
        // Original walls still connected
        assertNotNull(w1.getWallAtEnd());
        assertNotNull(w2.getWallAtStart());
    }

    @Test
    void testDuplicateRoom() {
        Room room = new Room(new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        room.setName("Kitchen");
        home.addRoom(room);

        Response resp = handler.execute(makeRequest(Arrays.asList(room.getId())), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, home.getRooms().size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duplicated = (List<Map<String, Object>>) resp.getData().get("duplicated");
        assertEquals("room", duplicated.get(0).get("type"));
        assertEquals("Kitchen", duplicated.get(0).get("name"));
    }

    @Test
    void testDuplicateLabel() {
        Label label = new Label("Hello", 100, 200);
        home.addLabel(label);

        Response resp = handler.execute(makeRequest(Arrays.asList(label.getId())), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, home.getLabels().size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> duplicated = (List<Map<String, Object>>) resp.getData().get("duplicated");
        assertEquals("label", duplicated.get(0).get("type"));
        assertEquals("Hello", duplicated.get(0).get("name"));
    }

    @Test
    void testDuplicateMixedTypes() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 100);
        Wall wall = new Wall(0, 0, 500, 0, 10);
        home.addWall(wall);
        Room room = new Room(new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        home.addRoom(room);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(piece.getId(), wall.getId(), room.getId())), accessor);

        assertTrue(resp.isOk());
        assertEquals(3, resp.getData().get("count"));
        assertEquals(2, home.getFurniture().size());
        assertEquals(2, home.getWalls().size());
        assertEquals(2, home.getRooms().size());
    }

    @Test
    void testDuplicatePreservesProperties() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 200);
        piece.setElevation(50);
        piece.setAngle((float) Math.toRadians(45));
        piece.setColor(0xFF0000);

        handler.execute(makeRequest(Arrays.asList(piece.getId())), accessor);

        HomePieceOfFurniture copy = home.getFurniture().get(1);
        assertEquals(50f, copy.getElevation(), 0.01f);
        assertEquals((float) Math.toRadians(45), copy.getAngle(), 0.01f);
        assertEquals(Integer.valueOf(0xFF0000), copy.getColor());
    }

    @Test
    void testNewIdsGenerated() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 200);

        handler.execute(makeRequest(Arrays.asList(piece.getId())), accessor);

        HomePieceOfFurniture copy = home.getFurniture().get(1);
        assertNotEquals(piece.getId(), copy.getId());
    }

    @Test
    void testErrorMissingIds() {
        Map<String, Object> params = new LinkedHashMap<>();
        Response resp = handler.execute(new Request("duplicate_objects", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("ids"));
    }

    @Test
    void testErrorEmptyIds() {
        Response resp = handler.execute(makeRequest(Arrays.asList()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("empty"));
    }

    @Test
    void testErrorIdsNotArray() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", "not-an-array");
        Response resp = handler.execute(new Request("duplicate_objects", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("array"));
    }

    @Test
    void testErrorObjectNotFound() {
        Response resp = handler.execute(makeRequest(Arrays.asList("nonexistent-id")), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertTrue(resp.getMessage().contains("nonexistent-id"));
    }

    @Test
    void testErrorPartialNotFound() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 200);

        Response resp = handler.execute(
                makeRequest(Arrays.asList(piece.getId(), "bad-id")), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("bad-id"));
        // Original scene unchanged
        assertEquals(1, home.getFurniture().size());
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("ids"));
        assertTrue(props.containsKey("offsetX"));
        assertTrue(props.containsKey("offsetY"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("ids"));
    }

    // --- Helpers ---

    private HomePieceOfFurniture addFurniture(String name, float x, float y) {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(
                        name, null, null, 50f, 50f, 50f, true, false));
        piece.setX(x);
        piece.setY(y);
        home.addPieceOfFurniture(piece);
        return piece;
    }

    private Request makeRequest(List<String> ids) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ids", ids);
        return new Request("duplicate_objects", params);
    }
}
