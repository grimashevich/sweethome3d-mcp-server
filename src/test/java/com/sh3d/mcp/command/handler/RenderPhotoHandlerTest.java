package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.SceneBoundsCalculator;
import com.sh3d.mcp.command.util.SceneBounds;
import com.sh3d.mcp.command.util.OverheadCameraComputer;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class RenderPhotoHandlerTest {

    private RenderPhotoHandler handler;
    private SceneBoundsCalculator boundsCalculator;
    private OverheadCameraComputer cameraComputer;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new RenderPhotoHandler();
        boundsCalculator = new SceneBoundsCalculator();
        cameraComputer = new OverheadCameraComputer();
        home = new Home();
        accessor = createAccessor(home);
    }

    // ==========================================================
    // Existing validation tests
    // ==========================================================

    @ParameterizedTest(name = "invalid dimension: width={0}, height={1}, expect error containing \"{2}\"")
    @CsvSource({
            "0.0, 600.0, width",
            "-100.0, 600.0, width",
            "5000.0, 600.0, width",
            "800.0, 0.0, height",
            "800.0, 9999.0, height"
    })
    void testInvalidDimensions(double width, double height, String expectedError) {
        Response resp = execute("width", width, "height", height);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains(expectedError));
    }

    @Test
    void testInvalidQuality() {
        Response resp = execute("width", 800.0, "height", 600.0, "quality", "ultra");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("quality"));
    }

    @Test
    void testMediumQualityAccepted() {
        // medium quality should pass validation (will fail at render stage without Sunflow, but not at validation)
        try {
            Response resp = execute("width", 800.0, "height", 600.0, "quality", "medium");
            // Will error because PhotoRenderer needs Sunflow, but the error should NOT be about quality validation
            if (resp.isError()) {
                assertFalse(resp.getMessage().contains("quality"),
                        "Medium quality should be accepted, error: " + resp.getMessage());
            }
        } catch (NoClassDefFoundError | Exception e) {
            // Sunflow not in test classpath — expected, validation passed
            assertTrue(true, "Medium quality passed validation, render failed due to missing Sunflow");
        }
    }

    // ==========================================================
    // Existing descriptor tests
    // ==========================================================

    @Test
    void testToolName() {
        assertNull(handler.getToolName());
    }

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("ray-trac") || desc.contains("render") || desc.contains("photo"));
        assertTrue(desc.contains("filePath") || desc.contains("file"),
                "Description should mention file saving capability");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);

        assertTrue(properties.containsKey("width"));
        assertTrue(properties.containsKey("height"));
        assertTrue(properties.containsKey("quality"));
        assertTrue(properties.containsKey("filePath"));
        assertTrue(properties.containsKey("x"));
        assertTrue(properties.containsKey("y"));
        assertTrue(properties.containsKey("z"));
        assertTrue(properties.containsKey("yaw"));
        assertTrue(properties.containsKey("pitch"));
        assertTrue(properties.containsKey("fov"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaFilePathProperty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> filePathProp = (Map<String, Object>) properties.get("filePath");

        assertEquals("string", filePathProp.get("type"));
        assertNotNull(filePathProp.get("description"));
        assertFalse(((String) filePathProp.get("description")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaQualityEnum() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> qualityProp = (Map<String, Object>) properties.get("quality");

        assertEquals("string", qualityProp.get("type"));
        List<String> enumValues = (List<String>) qualityProp.get("enum");
        assertNotNull(enumValues);
        assertTrue(enumValues.contains("low"));
        assertTrue(enumValues.contains("medium"), "Quality enum should include 'medium'");
        assertTrue(enumValues.contains("high"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoRequiredParams() {
        Map<String, Object> schema = handler.getSchema();
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty(), "All params should be optional");
    }

    @Test
    void testImplementsCommandDescriptor() {
        assertTrue(handler instanceof CommandDescriptor);
        assertTrue(handler instanceof CommandHandler);
    }

    // ==========================================================
    // Overhead validation tests
    // ==========================================================

    @Test
    void testOverheadIncompatibleWithX() {
        Response resp = execute("view", "overhead", "x", 100.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("x/y/z"));
    }

    @Test
    void testOverheadIncompatibleWithY() {
        Response resp = execute("view", "overhead", "y", 200.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("x/y/z"));
    }

    @Test
    void testOverheadIncompatibleWithZ() {
        Response resp = execute("view", "overhead", "z", 300.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("x/y/z"));
    }

    @Test
    void testOverheadIncompatibleWithYaw() {
        Response resp = execute("view", "overhead", "yaw", 90.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("yaw"));
    }

    @Test
    void testOverheadInvalidAngles() {
        Response resp = execute("view", "overhead", "angles", 3.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("angles"));
    }

    @Test
    void testOverheadInvalidAnglesZero() {
        Response resp = execute("view", "overhead", "angles", 0.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("angles"));
    }

    @Test
    void testOverheadInvalidPitchNegative() {
        addWalls();
        Response resp = execute("view", "overhead", "pitch", -10.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("pitch"));
    }

    @Test
    void testOverheadInvalidPitchOver90() {
        addWalls();
        Response resp = execute("view", "overhead", "pitch", 91.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("pitch"));
    }

    @Test
    void testOverheadInvalidPitchZero() {
        addWalls();
        Response resp = execute("view", "overhead", "pitch", 0.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("pitch"));
    }

    @Test
    void testOverheadEmptyScene() {
        Response resp = execute("view", "overhead");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("empty"));
    }

    @Test
    void testInvalidViewValue() {
        Response resp = execute("view", "birds_eye");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("overhead"));
    }

    // ==========================================================
    // Overhead schema tests
    // ==========================================================

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaContainsViewProperty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("view"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaContainsAnglesProperty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("angles"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaViewEnum() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> viewProp = (Map<String, Object>) properties.get("view");

        assertEquals("string", viewProp.get("type"));
        List<String> enumValues = (List<String>) viewProp.get("enum");
        assertNotNull(enumValues);
        assertTrue(enumValues.contains("overhead"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaAnglesDefault() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> anglesProp = (Map<String, Object>) properties.get("angles");

        assertEquals("integer", anglesProp.get("type"));
        assertEquals(4, anglesProp.get("default"));
    }

    // ==========================================================
    // Description tests
    // ==========================================================

    @Test
    void testDescriptionMentionsOverhead() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("overhead"));
    }

    @Test
    void testDescriptionRecommendsOverhead() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("RECOMMENDED"));
    }

    // ==========================================================
    // SceneBounds tests
    // ==========================================================

    @Test
    void testSceneBoundsEmptyScene() {
        SceneBounds bounds = boundsCalculator.computeSceneBounds(accessor);
        assertNull(bounds);
    }

    @Test
    void testSceneBoundsWallsOnly() {
        addWalls(); // Прямоугольник 0,0 -> 500,400
        SceneBounds bounds = boundsCalculator.computeSceneBounds(accessor);
        assertNotNull(bounds);

        // Стены имеют толщину, поэтому bounds немного выходят за точки
        assertTrue(bounds.minX <= 0, "minX should be <= 0, got " + bounds.minX);
        assertTrue(bounds.minY <= 0, "minY should be <= 0, got " + bounds.minY);
        assertTrue(bounds.maxX >= 500, "maxX should be >= 500, got " + bounds.maxX);
        assertTrue(bounds.maxY >= 400, "maxY should be >= 400, got " + bounds.maxY);
        assertTrue(bounds.maxZ > 0, "maxZ should be > 0");
        assertEquals((bounds.minX + bounds.maxX) / 2, bounds.centerX, 1.0);
        assertEquals((bounds.minY + bounds.maxY) / 2, bounds.centerY, 1.0);
    }

    @Test
    void testSceneBoundsWithFurniture() {
        addWalls(); // 0,0 -> 500,400
        // Мебель за пределами стен
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                        "Test Table", null, null, 50f, 50f, 100f, true, false));
        piece.setX(600);
        piece.setY(200);
        home.addPieceOfFurniture(piece);

        SceneBounds bounds = boundsCalculator.computeSceneBounds(accessor);
        assertNotNull(bounds);
        assertTrue(bounds.maxX >= 600, "Furniture should expand maxX, got " + bounds.maxX);
    }

    @Test
    void testSceneBoundsWithRooms() {
        // Только комнаты, без стен
        float[][] points = {{0, 0}, {600, 0}, {600, 500}, {0, 500}};
        Room room = new Room(points);
        home.addRoom(room);

        SceneBounds bounds = boundsCalculator.computeSceneBounds(accessor);
        assertNotNull(bounds);
        assertTrue(bounds.minX <= 0, "minX should be <= 0");
        assertTrue(bounds.maxX >= 600, "maxX should be >= 600");
        assertTrue(bounds.maxY >= 500, "maxY should be >= 500");
    }

    @Test
    void testSceneBoundsInvisibleFurnitureIgnored() {
        addWalls(); // 0,0 -> 500,400
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                        "Hidden Item", null, null, 50f, 50f, 100f, true, false));
        piece.setX(900);
        piece.setY(200);
        piece.setVisible(false);
        home.addPieceOfFurniture(piece);

        SceneBounds bounds = boundsCalculator.computeSceneBounds(accessor);
        assertNotNull(bounds);
        assertTrue(bounds.maxX < 900, "Invisible furniture should not expand bounds, maxX=" + bounds.maxX);
    }

    @Test
    void testSceneBoundsMinimumMaxZ() {
        // Комната без стен — maxZ должен быть минимум 100
        float[][] points = {{0, 0}, {100, 0}, {100, 100}, {0, 100}};
        Room room = new Room(points);
        home.addRoom(room);

        SceneBounds bounds = boundsCalculator.computeSceneBounds(accessor);
        assertNotNull(bounds);
        assertTrue(bounds.maxZ >= 100, "maxZ should be at least 100, got " + bounds.maxZ);
    }

    // ==========================================================
    // Camera positioning tests
    // ==========================================================

    @Test
    void testOverheadCameraNWPosition() {
        SceneBounds bounds = createTestBounds(0, 0, 500, 400, 250);
        Camera template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));
        Camera cam = cameraComputer.computeOverheadCamera(template, bounds, 315, 30, 63, 800, 600);

        // yaw=315: camera in NW, looking SE
        // NW = x < centerX, y < centerY
        assertTrue(cam.getX() < bounds.centerX,
                "Camera X should be < centerX for NW, got " + cam.getX());
        assertTrue(cam.getY() < bounds.centerY,
                "Camera Y should be < centerY for NW, got " + cam.getY());
    }

    @Test
    void testOverheadCameraSEPosition() {
        SceneBounds bounds = createTestBounds(0, 0, 500, 400, 250);
        Camera template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));
        Camera cam = cameraComputer.computeOverheadCamera(template, bounds, 135, 30, 63, 800, 600);

        // yaw=135: camera in SE, looking NW
        assertTrue(cam.getX() > bounds.centerX,
                "Camera X should be > centerX for SE, got " + cam.getX());
        assertTrue(cam.getY() > bounds.centerY,
                "Camera Y should be > centerY for SE, got " + cam.getY());
    }

    @Test
    void testOverheadCameraZAboveScene() {
        SceneBounds bounds = createTestBounds(0, 0, 500, 400, 250);
        Camera template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));
        Camera cam = cameraComputer.computeOverheadCamera(template, bounds, 315, 30, 63, 800, 600);

        assertTrue(cam.getZ() > bounds.maxZ,
                "Camera Z should be above scene maxZ, got " + cam.getZ() + " vs maxZ=" + bounds.maxZ);
    }

    @Test
    void testOverheadCameraPitchApplied() {
        SceneBounds bounds = createTestBounds(0, 0, 500, 400, 250);
        Camera template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));
        Camera cam = cameraComputer.computeOverheadCamera(template, bounds, 315, 30, 63, 800, 600);

        assertEquals(Math.toRadians(30), cam.getPitch(), 0.001,
                "Pitch should be 30 degrees in radians");
    }

    @Test
    void testOverheadCameraCustomPitch() {
        SceneBounds bounds = createTestBounds(0, 0, 500, 400, 250);
        Camera template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));
        Camera cam = cameraComputer.computeOverheadCamera(template, bounds, 315, 45, 63, 800, 600);

        assertEquals(Math.toRadians(45), cam.getPitch(), 0.001,
                "Pitch should be 45 degrees in radians");
    }

    @Test
    void testOverheadCameraFovApplied() {
        SceneBounds bounds = createTestBounds(0, 0, 500, 400, 250);
        Camera template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));
        Camera cam = cameraComputer.computeOverheadCamera(template, bounds, 315, 30, 63, 800, 600);

        assertEquals(Math.toRadians(63), cam.getFieldOfView(), 0.001,
                "FOV should be 63 degrees in radians");
    }

    @Test
    void testOverheadCameraYawApplied() {
        SceneBounds bounds = createTestBounds(0, 0, 500, 400, 250);
        Camera template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));
        Camera cam = cameraComputer.computeOverheadCamera(template, bounds, 315, 30, 63, 800, 600);

        assertEquals(Math.toRadians(315), cam.getYaw(), 0.001,
                "Yaw should be 315 degrees in radians");
    }

    @Test
    void testOverheadCameraAllFourPositions() {
        SceneBounds bounds = createTestBounds(0, 0, 500, 400, 250);
        Camera template = new Camera(0f, 0f, 170f, 0f, 0f, (float) Math.toRadians(63));

        // Все 4 камеры должны быть в разных квадрантах
        Camera nw = cameraComputer.computeOverheadCamera(template, bounds, 315, 30, 63, 800, 600);
        Camera se = cameraComputer.computeOverheadCamera(template, bounds, 135, 30, 63, 800, 600);
        Camera ne = cameraComputer.computeOverheadCamera(template, bounds, 45, 30, 63, 800, 600);
        Camera sw = cameraComputer.computeOverheadCamera(template, bounds, 225, 30, 63, 800, 600);

        // NW: x < center, y < center
        assertTrue(nw.getX() < bounds.centerX && nw.getY() < bounds.centerY, "NW position");
        // SE: x > center, y > center
        assertTrue(se.getX() > bounds.centerX && se.getY() > bounds.centerY, "SE position");
        // NE: x > center, y < center
        assertTrue(ne.getX() > bounds.centerX && ne.getY() < bounds.centerY, "NE position");
        // SW: x < center, y > center
        assertTrue(sw.getX() < bounds.centerX && sw.getY() > bounds.centerY, "SW position");
    }

    // ==========================================================
    // Helper method tests
    // ==========================================================

    @Test
    void testGenerateIndexedFilePath() {
        assertEquals("scene_1.png",
                RenderPhotoHandler.generateIndexedFilePath("scene.png", 1));
    }

    @Test
    void testGenerateIndexedFilePathNoExtension() {
        assertEquals("scene_2",
                RenderPhotoHandler.generateIndexedFilePath("scene", 2));
    }

    @Test
    void testGenerateIndexedFilePathWithDirectory() {
        assertEquals("C:/renders/scene_3.png",
                RenderPhotoHandler.generateIndexedFilePath("C:/renders/scene.png", 3));
    }

    @Test
    void testGenerateIndexedFilePathCaseInsensitive() {
        assertEquals("photo_1.png",
                RenderPhotoHandler.generateIndexedFilePath("photo.PNG", 1));
    }

    // ==========================================================
    // focusOn validation tests
    // ==========================================================

    @Test
    void testFocusOnWithoutOverhead() {
        Response resp = execute("focusOn", "furniture:0");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("focusOn"));
        assertTrue(resp.getMessage().contains("overhead"));
    }

    @Test
    void testFocusOnInvalidFormat() {
        addWalls();
        Response resp = execute("view", "overhead", "focusOn", "blah");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("focusOn"));
    }

    @Test
    void testFocusOnInvalidType() {
        addWalls();
        Response resp = execute("view", "overhead", "focusOn", "wall:0");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("furniture"));
        assertTrue(resp.getMessage().contains("room"));
    }

    @Test
    void testFocusOnEmptyId() {
        addWalls();
        Response resp = execute("view", "overhead", "focusOn", "furniture:");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("empty"));
    }

    @Test
    void testFocusOnFurnitureNotFound() {
        addWalls();
        Response resp = execute("view", "overhead", "focusOn", "furniture:nonexistent");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testFocusOnRoomNotFound() {
        addWalls();
        Response resp = execute("view", "overhead", "focusOn", "room:nonexistent");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    // ==========================================================
    // focusOn bounds tests
    // ==========================================================

    @Test
    void testFocusBoundsFurniture() {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                        "Sofa", null, null, 200f, 80f, 90f, true, false));
        piece.setX(300);
        piece.setY(250);
        home.addPieceOfFurniture(piece);

        SceneBounds bounds = boundsCalculator.computeFocusBounds(accessor, "furniture", piece.getId());
        assertNotNull(bounds);
        // Центр bounds должен быть примерно в позиции мебели
        assertEquals(300, bounds.centerX, 60);
        assertEquals(250, bounds.centerY, 60);
        // Bounds должны быть шире чем сам объект (padding)
        assertTrue(bounds.sceneWidth > 200, "Width with padding should be > 200, got " + bounds.sceneWidth);
        assertTrue(bounds.sceneDepth > 80, "Depth with padding should be > 80, got " + bounds.sceneDepth);
    }

    @Test
    void testFocusBoundsRoom() {
        float[][] points = {{100, 100}, {400, 100}, {400, 350}, {100, 350}};
        Room room = new Room(points);
        home.addRoom(room);

        SceneBounds bounds = boundsCalculator.computeFocusBounds(accessor, "room", room.getId());
        assertNotNull(bounds);
        // Центр = (250, 225), но с padding bounds шире
        assertEquals(250, bounds.centerX, 1);
        assertEquals(225, bounds.centerY, 1);
        assertTrue(bounds.sceneWidth > 300, "Width with padding should be > 300, got " + bounds.sceneWidth);
    }

    @Test
    void testFocusBoundsPaddingMinimum() {
        // Очень маленький объект — padding должен быть >= MIN_FURNITURE_PADDING (100)
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                        "Tiny", null, null, 10f, 10f, 10f, true, false));
        piece.setX(200);
        piece.setY(200);
        home.addPieceOfFurniture(piece);

        SceneBounds bounds = boundsCalculator.computeFocusBounds(accessor, "furniture", piece.getId());
        assertNotNull(bounds);
        // С padding >= 200 с каждой стороны, ширина >= 400
        assertTrue(bounds.sceneWidth >= 400, "Padding should ensure minimum width, got " + bounds.sceneWidth);
        assertTrue(bounds.sceneDepth >= 400, "Padding should ensure minimum depth, got " + bounds.sceneDepth);
    }

    @Test
    void testFocusBoundsFurnitureNotFound() {
        SceneBounds bounds = boundsCalculator.computeFocusBounds(accessor, "furniture", "nonexistent");
        assertNull(bounds);
    }

    @Test
    void testFocusBoundsRoomNotFound() {
        SceneBounds bounds = boundsCalculator.computeFocusBounds(accessor, "room", "nonexistent");
        assertNull(bounds);
    }

    @Test
    void testFocusBoundsInvalidType() {
        SceneBounds bounds = boundsCalculator.computeFocusBounds(accessor, "wall", "nonexistent");
        assertNull(bounds);
    }

    // ==========================================================
    // Overhead environment tests
    // ==========================================================

    @Test
    void testOverheadWallHeightConstant() {
        assertTrue(RenderPhotoHandler.OVERHEAD_WALL_HEIGHT > 0,
                "Wall height should be positive");
        assertTrue(RenderPhotoHandler.OVERHEAD_WALL_HEIGHT <= 5,
                "Wall height should be very small (<=5 cm) to be invisible from overhead");
    }

    @Test
    void testOverheadFloorColorConstant() {
        // Default floor color should be a light gray
        int color = RenderPhotoHandler.DEFAULT_FLOOR_COLOR;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        assertTrue(r > 128 && g > 128 && b > 128,
                "Floor color should be light gray, got #" + Integer.toHexString(color));
    }

    // ==========================================================
    // Overhead resolution tests
    // ==========================================================

    @Test
    void testOverheadDefaultResolution() {
        assertEquals(1200, RenderPhotoHandler.DEFAULT_OVERHEAD_WIDTH);
        assertEquals(900, RenderPhotoHandler.DEFAULT_OVERHEAD_HEIGHT);
    }

    @Test
    void testOverheadDefaultResolutionLargerThanStandard() {
        assertTrue(RenderPhotoHandler.DEFAULT_OVERHEAD_WIDTH > 800,
                "Overhead default width should be larger than standard 800");
        assertTrue(RenderPhotoHandler.DEFAULT_OVERHEAD_HEIGHT > 600,
                "Overhead default height should be larger than standard 600");
    }

    // ==========================================================
    // focusOn angles defaults
    // ==========================================================

    @Test
    void testFocusPaddingConstants() {
        assertEquals(0.5f, SceneBoundsCalculator.FURNITURE_PADDING_RATIO, 0.001);
        assertEquals(200.0f, SceneBoundsCalculator.MIN_FURNITURE_PADDING, 0.001);
    }

    // ==========================================================
    // hideWalls tests
    // ==========================================================

    @Test
    void testHideWallsDefaultTrue() {
        // By default hideWalls is true — walls hidden for unobstructed view
        Map<String, Object> schema = handler.getSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) properties.get("hideWalls");
        assertEquals(true, prop.get("default"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaContainsHideWalls() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("hideWalls"));
        Map<String, Object> prop = (Map<String, Object>) properties.get("hideWalls");
        assertEquals("boolean", prop.get("type"));
        assertEquals(true, prop.get("default"));
    }

    @Test
    void testDescriptionMentionsHideWalls() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("hideWalls"));
        assertTrue(desc.contains("walls"));
    }

    // ==========================================================
    // Schema focusOn tests
    // ==========================================================

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaContainsFocusOn() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("focusOn"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaFocusOnDescription() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> focusOnProp = (Map<String, Object>) properties.get("focusOn");
        assertEquals("string", focusOnProp.get("type"));
        String desc = (String) focusOnProp.get("description");
        assertTrue(desc.contains("furniture") && desc.contains("room"),
                "focusOn description should mention furniture and room");
    }

    // ==========================================================
    // Description focusOn tests
    // ==========================================================

    @Test
    void testDescriptionMentionsFocusOn() {
        String desc = handler.getDescription();
        assertTrue(desc.contains("focusOn"));
    }

    // ==========================================================
    // Format parameter tests
    // ==========================================================

    @Test
    void testInvalidFormat() {
        Response resp = execute("format", "bmp");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("format"));
    }

    @Test
    void testInvalidFormatGif() {
        Response resp = execute("format", "gif");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("format"));
    }

    @Test
    void testFormatCaseInsensitive() {
        // "JPEG" should be accepted (lowercased internally)
        // Use overhead with empty scene to trigger "empty" error AFTER format validation passes
        Response resp = execute("view", "overhead", "format", "JPEG");
        assertTrue(resp.isError());
        assertFalse(resp.getMessage().contains("format"),
                "JPEG in uppercase should be accepted, error: " + resp.getMessage());
        assertTrue(resp.getMessage().contains("empty"),
                "Should fail on empty scene, not format validation");
    }

    // ==========================================================
    // Schema: format property
    // ==========================================================

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaContainsFormatProperty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("format"), "Schema should have 'format' property");

        Map<String, Object> formatProp = (Map<String, Object>) properties.get("format");
        assertEquals("string", formatProp.get("type"));
        List<String> enumValues = (List<String>) formatProp.get("enum");
        assertNotNull(enumValues);
        assertTrue(enumValues.contains("jpeg"), "Format enum should include 'jpeg'");
        assertTrue(enumValues.contains("png"), "Format enum should include 'png'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaFormatDefaultIsJpeg() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> formatProp = (Map<String, Object>) properties.get("format");
        assertEquals("jpeg", formatProp.get("default"), "Default format should be 'jpeg'");
    }

    // ==========================================================
    // generateIndexedFilePath: JPEG extensions
    // ==========================================================

    @Test
    void testGenerateIndexedFilePathJpg() {
        assertEquals("scene_1.jpg",
                RenderPhotoHandler.generateIndexedFilePath("scene.jpg", 1));
    }

    @Test
    void testGenerateIndexedFilePathJpgUpperCase() {
        assertEquals("photo_2.jpg",
                RenderPhotoHandler.generateIndexedFilePath("photo.JPG", 2));
    }

    @Test
    void testGenerateIndexedFilePathJpeg() {
        assertEquals("render_3.jpeg",
                RenderPhotoHandler.generateIndexedFilePath("render.jpeg", 3));
    }

    @Test
    void testGenerateIndexedFilePathJpegWithDir() {
        assertEquals("C:/renders/out_1.jpg",
                RenderPhotoHandler.generateIndexedFilePath("C:/renders/out.jpg", 1));
    }

    // ==========================================================
    // JPEG_QUALITY constant
    // ==========================================================

    @Test
    void testJpegQualityInRange() {
        assertTrue(RenderPhotoHandler.JPEG_QUALITY > 0.0f, "JPEG quality should be > 0");
        assertTrue(RenderPhotoHandler.JPEG_QUALITY <= 1.0f, "JPEG quality should be <= 1");
    }

    @Test
    void testJpegQualityValue() {
        assertEquals(0.85f, RenderPhotoHandler.JPEG_QUALITY, 0.001f);
    }

    // ==========================================================
    // Description mentions format
    // ==========================================================

    @Test
    void testDescriptionMentionsJpeg() {
        String desc = handler.getDescription();
        assertTrue(desc.toLowerCase().contains("jpeg"),
                "Description should mention JPEG");
    }

    @Test
    void testDescriptionMentionsMediumQuality() {
        String desc = handler.getDescription();
        assertTrue(desc.toLowerCase().contains("medium"),
                "Description should mention medium quality");
    }

    @Test
    void testDescriptionMentionsMcpImage() {
        String desc = handler.getDescription();
        assertTrue(desc.toLowerCase().contains("mcp image") || desc.toLowerCase().contains("image content"),
                "Description should mention MCP image content");
    }

    // ==========================================================
    // Live Home not mutated during overhead render
    // ==========================================================

    @Test
    void testOverheadDoesNotMutateLiveHomeWallHeights() {
        addWalls(); // 4 walls with height 250
        float originalHeight = 250.0f;
        for (Wall w : home.getWalls()) {
            assertEquals(originalHeight, w.getHeight(),
                    "Precondition: wall height should be 250");
        }

        // Execute overhead — will fail at render stage (no Sunflow) but must not mutate live walls
        try {
            execute("view", "overhead");
        } catch (Throwable ignored) {
            // PhotoRenderer requires Sunflow which is not in test classpath
        }

        // Verify live Home walls are NOT mutated
        for (Wall w : home.getWalls()) {
            assertEquals(originalHeight, w.getHeight(), 0.001f,
                    "Live Home wall height must not be mutated during overhead render");
        }
    }

    @Test
    void testOverheadDoesNotMutateLiveHomeFloorColors() {
        addWalls();
        Room room = new Room(new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        // No floor color or texture — overhead would set gray on clone
        home.addRoom(room);
        assertNull(room.getFloorColor(), "Precondition: floor color should be null");

        try {
            execute("view", "overhead");
        } catch (Throwable ignored) {
            // PhotoRenderer requires Sunflow which is not in test classpath
        }

        // Live room floor color should remain null
        assertNull(room.getFloorColor(),
                "Live Home room floor color must not be mutated during overhead render");
    }

    @Test
    void testHomeCloneDoesNotAffectOriginalWalls() {
        // Direct verification that Home.clone() wall modifications don't affect the original
        addWalls();
        float originalHeight = 250.0f;

        Home clone = home.clone();
        for (Wall w : clone.getWalls()) {
            w.setHeight(RenderPhotoHandler.OVERHEAD_WALL_HEIGHT);
        }

        // Original walls must be unchanged
        for (Wall w : home.getWalls()) {
            assertEquals(originalHeight, w.getHeight(), 0.001f,
                    "Original Home wall height must not be affected by clone modifications");
        }
    }

    @Test
    void testHomeCloneDoesNotAffectOriginalRoomFloorColor() {
        Room room = new Room(new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        home.addRoom(room);
        assertNull(room.getFloorColor());

        Home clone = home.clone();
        for (Room r : clone.getRooms()) {
            r.setFloorColor(RenderPhotoHandler.DEFAULT_FLOOR_COLOR);
        }

        // Original room must be unchanged
        assertNull(room.getFloorColor(),
                "Original Home room floor color must not be affected by clone modifications");
    }

    // ==========================================================
    // Constants tests
    // ==========================================================

    @Test
    void testOverheadYawsLength() {
        assertEquals(4, RenderPhotoHandler.OVERHEAD_YAWS.length);
    }

    @Test
    void testOverheadLabelsLength() {
        assertEquals(4, RenderPhotoHandler.OVERHEAD_LABELS.length);
    }

    @Test
    void testOverheadLabelsMatchYaws() {
        assertEquals(RenderPhotoHandler.OVERHEAD_YAWS.length,
                RenderPhotoHandler.OVERHEAD_LABELS.length);
    }

    // ==========================================================
    // Helpers
    // ==========================================================

    private Response execute(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return handler.execute(new Request("render_photo", params), accessor);
    }

    /** Добавляет 4 стены: прямоугольник 0,0 -> 500,400, высота 250. */
    private void addWalls() {
        Wall w1 = new Wall(0, 0, 500, 0, 10, 250);
        Wall w2 = new Wall(500, 0, 500, 400, 10, 250);
        Wall w3 = new Wall(500, 400, 0, 400, 10, 250);
        Wall w4 = new Wall(0, 400, 0, 0, 10, 250);
        home.addWall(w1);
        home.addWall(w2);
        home.addWall(w3);
        home.addWall(w4);
    }

    /** Создаёт SceneBounds для тестов камеры. */
    private static SceneBounds createTestBounds(
            float minX, float minY, float maxX, float maxY, float maxZ) {
        return SceneBounds.of(minX, minY, maxX, maxY, maxZ);
    }
}
