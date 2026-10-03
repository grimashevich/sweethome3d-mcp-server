package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
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

        // A real catalog door, as loaded from a furniture library: carries sashes
        // and wall metadata that only HomeDoorOrWindow preserves.
        CatalogDoorOrWindow sashDoor = new CatalogDoorOrWindow(
                "test#sash-door", "Sash Door", null, null, null,
                90f, 12f, 210f, 0f, false, 1f, 0f,
                new Sash[] { new Sash(0f, 0f, 1f, 0f, (float) Math.PI / 2) },
                null, null, true, null, null);

        catalog.add(doorsCategory, door);
        catalog.add(doorsCategory, sashDoor);
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
        assertEquals(20f, home.getFurniture().get(0).getDepthInPlan(), 0.01f,
                "plan footprint follows the auto-fitted depth");
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

    // --- Door/window class ---

    @Test
    void testCatalogDoorBecomesHomeDoorOrWindow() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sash Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        HomePieceOfFurniture placed = home.getFurniture().get(0);
        assertTrue(placed instanceof HomeDoorOrWindow,
                "catalog doors must be placed as HomeDoorOrWindow, not a generic piece");
        HomeDoorOrWindow door = (HomeDoorOrWindow) placed;
        assertEquals(1, door.getSashes().length, "sashes from the catalog are kept");
        assertTrue(door.isBoundToWall());
    }

    @Test
    void testGenericDoorFlagStillPlacedAsPlainPiece() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", wall.getId());

        handler.execute(new Request("place_door_or_window", params), accessor);

        HomePieceOfFurniture placed = home.getFurniture().get(0);
        assertFalse(placed instanceof HomeDoorOrWindow);
        assertTrue(placed.isDoorOrWindow());
    }

    // --- Frame fit in wall ---
    // Спецификация — поведение SH3D при перетаскивании двери на стену: коробка
    // (доля wallThickness глубины модели от доли wallDistance) занимает толщину стены;
    // растягиваемая модель получает нужную глубину, план рисуется той же глубиной.

    private static final float EPS = 0.01f;

    /** Дверь каталога с метаданными коробки; модель без поворота. */
    private CatalogDoorOrWindow frameDoor(String name, float depth, float frameRatio, float frameOffset,
                                          boolean widthDepthDeformable, boolean resizable) {
        CatalogDoorOrWindow d = new CatalogDoorOrWindow(
                "test#" + name, name, null, null, null, null, null,
                null, null, null,
                90f, depth, 210f, 0f, 0f, false, null,
                frameRatio, frameOffset, true, widthDepthDeformable,
                new Sash[0], new float[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, false, null, null,
                resizable, true, true, (BigDecimal) null, (BigDecimal) null, null);
        accessor.getFurnitureCatalog().add(
                accessor.getFurnitureCatalog().getCategories().get(0), d);
        return d;
    }

    private Wall addWall(float xStart, float yStart, float xEnd, float yEnd, float thickness) {
        Wall wall = addWall(xStart, yStart, xEnd, yEnd);
        wall.setThickness(thickness);
        return wall;
    }

    private HomeDoorOrWindow place(String name, Wall wall, double position) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("wallId", wall.getId());
        params.put("position", position);
        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);
        assertTrue(resp.isOk(), () -> String.valueOf(resp.getMessage()));
        return (HomeDoorOrWindow) home.getFurniture().get(home.getFurniture().size() - 1);
    }

    /** Знаковые расстояния начала и конца коробки от оси стены (по нормали стены), по возрастанию. */
    private static float[] frameBand(HomeDoorOrWindow d, Wall wall) {
        double a = Math.atan2(wall.getYEnd() - wall.getYStart(), wall.getXEnd() - wall.getXStart());
        double nx = -Math.sin(a), ny = Math.cos(a);
        double fx = -Math.sin(d.getAngle()), fy = Math.cos(d.getAngle());   // лицевая ось изделия
        double backX = d.getX() - fx * d.getDepth() / 2, backY = d.getY() - fy * d.getDepth() / 2;
        double s = d.getWallDistance() * d.getDepth();
        double e = (d.getWallDistance() + d.getWallThickness()) * d.getDepth();
        float p = (float) ((backX + fx * s - wall.getXStart()) * nx + (backY + fy * s - wall.getYStart()) * ny);
        float q = (float) ((backX + fx * e - wall.getXStart()) * nx + (backY + fy * e - wall.getYStart()) * ny);
        return new float[] {Math.min(p, q), Math.max(p, q)};
    }

    /** Положение центра вдоль стены, доля длины. */
    private static float alongWall(HomeDoorOrWindow d, Wall wall) {
        double dx = wall.getXEnd() - wall.getXStart(), dy = wall.getYEnd() - wall.getYStart();
        double len2 = dx * dx + dy * dy;
        return (float) (((d.getX() - wall.getXStart()) * dx + (d.getY() - wall.getYStart()) * dy) / len2);
    }

    @Test
    void testFrameDoorFixtureCarriesMetadata() {
        CatalogDoorOrWindow d = frameDoor("Fixture Door", 14f, 0.5f, 0.1f, true, true);
        assertEquals(14f, d.getDepth(), EPS);
        assertEquals(0.5f, d.getWallThickness(), EPS);
        assertEquals(0.1f, d.getWallDistance(), EPS);
        assertTrue(d.isWidthDepthDeformable());
        assertTrue(d.isResizable());
        CatalogDoorOrWindow rigid = frameDoor("Rigid Fixture", 14f, 0.5f, 0.1f, false, true);
        assertFalse(rigid.isWidthDepthDeformable());
        assertTrue(rigid.isResizable());
        assertFalse(frameDoor("Fixed Fixture", 14f, 0.5f, 0.1f, true, false).isResizable());
    }

    @Test
    void testDeformableDoorFrameFillsWallThickness() {
        frameDoor("Deep Door", 14f, 0.5f, 0.1f, true, true);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        HomeDoorOrWindow d = place("Deep Door", wall, 0.5);

        assertEquals(20f, d.getDepth() * d.getWallThickness(), EPS, "frame is as thick as the wall");
        assertEquals(20f / 0.5f + 0.00075f, d.getDepth(), 1e-5f, "same margin as Sweet Home 3D");
        float[] band = frameBand(d, wall);
        assertEquals(-10f, band[0], EPS);
        assertEquals(10f, band[1], EPS);
        assertEquals(0.5f, alongWall(d, wall), 0.001f);
    }

    @Test
    void testFrameFitOnDiagonalWall() {
        frameDoor("Diag Door", 14f, 0.4f, 0.2f, true, true);
        Wall wall = addWall(100, 50, 400, 350, 30f);

        HomeDoorOrWindow d = place("Diag Door", wall, 0.3);

        float[] band = frameBand(d, wall);
        assertEquals(-15f, band[0], EPS);
        assertEquals(15f, band[1], EPS);
        assertEquals(0.3f, alongWall(d, wall), 0.001f);
    }

    @Test
    void testPlanDepthMatchesFittedDepth() {
        frameDoor("Plan Door", 14f, 0.5f, 0.1f, true, true);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        HomeDoorOrWindow d = place("Plan Door", wall, 0.5);

        assertEquals(d.getDepth(), d.getDepthInPlan(), EPS, "plan footprint uses the same depth as 3D");
    }

    @Test
    void testFittedDoorStaysBoundToWall() {
        frameDoor("Bound Door", 14f, 0.5f, 0.1f, true, true);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Bound Door");
        params.put("wallId", wall.getId());
        params.put("mirrored", true);
        params.put("elevation", 5.0);
        handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(((HomeDoorOrWindow) home.getFurniture().get(0)).isBoundToWall());
    }

    @Test
    void testRigidDoorKeepsDepthAndCentresFrame() {
        frameDoor("Rigid Door", 14f, 0.5f, 0.1f, false, true);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        HomeDoorOrWindow d = place("Rigid Door", wall, 0.5);

        assertEquals(14f, d.getDepth(), EPS, "a model that cannot stretch keeps its catalog depth");
        float[] band = frameBand(d, wall);
        assertEquals(0f, (band[0] + band[1]) / 2, EPS, "frame centred on the wall axis");
    }

    /** Центр коробки изделия (середина доли wallThickness глубины, от доли wallDistance). */
    private static double[] frameCentre(HomeDoorOrWindow d) {
        double fx = -Math.sin(d.getAngle()), fy = Math.cos(d.getAngle());
        double k = (d.getWallDistance() + d.getWallThickness() / 2) * d.getDepth() - d.getDepth() / 2;
        return new double[] {d.getX() + fx * k, d.getY() + fy * k};
    }

    private Wall addArcWall(float arcExtent) {
        Wall wall = addWall(0, 0, 500, 0, 20f);
        wall.setArcExtent(arcExtent);
        return wall;
    }

    @Test
    void testArcWallKeepsCatalogDepth() {
        // SH3D растягивает модель только в прямой стене
        frameDoor("Arc Door", 14f, 0.5f, 0.1f, true, true);

        HomeDoorOrWindow d = place("Arc Door", addArcWall((float) Math.PI / 2), 0.5);

        assertEquals(14f, d.getDepth(), EPS);
    }

    @Test
    void testArcWallFrameSitsOnTheArcAlongTheTangent() {
        frameDoor("Arc Door", 14f, 0.5f, 0.1f, true, true);
        for (float ext : new float[] {(float) Math.PI / 2, (float) -Math.PI / 2}) {
            for (double position : new double[] {0.0, 0.25, 0.5, 1.0}) {
                home.getFurniture().forEach(home::deletePieceOfFurniture);
                Wall wall = addArcWall(ext);
                double cx = wall.getXArcCircleCenter(), cy = wall.getYArcCircleCenter();
                double radius = Math.hypot(wall.getXStart() - cx, wall.getYStart() - cy);

                HomeDoorOrWindow d = place("Arc Door", wall, position);

                double[] c = frameCentre(d);
                String at = "ext " + ext + ", position " + position;
                assertEquals(radius, Math.hypot(c[0] - cx, c[1] - cy), EPS, "frame on the wall centre arc, " + at);
                double swept = Math.abs(Math.atan2(
                        (wall.getXStart() - cx) * (c[1] - cy) - (wall.getYStart() - cy) * (c[0] - cx),
                        (wall.getXStart() - cx) * (c[0] - cx) + (wall.getYStart() - cy) * (c[1] - cy)));
                assertEquals(Math.abs(ext) * position, swept, 0.001, "position is a fraction of the arc, " + at);
                double radial = ((c[0] - cx) * Math.cos(d.getAngle()) + (c[1] - cy) * Math.sin(d.getAngle())) / radius;
                assertEquals(0, radial, 0.001, "piece width runs along the tangent, " + at);
                home.deleteWall(wall);
            }
        }
    }

    @Test
    void testArcWallEndsMatchWallEnds() {
        // Обход дуги в обе стороны: при ошибке направления точка уходит на другую половину окружности
        frameDoor("Arc Door", 14f, 0.5f, 0.1f, true, true);
        for (float ext : new float[] {(float) Math.PI / 3, (float) -Math.PI / 3}) {
            Wall wall = addArcWall(ext);

            double[] start = frameCentre(place("Arc Door", wall, 0.0));
            double[] end = frameCentre(place("Arc Door", wall, 1.0));

            assertEquals(0, Math.hypot(start[0] - wall.getXStart(), start[1] - wall.getYStart()), EPS, "ext " + ext);
            assertEquals(0, Math.hypot(end[0] - wall.getXEnd(), end[1] - wall.getYEnd()), EPS, "ext " + ext);
        }
    }

    @Test
    void testNonResizableGenericDoorIsPlacedWithoutError() throws Exception {
        // Изделие без метаданных коробки: ветка «глубина не меньше стены» тоже не должна падать
        CatalogPieceOfFurniture rigid = new CatalogPieceOfFurniture(
                "Rigid Generic Door", null, null, 80f, 10f, 210f, false, false);
        setDoorOrWindow(rigid, true);
        Field resizable = CatalogPieceOfFurniture.class.getDeclaredField("resizable");
        resizable.setAccessible(true);
        resizable.set(rigid, false);
        assertFalse(rigid.isResizable());
        accessor.getFurnitureCatalog().add(accessor.getFurnitureCatalog().getCategories().get(0), rigid);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Rigid Generic Door");
        params.put("wallId", wall.getId());
        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk(), () -> String.valueOf(resp.getMessage()));
        assertEquals(10f, home.getFurniture().get(0).getDepth(), EPS);
    }

    @Test
    void testZeroFrameRatioFallsBackToWallThickness() {
        // Нет данных о коробке (доля 0): прежнее поведение, без деления на ноль
        frameDoor("Zero Ratio Door", 14f, 0f, 0f, true, true);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        HomeDoorOrWindow d = place("Zero Ratio Door", wall, 0.5);

        assertEquals(20f, d.getDepth(), EPS);
        assertEquals(20f, d.getDepthInPlan(), EPS);
        assertEquals(250f, d.getX(), EPS);
        assertEquals(0f, d.getY(), EPS);
    }

    @Test
    void testNonFiniteFrameMetadataFallsBackToWallThickness() {
        // Каталог из стороннего файла: нечисловые доли не должны давать NaN/Infinity в координатах
        frameDoor("NaN Distance Door", 14f, 0.5f, Float.NaN, true, true);
        frameDoor("Infinite Ratio Door", 14f, Float.POSITIVE_INFINITY, 0f, true, true);
        for (String name : new String[] {"NaN Distance Door", "Infinite Ratio Door"}) {
            Wall wall = addWall(0, 0, 500, 0, 20f);

            HomeDoorOrWindow d = place(name, wall, 0.5);

            assertEquals(250f, d.getX(), EPS, name);
            assertEquals(0f, d.getY(), EPS, name);
            assertEquals(20f, d.getDepth(), EPS, name);
        }
    }

    @Test
    void testResponseReportsFittedPositionOnDiagonalWall() {
        frameDoor("Diag Reported Door", 14f, 0.4f, 0.2f, true, true);
        Wall wall = addWall(100, 50, 400, 350, 30f);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Diag Reported Door");
        params.put("wallId", wall.getId());
        params.put("position", 0.3);
        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        HomePieceOfFurniture d = home.getFurniture().get(0);
        Map<?, ?> data = resp.getData();
        assertEquals(d.getX(), ((Number) data.get("x")).floatValue(), EPS);
        assertEquals(d.getY(), ((Number) data.get("y")).floatValue(), EPS);
        assertEquals(d.getDepth(), ((Number) data.get("depth")).floatValue(), EPS);
    }

    @Test
    void testArcWallPieceFacesLikeOnStraightWall() {
        // Как на прямой стене: ширина по ходу стены от начала к концу, лицо — вправо от хода
        frameDoor("Arc Facing Door", 14f, 0.5f, 0.1f, true, true);
        for (float ext : new float[] {(float) Math.PI / 2, (float) -Math.PI / 2}) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", "Arc Facing Door");
            params.put("wallId", addArcWall(ext).getId());
            Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

            HomePieceOfFurniture d = home.getFurniture().get(home.getFurniture().size() - 1);
            assertEquals(0, Math.sin(d.getAngle()), 0.001, "ext " + ext);
            assertEquals(1, Math.cos(d.getAngle()), 0.001, "ext " + ext);
            assertEquals(0.0, ((Number) resp.getData().get("angle")).doubleValue(), 0.001,
                    "angle reported as 0, not 360, ext " + ext);
        }
    }

    @Test
    void testSemicircleWallPlacesFrameInsideTheWall() {
        // Полукруг — частое значение дуги в SH3D; оба направления обхода приходят в конец стены
        frameDoor("Semi Door", 14f, 0.5f, 0.1f, true, true);
        for (float ext : new float[] {(float) Math.PI, (float) -Math.PI,
                (float) Math.toRadians(180), (float) Math.toRadians(-180)}) {
            for (double position : new double[] {0.25, 0.5, 0.75}) {
                Wall wall = addWall(251.28041f, 71.04035f, 80.02489f, 252.50995f, 20f);
                wall.setArcExtent(ext);

                double[] c = frameCentre(place("Semi Door", wall, position));

                assertTrue(wall.containsPoint((float) c[0], (float) c[1], false, 0.5f),
                        "frame centre inside the wall, ext " + ext + ", position " + position);
                home.deleteWall(wall);
            }
        }
    }

    @Test
    void testDoorIsBoundOnlyToStraightWall() {
        // SH3D: setBoundToWall(arcExtent == null || arcExtent == 0)
        frameDoor("Binding Door", 14f, 0.5f, 0.1f, true, true);

        assertTrue(place("Binding Door", addWall(0, 0, 500, 0, 20f), 0.5).isBoundToWall());
        assertFalse(place("Binding Door", addArcWall((float) Math.PI / 2), 0.5).isBoundToWall());
    }

    @Test
    void testMirroringNonResizableDoorIsRejectedWithoutPlacing() {
        frameDoor("Rigid Mirror Door", 14f, 0.5f, 0.1f, true, false);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Rigid Mirror Door");
        params.put("wallId", wall.getId());
        params.put("mirrored", true);
        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("mirrored"), resp.getMessage());
        assertTrue(home.getFurniture().isEmpty(), "nothing half-placed");
    }

    @Test
    void testNonResizableDoorIsPlacedWithoutError() {
        frameDoor("Fixed Size Door", 14f, 0.5f, 0.1f, true, false);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        HomeDoorOrWindow d = place("Fixed Size Door", wall, 0.5);

        assertEquals(14f, d.getDepth(), EPS);
    }

    @Test
    void testResponseReportsFittedDepthAndPosition() {
        frameDoor("Reported Door", 14f, 0.5f, 0.1f, true, true);
        Wall wall = addWall(0, 0, 500, 0, 20f);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Reported Door");
        params.put("wallId", wall.getId());
        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        HomePieceOfFurniture d = home.getFurniture().get(0);
        Map<?, ?> data = (Map<?, ?>) resp.getData();
        assertEquals(d.getDepth(), ((Number) data.get("depth")).floatValue(), EPS);
        assertEquals(d.getY(), ((Number) data.get("y")).floatValue(), EPS);
    }

    // --- Sash parameters ---

    @Test
    void testSashPresetOverridesCatalogSashes() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sash Door");
        params.put("wallId", wall.getId());
        params.put("sashPreset", "double");

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, ((Number) resp.getData().get("sashes")).intValue());
        assertEquals(2, ((HomeDoorOrWindow) home.getFurniture().get(0)).getSashes().length);
    }

    @Test
    void testUnknownSashPresetIsRejectedAndNothingPlaced() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sash Door");
        params.put("wallId", wall.getId());
        params.put("sashPreset", "revolving");

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertFalse(resp.isOk());
        assertTrue(home.getFurniture().isEmpty());
    }

    @Test
    void testSashCountReportedForPlacedDoor() {
        Wall wall = addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sash Door");
        params.put("wallId", wall.getId());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertEquals(1, ((Number) resp.getData().get("sashes")).intValue());
    }

    @Test
    void testInvalidSashListIsRejectedAndNothingPlaced() {
        Wall wall = addWall(0, 0, 500, 0);
        Map<String, Object> sash = new LinkedHashMap<>();
        sash.put("width", 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sash Door");
        params.put("wallId", wall.getId());
        params.put("sashes", List.of(sash));

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("sashes[0].width"), resp.getMessage());
        assertTrue(home.getFurniture().isEmpty());
    }

    @Test
    void testSashPresetHingedOnFrameFrontAfterFitInWall() {
        Wall wall = addWall(0, 0, 500, 0, 20f);
        frameDoor("Frame Sash Door", 14f, 0.5f, 0.1f, true, true);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Frame Sash Door");
        params.put("wallId", wall.getId());
        params.put("sashPreset", "single_right");

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk(), () -> String.valueOf(resp.getMessage()));
        HomeDoorOrWindow d = (HomeDoorOrWindow) home.getFurniture().get(0);
        Sash s = d.getSashes()[0];
        // Петля — на лицевой грани коробки, как у дверей каталога SH3D; створка открывается к лицу
        assertEquals(0.6f, s.getYAxis(), EPS);
        assertEquals(1f, s.getXAxis(), EPS);
        assertEquals(1.0, -Math.sin(s.getEndAngle()), EPS);
        // Дуга — четверть круга: правая створка 180 → 270, а не 180 → −90 (размах 270°)
        assertEquals(90.0, Math.toDegrees(s.getEndAngle() - s.getStartAngle()), 0.01);
        assertTrue(d.isBoundToWall(), "sashes must not undo wall binding");
    }

    @Test
    void testSashesOnCatalogItemWithoutDoorDataRejectedAndNothingPlaced() {
        Wall wall = addWall(0, 0, 500, 0);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");   // флаг двери, но не CatalogDoorOrWindow
        params.put("wallId", wall.getId());
        params.put("sashPreset", "single_left");

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("doors and windows") && resp.getMessage().contains("Front Door"),
                resp.getMessage());
        assertTrue(home.getFurniture().isEmpty());
    }

    @Test
    void testEmptySashListRemovesCatalogSashes() {
        Wall wall = addWall(0, 0, 500, 0);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sash Door");
        params.put("wallId", wall.getId());
        params.put("sashes", List.of());

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk(), () -> String.valueOf(resp.getMessage()));
        assertEquals(0, ((HomeDoorOrWindow) home.getFurniture().get(0)).getSashes().length);
        assertEquals(0, ((Number) resp.getData().get("sashes")).intValue());
    }

    @Test
    void testUnknownPresetNextToSashListIsRejected() {
        Wall wall = addWall(0, 0, 500, 0);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sash Door");
        params.put("wallId", wall.getId());
        params.put("sashes", List.of());
        params.put("sashPreset", "bogus");

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("bogus"), resp.getMessage());
        assertTrue(home.getFurniture().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaDeclaresSashParameters() {
        Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");
        assertEquals(List.of("single_left", "single_right", "double", "none"),
                ((Map<String, Object>) props.get("sashPreset")).get("enum"));
        assertEquals("array", ((Map<String, Object>) props.get("sashes")).get("type"));
    }
}
