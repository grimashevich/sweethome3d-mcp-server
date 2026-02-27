package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
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

class ConnectWallsHandlerTest {

    private ConnectWallsHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ConnectWallsHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testConnectEndToStart() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(w1.getId(), w2.getId(), "end", "start"), accessor);

        assertTrue(resp.isOk());
        assertSame(w2, w1.getWallAtEnd());
        assertSame(w1, w2.getWallAtStart());
        assertEquals("end", resp.getData().get("wall1End"));
        assertEquals("start", resp.getData().get("wall2End"));
    }

    @Test
    void testConnectStartToEnd() {
        Wall w1 = addWall(500, 0, 500, 300);
        Wall w2 = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(w1.getId(), w2.getId(), "start", "end"), accessor);

        assertTrue(resp.isOk());
        assertSame(w2, w1.getWallAtStart());
        assertSame(w1, w2.getWallAtEnd());
    }

    @Test
    void testConnectStartToStart() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(0, 0, 0, 300);

        Response resp = handler.execute(makeRequest(w1.getId(), w2.getId(), "start", "start"), accessor);

        assertTrue(resp.isOk());
        assertSame(w2, w1.getWallAtStart());
        assertSame(w1, w2.getWallAtStart());
    }

    @Test
    void testConnectEndToEnd() {
        Wall w1 = addWall(0, 0, 500, 300);
        Wall w2 = addWall(800, 600, 500, 300);

        Response resp = handler.execute(makeRequest(w1.getId(), w2.getId(), "end", "end"), accessor);

        assertTrue(resp.isOk());
        assertSame(w2, w1.getWallAtEnd());
        assertSame(w1, w2.getWallAtEnd());
    }

    @Test
    void testAutoDetectEndToStart() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequestAutoDetect(w1.getId(), w2.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("end", resp.getData().get("wall1End"));
        assertEquals("start", resp.getData().get("wall2End"));
        assertSame(w2, w1.getWallAtEnd());
        assertSame(w1, w2.getWallAtStart());
    }

    @Test
    void testAutoDetectStartToStart() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(0, 0, 0, 300);

        Response resp = handler.execute(makeRequestAutoDetect(w1.getId(), w2.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("start", resp.getData().get("wall1End"));
        assertEquals("start", resp.getData().get("wall2End"));
        assertSame(w2, w1.getWallAtStart());
        assertSame(w1, w2.getWallAtStart());
    }

    @Test
    void testAutoDetectEndToEnd() {
        Wall w1 = addWall(0, 0, 500, 300);
        Wall w2 = addWall(800, 600, 500, 300);

        Response resp = handler.execute(makeRequestAutoDetect(w1.getId(), w2.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("end", resp.getData().get("wall1End"));
        assertEquals("end", resp.getData().get("wall2End"));
    }

    @Test
    void testAutoDetectStartToEnd() {
        Wall w1 = addWall(500, 0, 500, 300);
        Wall w2 = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequestAutoDetect(w1.getId(), w2.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("start", resp.getData().get("wall1End"));
        assertEquals("end", resp.getData().get("wall2End"));
    }

    @Test
    void testWall1IdNotFound() {
        Wall w1 = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequestAutoDetect("nonexistent", w1.getId()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall1Id"));
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testWall2IdNotFound() {
        Wall w1 = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequestAutoDetect(w1.getId(), "nonexistent"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall2Id"));
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testSameWallIdError() {
        Wall w1 = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequestAutoDetect(w1.getId(), w1.getId()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("itself"));
    }

    @Test
    void testInvalidWall1End() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(w1.getId(), w2.getId(), "middle", "start"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall1End"));
        assertTrue(resp.getMessage().contains("middle"));
    }

    @Test
    void testInvalidWall2End() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(w1.getId(), w2.getId(), "end", "top"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall2End"));
        assertTrue(resp.getMessage().contains("top"));
    }

    @Test
    void testBidirectionalConnection() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        handler.execute(makeRequest(w1.getId(), w2.getId(), "end", "start"), accessor);

        assertSame(w2, w1.getWallAtEnd());
        assertSame(w1, w2.getWallAtStart());
        assertNull(w1.getWallAtStart());
        assertNull(w2.getWallAtEnd());
    }

    @Test
    void testResponseContainsMessage() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(w1.getId(), w2.getId(), "end", "start"), accessor);

        assertTrue(resp.isOk());
        String message = (String) resp.getData().get("message");
        assertNotNull(message);
        assertTrue(message.contains("connected"));
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
        assertTrue(props.containsKey("wall1Id"));
        assertTrue(props.containsKey("wall2Id"));
        assertTrue(props.containsKey("wall1End"));
        assertTrue(props.containsKey("wall2End"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("wall1Id"));
        assertTrue(required.contains("wall2Id"));
        assertFalse(required.contains("wall1End"));
        assertFalse(required.contains("wall2End"));
    }

    // --- Helpers ---

    private Wall addWall(float xStart, float yStart, float xEnd, float yEnd) {
        return TestFixtures.addWall(home, xStart, yStart, xEnd, yEnd);
    }

    private Request makeRequest(String wall1Id, String wall2Id, String wall1End, String wall2End) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("wall1Id", wall1Id);
        params.put("wall2Id", wall2Id);
        if (wall1End != null) params.put("wall1End", wall1End);
        if (wall2End != null) params.put("wall2End", wall2End);
        return new Request("connect_walls", params);
    }

    private Request makeRequestAutoDetect(String wall1Id, String wall2Id) {
        return makeRequest(wall1Id, wall2Id, null, null);
    }
}
