package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class LoadHomeHandlerTest {

    private LoadHomeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LoadHomeHandler();
    }

    // --- Descriptor tests ---

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandHandler);
        assertTrue(handler instanceof CommandDescriptor);
    }

    @Test
    void testToolNameNull() {
        assertNull(handler.getToolName());
    }

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.toLowerCase().contains("load") || desc.contains(".sh3d"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("filePath"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaFilePathProperty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> filePath = (Map<String, Object>) properties.get("filePath");

        assertEquals("string", filePath.get("type"));
        assertNotNull(filePath.get("description"));
        assertFalse(((String) filePath.get("description")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaRequiresFilePath() {
        Map<String, Object> schema = handler.getSchema();
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.contains("filePath"));
    }

    // --- Validation tests ---

    @Test
    void testMissingFilePath() {
        Home home = new Home();
        HomeAccessor accessor = createAccessor(home);
        Request request = new Request("load_home", Collections.emptyMap());

        Response response = handler.execute(request, accessor);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("filePath"));
    }

    @Test
    void testEmptyFilePath() {
        Home home = new Home();
        HomeAccessor accessor = createAccessor(home);
        Request request = new Request("load_home", Map.of("filePath", ""));

        Response response = handler.execute(request, accessor);

        assertTrue(response.isError());
        assertTrue(response.getMessage().contains("filePath"));
    }

    @Test
    void testFileNotFound() {
        Home home = new Home();
        HomeAccessor accessor = createAccessor(home);
        Request request = new Request("load_home",
                Map.of("filePath", "C:\\nonexistent\\path\\file.sh3d"));

        Response response = handler.execute(request, accessor);

        assertTrue(response.isError());
        assertTrue(response.getMessage().toLowerCase().contains("not found"));
    }

    // --- clearAll tests ---

    @Test
    void testClearAllRemovesWalls() {
        Home home = new Home();
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        home.addWall(new Wall(500, 0, 500, 400, 10, 250));
        assertEquals(2, home.getWalls().size());

        LoadHomeHandler.clearAll(home);

        assertTrue(home.getWalls().isEmpty());
    }

    @Test
    void testClearAllRemovesRooms() {
        Home home = new Home();
        home.addRoom(new Room(new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}}));
        assertEquals(1, home.getRooms().size());

        LoadHomeHandler.clearAll(home);

        assertTrue(home.getRooms().isEmpty());
    }

    @Test
    void testClearAllRemovesFurniture() {
        Home home = new Home();
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                        "test", null, null, 50, 50, 50, 0, false, null, null, false, 0, false));
        home.addPieceOfFurniture(piece);
        assertEquals(1, home.getFurniture().size());

        LoadHomeHandler.clearAll(home);

        assertTrue(home.getFurniture().isEmpty());
    }

    @Test
    void testClearAllRemovesLabels() {
        Home home = new Home();
        home.addLabel(new Label("Test Label", 100, 200));
        assertEquals(1, home.getLabels().size());

        LoadHomeHandler.clearAll(home);

        assertTrue(home.getLabels().isEmpty());
    }

    @Test
    void testClearAllRemovesDimensionLines() {
        Home home = new Home();
        home.addDimensionLine(new DimensionLine(0, 0, 500, 0, 20));
        assertEquals(1, home.getDimensionLines().size());

        LoadHomeHandler.clearAll(home);

        assertTrue(home.getDimensionLines().isEmpty());
    }

    @Test
    void testClearAllRemovesLevels() {
        Home home = new Home();
        home.addLevel(new Level("Floor 1", 0, 12, 250));
        home.addLevel(new Level("Floor 2", 300, 12, 250));
        assertEquals(2, home.getLevels().size());

        LoadHomeHandler.clearAll(home);

        assertTrue(home.getLevels().isEmpty());
    }

    @Test
    void testClearAllRemovesStoredCameras() {
        Home home = new Home();
        Camera cam = new Camera(100, 200, 300, 0, 0, (float) Math.PI / 4);
        cam.setName("TestCam");
        home.setStoredCameras(Arrays.asList(cam));
        assertEquals(1, home.getStoredCameras().size());

        LoadHomeHandler.clearAll(home);

        assertTrue(home.getStoredCameras().isEmpty());
    }

    @Test
    void testClearAllOnEmptyHome() {
        Home home = new Home();

        // Should not throw
        LoadHomeHandler.clearAll(home);

        assertTrue(home.getWalls().isEmpty());
        assertTrue(home.getRooms().isEmpty());
        assertTrue(home.getFurniture().isEmpty());
    }

    // --- copyEnvironment tests ---

    @Test
    void testCopyEnvironmentColors() {
        HomeEnvironment source = new HomeEnvironment();
        source.setGroundColor(0xFF0000);
        source.setSkyColor(0x00FF00);
        source.setLightColor(0x0000FF);
        source.setCeillingLightColor(0xFFFF00);

        HomeEnvironment target = new HomeEnvironment();
        LoadHomeHandler.copyEnvironment(target, source);

        assertEquals(0xFF0000, target.getGroundColor());
        assertEquals(0x00FF00, target.getSkyColor());
        assertEquals(0x0000FF, target.getLightColor());
        assertEquals(0xFFFF00, target.getCeillingLightColor());
    }

    @Test
    void testCopyEnvironmentAlphaAndMode() {
        HomeEnvironment source = new HomeEnvironment();
        source.setWallsAlpha(0.5f);
        source.setDrawingMode(HomeEnvironment.DrawingMode.FILL);
        source.setAllLevelsVisible(true);

        HomeEnvironment target = new HomeEnvironment();
        LoadHomeHandler.copyEnvironment(target, source);

        assertEquals(0.5f, target.getWallsAlpha(), 0.001f);
        assertEquals(HomeEnvironment.DrawingMode.FILL, target.getDrawingMode());
        assertTrue(target.isAllLevelsVisible());
    }

    @Test
    void testCopyEnvironmentPhotoSettings() {
        HomeEnvironment source = new HomeEnvironment();
        source.setPhotoWidth(1920);
        source.setPhotoHeight(1080);
        source.setPhotoQuality(2);

        HomeEnvironment target = new HomeEnvironment();
        LoadHomeHandler.copyEnvironment(target, source);

        assertEquals(1920, target.getPhotoWidth());
        assertEquals(1080, target.getPhotoHeight());
        assertEquals(2, target.getPhotoQuality());
    }

    // --- copyCompass tests ---

    @Test
    void testCopyCompass() {
        Compass source = new Compass(100, 200, 50);
        source.setNorthDirection(1.5f);
        source.setLatitude(45.0f);
        source.setLongitude(30.0f);
        source.setVisible(false);

        Compass target = new Compass(0, 0, 100);
        LoadHomeHandler.copyCompass(target, source);

        assertEquals(100, target.getX(), 0.001f);
        assertEquals(200, target.getY(), 0.001f);
        assertEquals(50, target.getDiameter(), 0.001f);
        assertEquals(1.5f, target.getNorthDirection(), 0.001f);
        assertEquals(45.0f, target.getLatitude(), 0.001f);
        assertEquals(30.0f, target.getLongitude(), 0.001f);
        assertFalse(target.isVisible());
    }

    // --- copyCameras tests ---

    @Test
    void testCopyCamerasTopMode() {
        Home source = new Home();
        // Top camera is default active

        Home target = new Home();
        // Set to observer first
        target.setCamera(target.getObserverCamera());

        LoadHomeHandler.copyCameras(target, source);

        // Should switch back to top camera
        assertFalse(target.getCamera() instanceof ObserverCamera);
    }

    @Test
    void testCopyCamerasObserverMode() {
        Home source = new Home();
        source.setCamera(source.getObserverCamera());

        Home target = new Home();

        LoadHomeHandler.copyCameras(target, source);

        assertTrue(target.getCamera() instanceof ObserverCamera);
    }

    @Test
    void testCopyCamerasPosition() {
        Home source = new Home();
        Camera topCam = source.getTopCamera();
        topCam.setX(500);
        topCam.setY(300);
        topCam.setZ(1200);

        Home target = new Home();

        LoadHomeHandler.copyCameras(target, source);

        assertEquals(500, target.getTopCamera().getX(), 0.01f);
        assertEquals(300, target.getTopCamera().getY(), 0.01f);
        assertEquals(1200, target.getTopCamera().getZ(), 0.01f);
    }
}
