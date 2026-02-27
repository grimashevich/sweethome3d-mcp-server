package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
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

class DeleteRoomHandlerTest {

    private DeleteRoomHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteRoomHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testDeleteSingleRoom() {
        Room room = addRoom("Kitchen", new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});

        Response resp = handler.execute(makeRequest(room.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getRooms().size());
    }

    @Test
    void testResponseContainsDeletedInfo() {
        Room room = addRoom("Living Room", new float[][]{{0, 0}, {600, 0}, {600, 500}, {0, 500}});

        Response resp = handler.execute(makeRequest(room.getId()), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals("Living Room", data.get("name"));
        assertTrue(((Number) data.get("area")).floatValue() > 0);
        assertTrue(((Number) data.get("xCenter")).floatValue() > 0);
        assertTrue(((Number) data.get("yCenter")).floatValue() > 0);
        assertNotNull(data.get("points"));
        assertTrue(((String) data.get("message")).contains("deleted"));
        assertTrue(((String) data.get("message")).contains("Living Room"));
    }

    @Test
    void testResponseContainsPoints() {
        Room room = addRoom("Hall", new float[][]{{100, 200}, {300, 200}, {300, 400}});

        Response resp = handler.execute(makeRequest(room.getId()), accessor);

        assertTrue(resp.isOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) resp.getData().get("points");
        assertEquals(3, points.size());
        assertEquals(100f, ((Number) points.get(0).get("x")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) points.get(0).get("y")).floatValue(), 0.01f);
    }

    @Test
    void testDeleteFromMultiple() {
        addRoom("Room A", new float[][]{{0, 0}, {100, 0}, {100, 100}, {0, 100}});
        Room roomB = addRoom("Room B", new float[][]{{200, 0}, {300, 0}, {300, 100}, {200, 100}});
        addRoom("Room C", new float[][]{{400, 0}, {500, 0}, {500, 100}, {400, 100}});

        Response resp = handler.execute(makeRequest(roomB.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("Room B", resp.getData().get("name"));
        assertEquals(2, home.getRooms().size());
    }

    @Test
    void testDeleteFirst() {
        Room first = addRoom("First", new float[][]{{0, 0}, {100, 0}, {100, 100}, {0, 100}});
        addRoom("Second", new float[][]{{200, 0}, {300, 0}, {300, 100}, {200, 100}});

        Response resp = handler.execute(makeRequest(first.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("First", resp.getData().get("name"));
        assertEquals(1, home.getRooms().size());
        assertEquals("Second", home.getRooms().get(0).getName());
    }

    @Test
    void testDeleteLast() {
        addRoom("First", new float[][]{{0, 0}, {100, 0}, {100, 100}, {0, 100}});
        Room second = addRoom("Second", new float[][]{{200, 0}, {300, 0}, {300, 100}, {200, 100}});

        Response resp = handler.execute(makeRequest(second.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("Second", resp.getData().get("name"));
        assertEquals(1, home.getRooms().size());
        assertEquals("First", home.getRooms().get(0).getName());
    }

    @Test
    void testUnnamedRoom() {
        Room room = addRoom(null, new float[][]{{0, 0}, {100, 0}, {100, 100}, {0, 100}});

        Response resp = handler.execute(makeRequest(room.getId()), accessor);

        assertTrue(resp.isOk());
        assertNull(resp.getData().get("name"));
        assertTrue(((String) resp.getData().get("message")).contains("(unnamed)"));
    }

    @Test
    void testIdNotFound() {
        addRoom("Only", new float[][]{{0, 0}, {100, 0}, {100, 100}, {0, 100}});

        Response resp = handler.execute(makeRequest("nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getRooms().size());
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeRequest("any-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
        assertTrue(handler.getDescription().contains("room"));

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

    private Room addRoom(String name, float[][] points) {
        Room room = new Room(points);
        if (name != null) {
            room.setName(name);
        }
        home.addRoom(room);
        return room;
    }

    private Request makeRequest(String id) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        return new Request("delete_room", params);
    }
}
