package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class CreateRoomPolygonHandlerTest {

    private CreateRoomPolygonHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new CreateRoomPolygonHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testCreateTriangle() {
        Response resp = exec(params(points(pt(0, 0), pt(300, 0), pt(150, 200))));

        assertTrue(resp.isOk());
        assertEquals(1, home.getRooms().size());

        Room room = home.getRooms().get(0);
        float[][] pts = room.getPoints();
        assertEquals(3, pts.length);
        assertEquals(0f, pts[0][0], 0.01f);
        assertEquals(300f, pts[1][0], 0.01f);
        assertEquals(200f, pts[2][1], 0.01f);

        assertTrue(((Number) resp.getData().get("area")).doubleValue() > 0);
    }

    @Test
    void testCreateRectangle() {
        Response resp = exec(params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400))));

        assertTrue(resp.isOk());
        assertEquals(1, home.getRooms().size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> respPoints = (List<Map<String, Object>>) resp.getData().get("points");
        assertEquals(4, respPoints.size());
        assertEquals(500.0, ((Number) respPoints.get(1).get("x")).doubleValue(), 0.01);
        assertEquals(400.0, ((Number) respPoints.get(2).get("y")).doubleValue(), 0.01);
    }

    @Test
    void testSetName() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("name", "Living Room");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        assertEquals("Living Room", home.getRooms().get(0).getName());
        assertEquals("Living Room", resp.getData().get("name"));
    }

    @Test
    void testFloorVisible() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("floorVisible", false);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        assertFalse(home.getRooms().get(0).isFloorVisible());
        assertEquals(false, resp.getData().get("floorVisible"));
    }

    @Test
    void testCeilingVisible() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("ceilingVisible", false);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        assertFalse(home.getRooms().get(0).isCeilingVisible());
        assertEquals(false, resp.getData().get("ceilingVisible"));
    }

    @Test
    void testSetFloorColor() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("floorColor", "#CCBB99");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        assertEquals(0xCCBB99, (int) home.getRooms().get(0).getFloorColor());
        assertEquals("#CCBB99", resp.getData().get("floorColor"));
    }

    @Test
    void testSetCeilingColor() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("ceilingColor", "#FFFFFF");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        assertEquals(0xFFFFFF, (int) home.getRooms().get(0).getCeilingColor());
        assertEquals("#FFFFFF", resp.getData().get("ceilingColor"));
    }

    @Test
    void testAreaVisible() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("areaVisible", true);

        Response resp = exec(p);

        assertTrue(resp.isOk());
        assertTrue(home.getRooms().get(0).isAreaVisible());
        assertEquals(true, resp.getData().get("areaVisible"));
    }

    @Test
    void testMissingPoints() {
        Response resp = exec(new LinkedHashMap<>());

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("points"));
    }

    @Test
    void testEmptyPoints() {
        Response resp = exec(params(new ArrayList<>()));

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("at least 3"));
    }

    @Test
    void testTwoPoints() {
        Response resp = exec(params(points(pt(0, 0), pt(100, 0))));

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("at least 3"));
    }

    @Test
    void testPointMissingCoordinate() {
        Map<String, Object> badPt = new LinkedHashMap<>();
        badPt.put("x", 100.0);
        // missing y
        List<Object> pts = new ArrayList<>();
        pts.add(badPt);
        pts.add(pt(200, 0));
        pts.add(pt(100, 200));

        Response resp = exec(params(pts));

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("index 0"));
    }

    @Test
    void testPointNotObject() {
        List<Object> pts = new ArrayList<>();
        pts.add("not a point");
        pts.add(pt(200, 0));
        pts.add(pt(100, 200));

        Response resp = exec(params(pts));

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("index 0"));
    }

    @Test
    void testInvalidFloorColor() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("floorColor", "red");

        Response resp = exec(p);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("floorColor"));
    }

    @Test
    void testInvalidCeilingColor() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("ceilingColor", "#GG0000");

        Response resp = exec(p);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("ceilingColor"));
    }

    @Test
    void testResponseContainsAllFields() {
        Map<String, Object> p = params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400)));
        p.put("name", "Test Room");

        Response resp = exec(p);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertNotNull(data.get("id"));
        assertTrue(data.get("id") instanceof String, "id should be a String");
        assertEquals("Test Room", data.get("name"));
        assertNotNull(data.get("area"));
        assertNotNull(data.get("areaVisible"));
        assertNotNull(data.get("floorVisible"));
        assertNotNull(data.get("ceilingVisible"));
        assertTrue(data.containsKey("floorColor"));
        assertTrue(data.containsKey("ceilingColor"));
        assertNotNull(data.get("xCenter"));
        assertNotNull(data.get("yCenter"));
        assertNotNull(data.get("points"));
    }

    @Test
    void testDefaultVisibility() {
        Response resp = exec(params(points(pt(0, 0), pt(500, 0), pt(500, 400), pt(0, 400))));

        assertTrue(resp.isOk());
        Room room = home.getRooms().get(0);
        assertTrue(room.isFloorVisible());
        assertTrue(room.isCeilingVisible());
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("points"));
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("floorVisible"));
        assertTrue(props.containsKey("ceilingVisible"));
        assertTrue(props.containsKey("floorColor"));
        assertTrue(props.containsKey("ceilingColor"));
        assertTrue(props.containsKey("areaVisible"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("points"));
        assertEquals(1, required.size());
    }

    // --- helpers ---

    private Response exec(Map<String, Object> params) {
        return handler.execute(new Request("create_room_polygon", params), accessor);
    }

    private static Map<String, Object> pt(double x, double y) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("x", x);
        p.put("y", y);
        return p;
    }

    @SafeVarargs
    private static List<Object> points(Map<String, Object>... pts) {
        return new ArrayList<>(Arrays.asList(pts));
    }

    private static Map<String, Object> params(List<Object> pointsList) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("points", pointsList);
        return p;
    }
}
