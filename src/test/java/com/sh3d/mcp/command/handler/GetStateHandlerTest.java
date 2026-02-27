package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.util.SceneBoundsCalculator;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetStateHandlerTest {

    private GetStateHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new GetStateHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Empty scene ---

    @Test
    @SuppressWarnings("unchecked")
    void testEmptyScene() {
        Response resp = execute();

        assertTrue(resp.isOk());
        assertEquals(0, resp.getData().get("wallCount"));
        assertEquals(0, resp.getData().get("furnitureCount"));
        assertEquals(0, resp.getData().get("roomCount"));
        assertEquals(0, resp.getData().get("labelCount"));
        assertEquals(0, resp.getData().get("dimensionLineCount"));
        assertEquals(0, resp.getData().get("levelCount"));
        assertNull(resp.getData().get("boundingBox"));

        assertTrue(((List<Object>) resp.getData().get("walls")).isEmpty());
        assertTrue(((List<Object>) resp.getData().get("furniture")).isEmpty());
        assertTrue(((List<Object>) resp.getData().get("rooms")).isEmpty());
        assertTrue(((List<Object>) resp.getData().get("labels")).isEmpty());
        assertTrue(((List<Object>) resp.getData().get("dimensionLines")).isEmpty());
        assertTrue(((List<Object>) resp.getData().get("levels")).isEmpty());

        // Camera is always present
        assertNotNull(resp.getData().get("camera"));
    }

    // --- Walls ---

    @Test
    @SuppressWarnings("unchecked")
    void testWallsWithCoordinatesAndId() {
        Wall w1 = new Wall(0, 0, 500, 0, 10);
        w1.setHeight(250f);
        home.addWall(w1);
        home.addWall(new Wall(500, 0, 500, 300, 12));

        Response resp = execute();
        assertEquals(2, resp.getData().get("wallCount"));

        List<Map<String, Object>> walls = (List<Map<String, Object>>) resp.getData().get("walls");
        assertEquals(2, walls.size());

        Map<String, Object> wall0 = walls.get(0);
        assertEquals(w1.getId(), wall0.get("id"));
        assertEquals(0.0, (double) wall0.get("xStart"), 0.01);
        assertEquals(0.0, (double) wall0.get("yStart"), 0.01);
        assertEquals(500.0, (double) wall0.get("xEnd"), 0.01);
        assertEquals(0.0, (double) wall0.get("yEnd"), 0.01);
        assertEquals(10.0, (double) wall0.get("thickness"), 0.01);
        assertEquals(250.0, (double) wall0.get("height"), 0.01);
        assertEquals(500.0, (double) wall0.get("length"), 0.01);

        Map<String, Object> wall1 = walls.get(1);
        assertNotNull(wall1.get("id"));
        assertTrue(wall1.get("id") instanceof String);
        assertEquals(12.0, (double) wall1.get("thickness"), 0.01);
    }

    // --- Furniture ---

    @Test
    @SuppressWarnings("unchecked")
    void testFurnitureWithEnhancedFields() {
        HomePieceOfFurniture piece = mock(HomePieceOfFurniture.class);
        when(piece.getName()).thenReturn("TestChair");
        when(piece.getCatalogId()).thenReturn("chair-001");
        when(piece.getX()).thenReturn(150.0f);
        when(piece.getY()).thenReturn(250.0f);
        when(piece.getElevation()).thenReturn(5.0f);
        when(piece.getAngle()).thenReturn((float) Math.toRadians(90));
        when(piece.getWidth()).thenReturn(50.0f);
        when(piece.getDepth()).thenReturn(50.0f);
        when(piece.getHeight()).thenReturn(80.0f);
        when(piece.isDoorOrWindow()).thenReturn(false);
        when(piece.isVisible()).thenReturn(true);

        home.addPieceOfFurniture(piece);

        Response resp = execute();
        assertEquals(1, resp.getData().get("furnitureCount"));

        List<Map<String, Object>> furniture = (List<Map<String, Object>>) resp.getData().get("furniture");
        Map<String, Object> item = furniture.get(0);

        // Mock returns null for final getId() — expected with Mockito + JDK 24
        assertTrue(item.containsKey("id"));
        assertEquals("TestChair", item.get("name"));
        assertEquals("chair-001", item.get("catalogId"));
        assertEquals(150.0, (double) item.get("x"), 0.01);
        assertEquals(250.0, (double) item.get("y"), 0.01);
        assertEquals(5.0, (double) item.get("elevation"), 0.01);
        assertEquals(90.0, (double) item.get("angle"), 0.01);
        assertEquals(50.0, (double) item.get("width"), 0.01);
        assertEquals(50.0, (double) item.get("depth"), 0.01);
        assertEquals(80.0, (double) item.get("height"), 0.01);
        assertEquals(false, item.get("isDoorOrWindow"));
        assertEquals(true, item.get("visible"));
        assertNull(item.get("level"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDoorOrWindowFlagged() {
        HomePieceOfFurniture door = mock(HomePieceOfFurniture.class);
        when(door.getName()).thenReturn("Front Door");
        when(door.isDoorOrWindow()).thenReturn(true);
        when(door.isVisible()).thenReturn(true);

        home.addPieceOfFurniture(door);

        Response resp = execute();
        List<Map<String, Object>> furniture = (List<Map<String, Object>>) resp.getData().get("furniture");
        assertEquals(true, furniture.get(0).get("isDoorOrWindow"));
    }

    // --- Rooms ---

    @Test
    @SuppressWarnings("unchecked")
    void testRoomsWithPolygon() {
        float[][] points = {{0, 0}, {500, 0}, {500, 400}, {0, 400}};
        Room room = new Room(points);
        room.setName("Living Room");
        room.setFloorVisible(true);
        room.setCeilingVisible(true);
        room.setFloorColor(0xCCBB99);

        home.addRoom(room);

        Response resp = execute();
        assertEquals(1, resp.getData().get("roomCount"));

        List<Map<String, Object>> rooms = (List<Map<String, Object>>) resp.getData().get("rooms");
        Map<String, Object> r = rooms.get(0);

        assertEquals(room.getId(), r.get("id"));
        assertEquals("Living Room", r.get("name"));
        assertTrue((double) r.get("area") > 0);
        assertEquals(true, r.get("floorVisible"));
        assertEquals(true, r.get("ceilingVisible"));
        assertEquals("#CCBB99", r.get("floorColor"));
        assertNull(r.get("ceilingColor"));

        List<Map<String, Object>> pts = (List<Map<String, Object>>) r.get("points");
        assertEquals(4, pts.size());
        assertEquals(0.0, (double) pts.get(0).get("x"), 0.01);
        assertEquals(0.0, (double) pts.get(0).get("y"), 0.01);
        assertEquals(500.0, (double) pts.get(1).get("x"), 0.01);
    }

    // --- Labels ---

    @Test
    @SuppressWarnings("unchecked")
    void testLabels() {
        Label label = new Label("Hello World", 100f, 200f);
        home.addLabel(label);

        Response resp = execute();
        assertEquals(1, resp.getData().get("labelCount"));

        List<Map<String, Object>> labels = (List<Map<String, Object>>) resp.getData().get("labels");
        Map<String, Object> l = labels.get(0);

        assertEquals(label.getId(), l.get("id"));
        assertEquals("Hello World", l.get("text"));
        assertEquals(100.0, (double) l.get("x"), 0.01);
        assertEquals(200.0, (double) l.get("y"), 0.01);
    }

    // --- Dimension lines ---

    @Test
    @SuppressWarnings("unchecked")
    void testDimensionLines() {
        DimensionLine dim = new DimensionLine(0, 0, 500, 0, 20);
        home.addDimensionLine(dim);

        Response resp = execute();
        assertEquals(1, resp.getData().get("dimensionLineCount"));

        List<Map<String, Object>> dims = (List<Map<String, Object>>) resp.getData().get("dimensionLines");
        Map<String, Object> d = dims.get(0);

        assertEquals(dim.getId(), d.get("id"));
        assertEquals(0.0, (double) d.get("xStart"), 0.01);
        assertEquals(0.0, (double) d.get("yStart"), 0.01);
        assertEquals(500.0, (double) d.get("xEnd"), 0.01);
        assertEquals(0.0, (double) d.get("yEnd"), 0.01);
        assertEquals(20.0, (double) d.get("offset"), 0.01);
        assertEquals(500.0, (double) d.get("length"), 0.01);
    }

    // --- Camera ---

    @Test
    @SuppressWarnings("unchecked")
    void testCameraInfo() {
        Response resp = execute();
        Map<String, Object> cam = (Map<String, Object>) resp.getData().get("camera");

        assertNotNull(cam);
        assertNotNull(cam.get("mode"));
        assertTrue(cam.containsKey("x"));
        assertTrue(cam.containsKey("y"));
        assertTrue(cam.containsKey("z"));
        assertTrue(cam.containsKey("yaw_degrees"));
        assertTrue(cam.containsKey("pitch_degrees"));
        assertTrue(cam.containsKey("fov_degrees"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCameraModeTop() {
        // Default camera is top
        home.setCamera(home.getTopCamera());
        Response resp = execute();
        Map<String, Object> cam = (Map<String, Object>) resp.getData().get("camera");
        assertEquals("top", cam.get("mode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCameraModeObserver() {
        home.setCamera(home.getObserverCamera());
        Response resp = execute();
        Map<String, Object> cam = (Map<String, Object>) resp.getData().get("camera");
        assertEquals("observer", cam.get("mode"));
    }

    // --- Bounding box ---

    @Test
    @SuppressWarnings("unchecked")
    void testBoundingBoxCalculation() {
        home.addWall(new Wall(100, 200, 500, 200, 10));
        home.addWall(new Wall(500, 200, 500, 600, 10));

        Response resp = execute();
        Map<String, Object> bb = (Map<String, Object>) resp.getData().get("boundingBox");
        assertNotNull(bb);
        // SceneBoundsCalculator uses wall.getPoints() (polygon with thickness),
        // so bounds extend slightly beyond the centerline coordinates
        assertTrue((double) bb.get("minX") <= 100.0, "minX should be <= 100");
        assertTrue((double) bb.get("minY") <= 200.0, "minY should be <= 200");
        assertTrue((double) bb.get("maxX") >= 500.0, "maxX should be >= 500");
        assertTrue((double) bb.get("maxY") >= 600.0, "maxY should be >= 600");
    }

    @Test
    void testBoundingBoxNullWhenNoContent() {
        Response resp = execute();
        assertNull(resp.getData().get("boundingBox"));
    }

    // --- Descriptor ---

    @Test
    void testDescriptionUpdated() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("walls"));
        assertTrue(desc.contains("furniture"));
        assertTrue(desc.contains("rooms"));
        assertTrue(desc.contains("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoParams() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.isEmpty());
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.isEmpty());
    }

    @Test
    void testAlwaysReturnsOk() {
        Response resp = execute();
        assertTrue(resp.isOk());
        assertFalse(resp.isError());
    }

    // --- Helper ---

    private Response execute() {
        return handler.execute(new Request("get_state", Collections.emptyMap()), accessor);
    }
}
