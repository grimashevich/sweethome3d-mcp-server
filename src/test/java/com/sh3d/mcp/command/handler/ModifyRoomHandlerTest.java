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

class ModifyRoomHandlerTest {

    private ModifyRoomHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ModifyRoomHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Name ---

    @Test
    void testSetName() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "name", "Kitchen"), accessor);

        assertTrue(resp.isOk());
        assertEquals("Kitchen", room.getName());
        assertEquals("Kitchen", resp.getData().get("name"));
    }

    @Test
    void testSetNameToNull() {
        Room room = addRoom();
        room.setName("Old Name");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", room.getId());
        params.put("name", null);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isOk());
        assertNull(room.getName());
    }

    // --- Visibility ---

    @Test
    void testSetFloorVisible() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "floorVisible", false), accessor);

        assertTrue(resp.isOk());
        assertFalse(room.isFloorVisible());
        assertEquals(false, resp.getData().get("floorVisible"));
    }

    @Test
    void testSetCeilingVisible() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "ceilingVisible", false), accessor);

        assertTrue(resp.isOk());
        assertFalse(room.isCeilingVisible());
        assertEquals(false, resp.getData().get("ceilingVisible"));
    }

    @Test
    void testSetAreaVisible() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "areaVisible", true), accessor);

        assertTrue(resp.isOk());
        assertTrue(room.isAreaVisible());
        assertEquals(true, resp.getData().get("areaVisible"));
    }

    // --- Colors ---

    @Test
    void testSetFloorColor() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "floorColor", "#CCBB99"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xCCBB99, (int) room.getFloorColor());
        assertEquals("#CCBB99", resp.getData().get("floorColor"));
    }

    @Test
    void testSetCeilingColor() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "ceilingColor", "#FFFFFF"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFFFFFF, (int) room.getCeilingColor());
        assertEquals("#FFFFFF", resp.getData().get("ceilingColor"));
    }

    @Test
    void testClearFloorColor() {
        Room room = addRoom();
        room.setFloorColor(0xFF0000);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", room.getId());
        params.put("floorColor", null);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isOk());
        assertNull(room.getFloorColor());
        assertNull(resp.getData().get("floorColor"));
    }

    @Test
    void testClearCeilingColor() {
        Room room = addRoom();
        room.setCeilingColor(0x00FF00);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", room.getId());
        params.put("ceilingColor", null);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isOk());
        assertNull(room.getCeilingColor());
        assertNull(resp.getData().get("ceilingColor"));
    }

    @Test
    void testInvalidFloorColorFormat() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "floorColor", "red"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("floorColor"));
    }

    @Test
    void testInvalidCeilingColorFormat() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "ceilingColor", "#GGG"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("ceilingColor"));
    }

    // --- Shininess ---

    @Test
    void testSetFloorShininess() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "floorShininess", 0.5), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.5f, room.getFloorShininess(), 0.01f);
        assertEquals(0.5, ((Number) resp.getData().get("floorShininess")).doubleValue(), 0.01);
    }

    @Test
    void testSetCeilingShininess() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "ceilingShininess", 0.8), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.8f, room.getCeilingShininess(), 0.01f);
        assertEquals(0.8, ((Number) resp.getData().get("ceilingShininess")).doubleValue(), 0.01);
    }

    @Test
    void testFloorShininessOutOfRange() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "floorShininess", 1.5), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("floorShininess"));
        assertTrue(resp.getMessage().contains("0.0"));
    }

    @Test
    void testCeilingShininessNegative() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "ceilingShininess", -0.1), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("ceilingShininess"));
    }

    // --- ID validation ---

    @Test
    void testIdNotFound() {
        addRoom();

        Response resp = handler.execute(makeRequest("nonexistent-id", "name", "Test"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeRequest("any-id", "name", "Test"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testNoModifiableProperties() {
        Room room = addRoom();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", room.getId());
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    // --- Partial updates ---

    @Test
    void testPartialUpdatePreservesOtherProperties() {
        Room room = addRoom();
        room.setFloorColor(0xFF0000);
        room.setName("Original");

        Response resp = handler.execute(makeRequest(room.getId(), "ceilingShininess", 0.7), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFF0000, (int) room.getFloorColor());
        assertEquals("Original", room.getName());
        assertEquals(0.7f, room.getCeilingShininess(), 0.01f);
    }

    @Test
    void testMultiplePropertiesAtOnce() {
        Room room = addRoom();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", room.getId());
        params.put("name", "Living Room");
        params.put("floorColor", "#CCBB99");
        params.put("ceilingColor", "#FFFFFF");
        params.put("floorShininess", 0.3);
        params.put("areaVisible", true);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Living Room", room.getName());
        assertEquals(0xCCBB99, (int) room.getFloorColor());
        assertEquals(0xFFFFFF, (int) room.getCeilingColor());
        assertEquals(0.3f, room.getFloorShininess(), 0.01f);
        assertTrue(room.isAreaVisible());
    }

    // --- Response format ---

    @Test
    void testResponseContainsAllFields() {
        Room room = addRoom();

        Response resp = handler.execute(makeRequest(room.getId(), "name", "Test Room"), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertNotNull(data.get("id"));
        assertTrue(data.get("id") instanceof String, "id should be a String");
        assertEquals("Test Room", data.get("name"));
        assertNotNull(data.get("area"));
        assertTrue(data.containsKey("areaVisible"));
        assertTrue(data.containsKey("floorVisible"));
        assertTrue(data.containsKey("ceilingVisible"));
        assertTrue(data.containsKey("floorColor"));
        assertTrue(data.containsKey("ceilingColor"));
        assertNotNull(data.get("floorShininess"));
        assertNotNull(data.get("ceilingShininess"));
        assertNotNull(data.get("xCenter"));
        assertNotNull(data.get("yCenter"));
        assertNotNull(data.get("points"));

        @SuppressWarnings("unchecked")
        List<Object> points = (List<Object>) data.get("points");
        assertEquals(4, points.size());
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

        @SuppressWarnings("unchecked")
        Map<String, Object> idProp = (Map<String, Object>) props.get("id");
        assertEquals("string", idProp.get("type"));

        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("floorVisible"));
        assertTrue(props.containsKey("ceilingVisible"));
        assertTrue(props.containsKey("areaVisible"));
        assertTrue(props.containsKey("floorColor"));
        assertTrue(props.containsKey("ceilingColor"));
        assertTrue(props.containsKey("floorShininess"));
        assertTrue(props.containsKey("ceilingShininess"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
        assertEquals(1, required.size());
    }

    // --- Helpers ---

    private Room addRoom() {
        float[][] polygon = {
                {0, 0}, {500, 0}, {500, 400}, {0, 400}
        };
        Room room = new Room(polygon);
        home.addRoom(room);
        return room;
    }

    private Request makeRequest(String id, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new Request("modify_room", params);
    }
}
