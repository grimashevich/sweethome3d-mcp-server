package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.SceneBoundsCalculator;
import com.sh3d.mcp.command.util.SceneBounds;

import com.sh3d.mcp.bridge.CommandException;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class SetCameraHandlerTest {

    private SetCameraHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new SetCameraHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Validation ---

    @Test
    void testMissingModeAndName() {
        Response resp = execute();
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mode"));
    }

    @Test
    void testInvalidMode() {
        Response resp = execute("mode", "panoramic");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mode"));
        assertTrue(resp.getMessage().contains("panoramic"));
    }

    @Test
    void testEmptyMode() {
        Response resp = execute("mode", "");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mode"));
    }

    // --- Functional ---

    @Test
    @SuppressWarnings("unchecked")
    void testSetTopCamera() {
        Response resp = execute("mode", "top");
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("top", data.get("mode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetObserverCamera() {
        Response resp = execute("mode", "observer");
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("observer", data.get("mode"));
    }

    @Test
    void testModeCaseInsensitive() {
        Response resp = execute("mode", "TOP");
        assertFalse(resp.isError());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testObserverWithPosition() {
        Response resp = execute("mode", "observer", "x", 250.0, "y", 200.0, "z", 170.0);
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(250.0f, ((Number) data.get("x")).floatValue(), 0.1f);
        assertEquals(200.0f, ((Number) data.get("y")).floatValue(), 0.1f);
        assertEquals(170.0f, ((Number) data.get("z")).floatValue(), 0.1f);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testObserverWithYawPitch() {
        Response resp = execute("mode", "observer", "yaw", 90.0, "pitch", -30.0);
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(90.0, ((Number) data.get("yaw_degrees")).doubleValue(), 0.5);
        assertEquals(-30.0, ((Number) data.get("pitch_degrees")).doubleValue(), 0.5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testResponseContainsCameraInfo() {
        Response resp = execute("mode", "observer");
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertTrue(data.containsKey("x"));
        assertTrue(data.containsKey("y"));
        assertTrue(data.containsKey("z"));
        assertTrue(data.containsKey("yaw_degrees"));
        assertTrue(data.containsKey("pitch_degrees"));
        assertTrue(data.containsKey("fov_degrees"));
    }

    // --- Descriptor ---

    @Test
    void testToolName() {
        assertNull(handler.getToolName());
    }

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("camera") || desc.contains("Camera"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoRequired() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.isEmpty(), "Neither mode nor name should be strictly required");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaProperties() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("mode"));
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("x"));
        assertTrue(props.containsKey("y"));
        assertTrue(props.containsKey("z"));
        assertTrue(props.containsKey("yaw"));
        assertTrue(props.containsKey("pitch"));
        assertTrue(props.containsKey("fov"));
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandDescriptor);
        assertTrue(handler instanceof CommandHandler);
    }

    // --- Restore stored camera ---

    @Test
    @SuppressWarnings("unchecked")
    void testRestoreStoredCamera() {
        // Store a camera first
        Camera stored = new Camera(100f, 200f, 170f,
                (float) Math.toRadians(45), (float) Math.toRadians(10),
                (float) Math.toRadians(60));
        stored.setName("Kitchen");
        List<Camera> cameras = new ArrayList<>();
        cameras.add(stored);
        home.setStoredCameras(cameras);

        Response resp = execute("name", "Kitchen");
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("observer", data.get("mode"));
        assertEquals("Kitchen", data.get("restoredFrom"));
        assertEquals(100.0f, ((Number) data.get("x")).floatValue(), 0.1f);
        assertEquals(200.0f, ((Number) data.get("y")).floatValue(), 0.1f);
        assertEquals(170.0f, ((Number) data.get("z")).floatValue(), 0.1f);
    }

    @Test
    void testRestoreNonexistentCamera() {
        Response resp = execute("name", "NonExistent");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("NonExistent"));
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRestoreAppliesYawPitchFov() {
        Camera stored = new Camera(50f, 50f, 170f,
                (float) Math.toRadians(90), (float) Math.toRadians(-15),
                (float) Math.toRadians(75));
        stored.setName("Wide Angle");
        List<Camera> cameras = new ArrayList<>();
        cameras.add(stored);
        home.setStoredCameras(cameras);

        Response resp = execute("name", "Wide Angle");
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(90.0, ((Number) data.get("yaw_degrees")).doubleValue(), 0.5);
        assertEquals(-15.0, ((Number) data.get("pitch_degrees")).doubleValue(), 0.5);
        assertEquals(75.0, ((Number) data.get("fov_degrees")).doubleValue(), 0.5);
    }

    // --- lookAt ---

    @Test
    @SuppressWarnings("unchecked")
    void testLookAtComputesYawPitch() {
        // Camera at (0, 0, 170), looking at (500, 500, 0)
        // dx=500, dy=500, dz=-170
        // yaw = atan2(-500, 500) = atan2(-1, 1) = -π/4 = -45° = 315°
        Map<String, Object> lookAt = new LinkedHashMap<>();
        lookAt.put("x", 500.0);
        lookAt.put("y", 500.0);
        lookAt.put("z", 0.0);

        Response resp = execute("mode", "observer", "x", 0.0, "y", 0.0, "z", 170.0, "lookAt", lookAt);
        assertFalse(resp.isError(), "Expected success, got: " + resp.getMessage());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        double yawDeg = ((Number) data.get("yaw_degrees")).doubleValue();
        // Normalize to 0-360
        while (yawDeg < 0) yawDeg += 360;
        assertEquals(315.0, yawDeg, 1.0, "Yaw should be ~315 degrees");
    }

    @Test
    void testLookAtConflictWithYaw() {
        Map<String, Object> lookAt = new LinkedHashMap<>();
        lookAt.put("x", 100.0);
        lookAt.put("y", 100.0);
        Response resp = execute("mode", "observer", "lookAt", lookAt, "yaw", 90.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mutually exclusive"));
    }

    @Test
    void testLookAtConflictWithPitch() {
        Map<String, Object> lookAt = new LinkedHashMap<>();
        lookAt.put("x", 100.0);
        lookAt.put("y", 100.0);
        Response resp = execute("mode", "observer", "lookAt", lookAt, "pitch", 10.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mutually exclusive"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLookAtWithoutZ() {
        // z defaults to 0 when not provided
        Map<String, Object> lookAt = new LinkedHashMap<>();
        lookAt.put("x", 0.0);
        lookAt.put("y", 500.0);

        Response resp = execute("mode", "observer", "x", 0.0, "y", 0.0, "z", 170.0, "lookAt", lookAt);
        assertFalse(resp.isError(), "Expected success, got: " + resp.getMessage());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        // Camera at (0,0,170) looking at (0,500,0): dx=0, dy=500 → yaw=atan2(0,500)=0 → 0 degrees (south)
        double yawDeg = ((Number) data.get("yaw_degrees")).doubleValue();
        while (yawDeg < 0) yawDeg += 360;
        assertEquals(0.0, yawDeg, 1.0, "Yaw should be ~0 degrees (looking south)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLookAtPitchComputation() {
        // Camera at (0, 0, 200), looking at (0, 100, 0)
        // dx=0, dy=100, dz=-200
        // horizontalDist=100, pitch=atan2(200, 100) ≈ 63.4°
        Map<String, Object> lookAt = new LinkedHashMap<>();
        lookAt.put("x", 0.0);
        lookAt.put("y", 100.0);
        lookAt.put("z", 0.0);

        Response resp = execute("mode", "observer", "x", 0.0, "y", 0.0, "z", 200.0, "lookAt", lookAt);
        assertFalse(resp.isError(), "Expected success, got: " + resp.getMessage());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        double pitchDeg = ((Number) data.get("pitch_degrees")).doubleValue();
        // pitch = atan2(-dz, horizontalDist) = atan2(200, 100) ≈ 63.4°
        assertEquals(63.4, pitchDeg, 1.0, "Pitch should be ~63.4 degrees (looking down)");
    }

    @Test
    void testLookAtTopModeError() {
        Map<String, Object> lookAt = new LinkedHashMap<>();
        lookAt.put("x", 100.0);
        lookAt.put("y", 100.0);
        Response resp = execute("mode", "top", "lookAt", lookAt);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("observer mode"));
    }

    // --- target ---

    @Test
    @SuppressWarnings("unchecked")
    void testTargetCenter() {
        // Add walls to create a scene with center at (250, 200)
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        home.addWall(new Wall(500, 0, 500, 400, 10, 250));
        home.addWall(new Wall(500, 400, 0, 400, 10, 250));
        home.addWall(new Wall(0, 400, 0, 0, 10, 250));

        Response resp = execute("mode", "observer", "x", 0.0, "y", 0.0, "z", 170.0, "target", "center");
        assertFalse(resp.isError(), "Expected success, got: " + resp.getMessage());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        // Camera at (0,0) looking at center (~250, ~200) → yaw should point toward +X/+Y quadrant
        assertNotNull(data.get("yaw_degrees"));
        assertNotNull(data.get("pitch_degrees"));
    }

    @Test
    void testTargetCenterEmptyScene() {
        CommandException ex = assertThrows(CommandException.class,
                () -> execute("mode", "observer", "target", "center"));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void testTargetConflictWithYaw() {
        home.addWall(new Wall(0, 0, 100, 0, 10, 250));
        Response resp = execute("mode", "observer", "target", "center", "yaw", 90.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mutually exclusive"));
    }

    @Test
    void testTargetConflictWithLookAt() {
        home.addWall(new Wall(0, 0, 100, 0, 10, 250));
        Map<String, Object> lookAt = new LinkedHashMap<>();
        lookAt.put("x", 100.0);
        lookAt.put("y", 100.0);
        Response resp = execute("mode", "observer", "target", "center", "lookAt", lookAt);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mutually exclusive"));
    }

    @Test
    void testTargetInvalidValue() {
        home.addWall(new Wall(0, 0, 100, 0, 10, 250));
        CommandException ex = assertThrows(CommandException.class,
                () -> execute("mode", "observer", "target", "origin"));
        assertTrue(ex.getMessage().contains("center"));
    }

    // --- target: object IDs ---

    @Test
    @SuppressWarnings("unchecked")
    void testTargetFurniture() {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture("Table", null, null, 100, 60, 75, true, false));
        piece.setX(300);
        piece.setY(200);
        home.addPieceOfFurniture(piece);

        Response resp = execute("mode", "observer", "x", 0.0, "y", 0.0, "z", 170.0,
                "target", "furniture:" + piece.getId());
        assertFalse(resp.isError(), "Expected success, got: " + resp.getMessage());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertNotNull(data.get("yaw_degrees"));
        assertNotNull(data.get("pitch_degrees"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTargetWall() {
        Wall wall = new Wall(100, 100, 500, 100, 10, 250);
        home.addWall(wall);

        Response resp = execute("mode", "observer", "x", 0.0, "y", 0.0, "z", 170.0,
                "target", "wall:" + wall.getId());
        assertFalse(resp.isError(), "Expected success, got: " + resp.getMessage());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertNotNull(data.get("yaw_degrees"));
        assertNotNull(data.get("pitch_degrees"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTargetRoom() {
        Room room = new Room(new float[][]{{0, 0}, {400, 0}, {400, 300}, {0, 300}});
        home.addRoom(room);

        Response resp = execute("mode", "observer", "x", -100.0, "y", -100.0, "z", 170.0,
                "target", "room:" + room.getId());
        assertFalse(resp.isError(), "Expected success, got: " + resp.getMessage());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertNotNull(data.get("yaw_degrees"));
        assertNotNull(data.get("pitch_degrees"));
    }

    @Test
    void testTargetFurnitureNotFound() {
        CommandException ex = assertThrows(CommandException.class,
                () -> execute("mode", "observer", "target", "furniture:nonexistent-id"));
        assertTrue(ex.getMessage().contains("Furniture not found"));
    }

    @Test
    void testTargetWallNotFound() {
        CommandException ex = assertThrows(CommandException.class,
                () -> execute("mode", "observer", "target", "wall:nonexistent-id"));
        assertTrue(ex.getMessage().contains("Wall not found"));
    }

    @Test
    void testTargetRoomNotFound() {
        CommandException ex = assertThrows(CommandException.class,
                () -> execute("mode", "observer", "target", "room:nonexistent-id"));
        assertTrue(ex.getMessage().contains("Room not found"));
    }

    @Test
    void testTargetInvalidType() {
        CommandException ex = assertThrows(CommandException.class,
                () -> execute("mode", "observer", "target", "label:some-id"));
        assertTrue(ex.getMessage().contains("unsupported type"));
    }

    @Test
    void testTargetEmptyId() {
        CommandException ex = assertThrows(CommandException.class,
                () -> execute("mode", "observer", "target", "furniture:"));
        assertTrue(ex.getMessage().contains("empty ID"));
    }

    @Test
    void testTargetCenterStillWorks() {
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        home.addWall(new Wall(500, 0, 500, 400, 10, 250));
        Response resp = execute("mode", "observer", "x", 0.0, "y", 0.0, "z", 170.0, "target", "center");
        assertFalse(resp.isError(), "target=center should still work, got: " + resp.getMessage());
    }

    @Test
    void testTargetFurnitureConflictWithYaw() {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture("Chair", null, null, 50, 50, 80, true, false));
        piece.setX(100);
        piece.setY(100);
        home.addPieceOfFurniture(piece);

        Response resp = execute("mode", "observer", "target", "furniture:" + piece.getId(), "yaw", 90.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mutually exclusive"));
    }

    @Test
    void testTargetFurnitureConflictWithLookAt() {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture("Chair", null, null, 50, 50, 80, true, false));
        piece.setX(100);
        piece.setY(100);
        home.addPieceOfFurniture(piece);

        Map<String, Object> lookAt = new LinkedHashMap<>();
        lookAt.put("x", 100.0);
        lookAt.put("y", 100.0);
        Response resp = execute("mode", "observer", "target", "furniture:" + piece.getId(), "lookAt", lookAt);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mutually exclusive"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTargetFurnitureYawPointsAtObject() {
        // Camera at (0, 0), furniture at (500, 0) — directly to the right on plan
        // Expected: yaw should point toward +X → yaw ~270° (east)
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture("Target", null, null, 50, 50, 80, true, false));
        piece.setX(500);
        piece.setY(0);
        home.addPieceOfFurniture(piece);

        Response resp = execute("mode", "observer", "x", 0.0, "y", 0.0, "z", 0.0,
                "target", "furniture:" + piece.getId());
        assertFalse(resp.isError(), "Expected success, got: " + resp.getMessage());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        double yawDeg = ((Number) data.get("yaw_degrees")).doubleValue();
        while (yawDeg < 0) yawDeg += 360;
        // Looking east (+X) → yaw should be ~270°
        assertEquals(270.0, yawDeg, 5.0, "Yaw should point toward furniture at +X (east ~270°)");
    }

    @Test
    void testTargetTopModeError() {
        home.addWall(new Wall(0, 0, 100, 0, 10, 250));
        Response resp = execute("mode", "top", "target", "center");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("observer mode"));
    }

    // --- Schema lookAt/target ---

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaContainsLookAt() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("lookAt"));
        Map<String, Object> lookAtProp = (Map<String, Object>) props.get("lookAt");
        assertEquals("object", lookAtProp.get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaContainsTarget() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("target"));
    }

    @Test
    void testDescriptionMentionsLookAt() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("lookAt") || desc.contains("LOOKAT"));
    }

    @Test
    void testDescriptionMentionsTarget() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("target") || desc.contains("TARGET"));
    }

    // --- scene center via SceneBoundsCalculator ---

    @Test
    void testSceneBoundsWithWalls() {
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        home.addWall(new Wall(500, 0, 500, 400, 10, 250));
        home.addWall(new Wall(500, 400, 0, 400, 10, 250));
        home.addWall(new Wall(0, 400, 0, 0, 10, 250));

        SceneBoundsCalculator calculator = new SceneBoundsCalculator();
        SceneBounds bounds = calculator.computeSceneBounds(accessor);
        assertNotNull(bounds);
        // Center should be approximately (250, 200) — walls have thickness so bounds vary slightly
        assertEquals(250.0, bounds.centerX, 10.0);
        assertEquals(200.0, bounds.centerY, 10.0);
        assertTrue(bounds.maxZ > 0, "maxZ should be > 0");
    }

    @Test
    void testSceneBoundsEmptyScene() {
        SceneBoundsCalculator calculator = new SceneBoundsCalculator();
        SceneBounds bounds = calculator.computeSceneBounds(accessor);
        assertNull(bounds);
    }

    // --- Helper ---

    private Response execute(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return handler.execute(new Request("set_camera", params), accessor);
    }
}
