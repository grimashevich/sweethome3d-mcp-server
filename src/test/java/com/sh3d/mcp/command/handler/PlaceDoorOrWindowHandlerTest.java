package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceDoorOrWindowHandlerTest {

    private PlaceDoorOrWindowHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() throws Exception {
        handler = new PlaceDoorOrWindowHandler();
        home = new Home();

        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory doorsCategory = new FurnitureCategory("Doors");
        FurnitureCategory windowsCategory = new FurnitureCategory("Windows");
        FurnitureCategory furnitureCategory = new FurnitureCategory("Living Room");

        // SH3D bug: простой конструктор не передаёт doorOrWindow в master-конструктор.
        // Используем reflection для установки private final поля.
        CatalogPieceOfFurniture door = new CatalogPieceOfFurniture(
                "Front Door", null, null, 80f, 10f, 210f, false, false);
        setDoorOrWindow(door, true);

        CatalogPieceOfFurniture window = new CatalogPieceOfFurniture(
                "Double Window", null, null, 120f, 8f, 100f, false, false);
        setDoorOrWindow(window, true);

        // isDoorOrWindow = false — regular furniture
        CatalogPieceOfFurniture table = new CatalogPieceOfFurniture(
                "Dining Table", null, null, 120f, 80f, 75f, true, false);

        catalog.add(doorsCategory, door);
        catalog.add(windowsCategory, window);
        catalog.add(furnitureCategory, table);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(home, prefs);
    }

    /**
     * Устанавливает doorOrWindow через reflection.
     * Простой конструктор CatalogPieceOfFurniture (SH3D 7.x) не передаёт
     * этот параметр в master-конструктор — баг в цепочке делегирования.
     */
    private static void setDoorOrWindow(CatalogPieceOfFurniture piece, boolean value)
            throws Exception {
        Field field = CatalogPieceOfFurniture.class.getDeclaredField("doorOrWindow");
        field.setAccessible(true);
        field.set(piece, value);
    }

    private Wall addWall(float xStart, float yStart, float xEnd, float yEnd) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10);
        wall.setHeight(250f);
        home.addWall(wall);
        return wall;
    }

    // --- Success cases ---

    @Test
    void testPlaceDoorInWallCenter() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Front Door", resp.getData().get("name"));
        assertEquals(250.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(0.0, (double) resp.getData().get("y"), 0.01);
        assertEquals(true, resp.getData().get("isDoorOrWindow"));
    }

    @Test
    void testFurnitureAddedToHome() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        handler.execute(new Request("place_door_or_window", params), accessor);

        assertEquals(1, home.getFurniture().size());
        assertTrue(home.getFurniture().get(0).isDoorOrWindow());
    }

    @Test
    void testPlaceWindowWithElevation() {
        Wall wall = addWall(0, 0, 400, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Double Window");
        params.put("wallId", wall.getId());
        params.put("elevation", 90.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(90.0, (double) resp.getData().get("elevation"), 0.01);
        assertEquals(90f, home.getFurniture().get(0).getElevation(), 0.01f);
    }

    @Test
    void testPlaceWithMirroredTrue() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());
        params.put("mirrored", true);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(true, resp.getData().get("mirrored"));
        assertTrue(home.getFurniture().get(0).isModelMirrored());
    }

    @Test
    void testDefaultMirroredIsFalse() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(false, resp.getData().get("mirrored"));
    }

    // --- Position calculations ---

    @Test
    void testPlaceAtPositionZero() {
        Wall wall = addWall(100, 200, 500, 200);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());
        params.put("position", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(100.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(200.0, (double) resp.getData().get("y"), 0.01);
    }

    @Test
    void testPlaceAtPositionOne() {
        Wall wall = addWall(100, 200, 500, 200);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());
        params.put("position", 1.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(500.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(200.0, (double) resp.getData().get("y"), 0.01);
    }

    @Test
    void testCustomPosition() {
        Wall wall = addWall(0, 0, 400, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());
        params.put("position", 0.25);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(100.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(0.0, (double) resp.getData().get("y"), 0.01);
    }

    // --- Angle calculations ---

    @Test
    void testAngleHorizontalWall() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.0, (double) resp.getData().get("angle"), 0.01);
    }

    @Test
    void testAngleVerticalWall() {
        Wall wall = addWall(0, 0, 0, 300);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(90.0, (double) resp.getData().get("angle"), 0.01);
    }

    @Test
    void testAngleDiagonalWall() {
        Wall wall = addWall(0, 0, 300, 300);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(45.0, (double) resp.getData().get("angle"), 0.01);
    }

    // --- Catalog filtering ---

    @Test
    void testCatalogFiltersOnlyDoorsWindows() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Front Door", resp.getData().get("name"));
    }

    @Test
    void testRegularFurnitureNotFoundAsDoorOrWindow() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Table");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Door/window not found"));
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testCaseInsensitiveSearch() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "front door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Front Door", resp.getData().get("name"));
    }

    @Test
    void testPartialNameMatch() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Window");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Double Window", resp.getData().get("name"));
    }

    // --- Validation errors ---

    @Test
    void testMissingNameReturnsError() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testEmptyNameReturnsError() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testMissingWallIdReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wallId"));
    }

    @Test
    void testWallIdNotFoundReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", "nonexistent-wall-id");

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testPositionAboveOneReturnsError() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());
        params.put("position", 1.5);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("position"));
    }

    @Test
    void testPositionBelowZeroReturnsError() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());
        params.put("position", -0.1);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("position"));
    }

    // --- Misc ---

    @Test
    void testMultiplePlacementsOnSameWall() {
        Wall wall = addWall(0, 0, 600, 0);

        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("name", "Front Door");
        params1.put("wallId", wall.getId());
        params1.put("position", 0.25);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("name", "Double Window");
        params2.put("wallId", wall.getId());
        params2.put("position", 0.75);

        handler.execute(new Request("place_door_or_window", params1), accessor);
        handler.execute(new Request("place_door_or_window", params2), accessor);

        assertEquals(2, home.getFurniture().size());
        assertEquals(150f, home.getFurniture().get(0).getX(), 0.01f);
        assertEquals(450f, home.getFurniture().get(1).getX(), 0.01f);
    }

    // --- Auto-fit depth to wall thickness ---

    @Test
    void testDepthAutoFitToWallThickness() {
        // Door depth (10) < wall thickness (default 10), so no change
        Wall wall = addWall(0, 0, 500, 0); // thickness=10

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        // Door depth=10, wall thickness=10, no change needed
        assertEquals(10.0, (double) resp.getData().get("depth"), 0.01);
    }

    @Test
    void testDepthAutoFitWhenDoorThinnerThanWall() {
        // Create wall with thickness=20, door depth=10 -> should be auto-fit to 20
        Wall wall = new Wall(0, 0, 500, 0, 20);
        wall.setHeight(250f);
        home.addWall(wall);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        // Door depth should be increased to wall thickness
        assertEquals(20.0, (double) resp.getData().get("depth"), 0.01);
        assertEquals(20f, home.getFurniture().get(0).getDepth(), 0.01f);
    }

    @Test
    void testDepthNotReducedWhenDoorThickerThanWall() {
        // Create wall with thickness=5, door depth=10 -> should stay 10
        Wall wall = new Wall(0, 0, 500, 0, 5);
        wall.setHeight(250f);
        home.addWall(wall);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        // Door depth should NOT be reduced
        assertEquals(10.0, (double) resp.getData().get("depth"), 0.01);
        assertEquals(10f, home.getFurniture().get(0).getDepth(), 0.01f);
    }

    @Test
    void testResponseContainsAllFields() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertNotNull(data.get("name"));
        assertNotNull(data.get("x"));
        assertNotNull(data.get("y"));
        assertNotNull(data.get("angle"));
        assertNotNull(data.get("elevation"));
        assertNotNull(data.get("width"));
        assertNotNull(data.get("depth"));
        assertNotNull(data.get("height"));
        assertNotNull(data.get("isDoorOrWindow"));
        assertNotNull(data.get("mirrored"));
        assertNotNull(data.get("wallId"));
        assertNotNull(data.get("position"));
    }

    @Test
    void testDefaultPositionIsCenter() {
        Wall wall = addWall(0, 0, 400, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());
        // no position param — should default to 0.5

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(200.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(0.5, (double) resp.getData().get("position"), 0.01);
    }

    @Test
    void testDoorNotFoundReturnsError() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "NonExistent Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Door/window not found"));
        assertTrue(resp.getMessage().contains("NonExistent Door"));
    }

    // ==================== Exact match priority ====================

    @Test
    void testExactMatchPreferredOverSubstring() throws Exception {
        // Add "Door" (exact) alongside existing "Front Door" (substring)
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory cat = new FurnitureCategory("Doors");

        CatalogPieceOfFurniture door = new CatalogPieceOfFurniture(
                "Door", null, null, 87f, 10f, 210f, false, false);
        setDoorOrWindow(door, true);
        CatalogPieceOfFurniture frontDoor = new CatalogPieceOfFurniture(
                "Front Door", null, null, 91.5f, 10f, 210f, false, false);
        setDoorOrWindow(frontDoor, true);

        catalog.add(cat, frontDoor); // front door added FIRST
        catalog.add(cat, door);       // exact match added SECOND

        Home localHome = new Home();
        Wall wall = new Wall(0, 0, 500, 0, 10);
        localHome.addWall(wall);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor localAccessor = new HomeAccessor(localHome, prefs);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), localAccessor);

        assertTrue(resp.isOk());
        // Should find "Door" (exact match), NOT "Front Door" (substring)
        assertEquals("Door", resp.getData().get("name"));
    }

    // ==================== catalogId ====================

    @Test
    void testCatalogIdSuccess() throws Exception {
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory cat = new FurnitureCategory("Doors");

        CatalogPieceOfFurniture door = new CatalogPieceOfFurniture(
                "door-001", "Front Door", null, null, null,
                80f, 10f, 210f, 0f, false, null, null, true, null, null);
        setDoorOrWindow(door, true);
        catalog.add(cat, door);

        Home localHome = new Home();
        Wall wall = new Wall(0, 0, 500, 0, 10);
        localHome.addWall(wall);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor localAccessor = new HomeAccessor(localHome, prefs);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("catalogId", "door-001");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), localAccessor);

        assertTrue(resp.isOk());
        assertEquals("Front Door", resp.getData().get("name"));
    }

    @Test
    void testCatalogIdNotFound() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("catalogId", "nonexistent");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("nonexistent"));
    }

    // ==================== returned id ====================

    @Test
    void testResponseContainsId() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        Object id = resp.getData().get("id");
        assertNotNull(id, "Response must contain id");
        assertInstanceOf(String.class, id, "ID must be a string");
        assertFalse(((String) id).isEmpty(), "ID must not be empty");
    }

    @Test
    void testIdIsStableString() {
        Wall wall = addWall(0, 0, 500, 0);

        // Добавляем мебель до вызова
        HomePieceOfFurniture existing = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(
                        "Existing Sofa", null, null, 200f, 80f, 85f, true, false));
        home.addPieceOfFurniture(existing);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        String id = (String) resp.getData().get("id");
        assertNotEquals(existing.getId(), id, "New furniture must have different ID");

        // Verify the returned ID matches the placed furniture
        HomePieceOfFurniture placed = home.getFurniture().stream()
                .filter(p -> p.getId().equals(id)).findFirst().orElse(null);
        assertNotNull(placed);
        assertEquals("Front Door", placed.getName());
    }

    @Test
    void testBothNameAndCatalogIdMissingReturnsError() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
        assertTrue(resp.getMessage().contains("catalogId"));
    }
}
