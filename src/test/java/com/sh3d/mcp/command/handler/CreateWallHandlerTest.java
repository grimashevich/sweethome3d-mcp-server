package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class CreateWallHandlerTest {

    private CreateWallHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new CreateWallHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testCreateWallAddsOneWall() {
        Request req = makeRequest(0, 0, 500, 0);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(1, home.getWalls().size());
    }

    @Test
    void testResponseContainsIdAndCoordinates() {
        Request req = makeRequest(100, 200, 500, 200);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertNotNull(data.get("id"));
        assertInstanceOf(String.class, data.get("id"));
        assertEquals(100f, ((Number) data.get("xStart")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yStart")).floatValue(), 0.01f);
        assertEquals(500f, ((Number) data.get("xEnd")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yEnd")).floatValue(), 0.01f);
    }

    @Test
    void testResponseContainsLength() {
        Request req = makeRequest(0, 0, 300, 400);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        // 3-4-5 triangle: length = 500
        assertEquals(500f, ((Number) resp.getData().get("length")).floatValue(), 0.01f);
    }

    @Test
    void testDefaultThickness() {
        Request req = makeRequest(0, 0, 500, 0);
        handler.execute(req, accessor);

        Wall wall = home.getWalls().iterator().next();
        assertEquals(10.0f, wall.getThickness(), 0.01f);
    }

    @Test
    void testCustomThickness() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("xStart", 0.0);
        params.put("yStart", 0.0);
        params.put("xEnd", 500.0);
        params.put("yEnd", 0.0);
        params.put("thickness", 20.0);

        Request req = new Request("create_wall", params);
        handler.execute(req, accessor);

        Wall wall = home.getWalls().iterator().next();
        assertEquals(20.0f, wall.getThickness(), 0.01f);
    }

    @Test
    void testCustomHeight() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("xStart", 0.0);
        params.put("yStart", 0.0);
        params.put("xEnd", 500.0);
        params.put("yEnd", 0.0);
        params.put("height", 300.0);

        Request req = new Request("create_wall", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        Wall wall = home.getWalls().iterator().next();
        assertEquals(300.0f, wall.getHeight(), 0.01f);
        assertEquals(300f, ((Number) resp.getData().get("height")).floatValue(), 0.01f);
    }

    @Test
    void testDefaultHeightUsesHomeDefault() {
        Home homeWithHeight = new Home(270f);
        HomeAccessor accessorWithHeight = createAccessor(homeWithHeight);

        Request req = makeRequest(0, 0, 500, 0);
        handler.execute(req, accessorWithHeight);

        Wall wall = homeWithHeight.getWalls().iterator().next();
        assertEquals(270.0f, wall.getHeight(), 0.01f);
    }

    @Test
    void testDefaultHeightFallback250() {
        // Home wallHeight = 0 (default), height param not set
        Request req = makeRequest(0, 0, 500, 0);
        handler.execute(req, accessor);

        Wall wall = home.getWalls().iterator().next();
        assertEquals(250.0f, wall.getHeight(), 0.01f);
    }

    @Test
    void testZeroLengthWallReturnsError() {
        Request req = makeRequest(100, 200, 100, 200);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("zero length"));
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testNegativeThicknessReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("xStart", 0.0);
        params.put("yStart", 0.0);
        params.put("xEnd", 500.0);
        params.put("yEnd", 0.0);
        params.put("thickness", -5.0);

        Request req = new Request("create_wall", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("thickness"));
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testMissingRequiredParamThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("xStart", 0.0);
        params.put("yStart", 0.0);
        // xEnd, yEnd missing

        Request req = new Request("create_wall", params);
        assertThrows(IllegalArgumentException.class, () -> handler.execute(req, accessor));
    }

    @Test
    void testDiagonalWall() {
        Request req = makeRequest(0, 0, 300, 400);
        handler.execute(req, accessor);

        List<Wall> walls = new ArrayList<>(home.getWalls());
        assertEquals(1, walls.size());

        Wall wall = walls.get(0);
        assertEquals(0f, wall.getXStart(), 0.01f);
        assertEquals(0f, wall.getYStart(), 0.01f);
        assertEquals(300f, wall.getXEnd(), 0.01f);
        assertEquals(400f, wall.getYEnd(), 0.01f);
    }

    @Test
    void testSecondWallGetsUniqueId() {
        Response resp1 = handler.execute(makeRequest(0, 0, 500, 0), accessor);
        Response resp2 = handler.execute(makeRequest(500, 0, 500, 300), accessor);

        assertTrue(resp2.isOk());
        String id1 = (String) resp1.getData().get("id");
        String id2 = (String) resp2.getData().get("id");
        assertNotEquals(id1, id2);
        assertEquals(2, home.getWalls().size());
    }

    @Test
    void testWallCoordinatesWithNegativeValues() {
        Request req = makeRequest(-100, -200, 300, 400);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        Wall wall = home.getWalls().iterator().next();
        assertEquals(-100f, wall.getXStart(), 0.01f);
        assertEquals(-200f, wall.getYStart(), 0.01f);
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("xStart"));
        assertTrue(props.containsKey("yStart"));
        assertTrue(props.containsKey("xEnd"));
        assertTrue(props.containsKey("yEnd"));
        assertTrue(props.containsKey("thickness"));
        assertTrue(props.containsKey("height"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("xStart"));
        assertTrue(required.contains("yStart"));
        assertTrue(required.contains("xEnd"));
        assertTrue(required.contains("yEnd"));
        assertFalse(required.contains("thickness"));
        assertFalse(required.contains("height"));
    }

    private Request makeRequest(float xStart, float yStart, float xEnd, float yEnd) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("xStart", (double) xStart);
        params.put("yStart", (double) yStart);
        params.put("xEnd", (double) xEnd);
        params.put("yEnd", (double) yEnd);
        return new Request("create_wall", params);
    }
}
