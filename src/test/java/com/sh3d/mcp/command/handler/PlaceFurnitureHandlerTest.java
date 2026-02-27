package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceFurnitureHandlerTest {

    private PlaceFurnitureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new PlaceFurnitureHandler();
        home = new Home();

        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory category = new FurnitureCategory("Living Room");

        CatalogPieceOfFurniture table = new CatalogPieceOfFurniture(
                "Dining Table", null, null, 120f, 80f, 75f, true, false);
        CatalogPieceOfFurniture chair = new CatalogPieceOfFurniture(
                "Office Chair", null, null, 50f, 50f, 90f, true, false);

        catalog.add(category, table);
        catalog.add(category, chair);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(home, prefs);
    }

    @Test
    void testPlaceFurnitureSuccess() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 250.0);
        params.put("y", 150.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals("Dining Table", resp.getData().get("name"));
        assertEquals(250.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(150.0, (double) resp.getData().get("y"), 0.01);
    }

    @Test
    void testFurnitureAddedToHome() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);

        Request req = new Request("place_furniture", params);
        handler.execute(req, accessor);

        List<HomePieceOfFurniture> furniture = home.getFurniture();
        assertEquals(1, furniture.size());
        assertEquals("Dining Table", furniture.get(0).getName());
    }

    @Test
    void testCaseInsensitiveSearch() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "dining table");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals("Dining Table", resp.getData().get("name"));
    }

    @Test
    void testPartialNameMatch() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Chair");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals("Office Chair", resp.getData().get("name"));
    }

    @Test
    void testFurnitureNotFound() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sofa");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Furniture not found"));
        assertTrue(resp.getMessage().contains("Sofa"));
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testMissingNameReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testEmptyNameReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testBlankNameReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "   ");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testMissingXReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("x"));
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testMissingYReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("y"));
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testDefaultAngleIsZero() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(0.0, (double) resp.getData().get("angle"), 0.01);
    }

    @Test
    void testCustomAngleConvertedToRadians() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);
        params.put("angle", 90.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(90.0, (double) resp.getData().get("angle"), 0.01);

        HomePieceOfFurniture placed = home.getFurniture().get(0);
        assertEquals(Math.toRadians(90), placed.getAngle(), 0.01);
    }

    @Test
    void testResponseContainsDimensions() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(120.0, (double) resp.getData().get("width"), 0.01);
        assertEquals(80.0, (double) resp.getData().get("depth"), 0.01);
        assertEquals(75.0, (double) resp.getData().get("height"), 0.01);
    }

    @Test
    void testPlaceMultiplePieces() {
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("name", "Dining Table");
        params1.put("x", 100.0);
        params1.put("y", 100.0);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("name", "Office Chair");
        params2.put("x", 200.0);
        params2.put("y", 200.0);

        handler.execute(new Request("place_furniture", params1), accessor);
        handler.execute(new Request("place_furniture", params2), accessor);

        assertEquals(2, home.getFurniture().size());
    }

    // ==================== Exact match priority ====================

    @Test
    void testExactMatchPreferredOverSubstring() {
        // Add "Table" which is exact match for "Table", while "Dining Table" is substring
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory cat = new FurnitureCategory("Living Room");
        catalog.add(cat, new CatalogPieceOfFurniture(
                "Dining Table", null, null, 120f, 80f, 75f, true, false));
        catalog.add(cat, new CatalogPieceOfFurniture(
                "Table", null, null, 80f, 60f, 70f, true, false));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor localAccessor = new HomeAccessor(new Home(), prefs);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Table");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Response resp = handler.execute(new Request("place_furniture", params), localAccessor);

        assertTrue(resp.isOk());
        assertEquals("Table", resp.getData().get("name"));
    }

    // ==================== catalogId ====================

    @Test
    void testCatalogIdSuccess() {
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory cat = new FurnitureCategory("Living Room");
        catalog.add(cat, new CatalogPieceOfFurniture(
                "table-001", "Dining Table", null, null, null,
                120f, 80f, 75f, 0f, true, null, null, true, null, null));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor localAccessor = new HomeAccessor(new Home(), prefs);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("catalogId", "table-001");
        params.put("x", 100.0);
        params.put("y", 200.0);

        Response resp = handler.execute(new Request("place_furniture", params), localAccessor);

        assertTrue(resp.isOk());
        assertEquals("Dining Table", resp.getData().get("name"));
    }

    @Test
    void testCatalogIdNotFound() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("catalogId", "nonexistent");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Response resp = handler.execute(new Request("place_furniture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("nonexistent"));
    }

    @Test
    void testCatalogIdWithoutName() {
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory cat = new FurnitureCategory("Living Room");
        catalog.add(cat, new CatalogPieceOfFurniture(
                "chair-001", "Office Chair", null, null, null,
                50f, 50f, 90f, 0f, true, null, null, true, null, null));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor localAccessor = new HomeAccessor(new Home(), prefs);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("catalogId", "chair-001");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Response resp = handler.execute(new Request("place_furniture", params), localAccessor);

        assertTrue(resp.isOk());
        assertEquals("Office Chair", resp.getData().get("name"));
    }

    // ==================== returned id ====================

    @Test
    void testResponseContainsId() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);

        Response resp = handler.execute(new Request("place_furniture", params), accessor);

        assertTrue(resp.isOk());
        Object id = resp.getData().get("id");
        assertNotNull(id, "Response must contain id");
        assertInstanceOf(String.class, id, "ID must be a string");
        assertFalse(((String) id).isEmpty(), "ID must not be empty");
    }

    @Test
    void testIdMatchesPlacedFurniture() {
        // Размещаем два предмета мебели
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("name", "Dining Table");
        params1.put("x", 100.0);
        params1.put("y", 100.0);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("name", "Office Chair");
        params2.put("x", 200.0);
        params2.put("y", 200.0);

        Response resp1 = handler.execute(new Request("place_furniture", params1), accessor);
        Response resp2 = handler.execute(new Request("place_furniture", params2), accessor);

        String id1 = (String) resp1.getData().get("id");
        String id2 = (String) resp2.getData().get("id");

        assertNotEquals(id1, id2, "IDs must be unique");

        // Verify IDs match actual furniture objects
        HomePieceOfFurniture piece1 = home.getFurniture().stream()
                .filter(p -> p.getId().equals(id1)).findFirst().orElse(null);
        HomePieceOfFurniture piece2 = home.getFurniture().stream()
                .filter(p -> p.getId().equals(id2)).findFirst().orElse(null);
        assertNotNull(piece1);
        assertNotNull(piece2);
        assertEquals("Dining Table", piece1.getName());
        assertEquals("Office Chair", piece2.getName());
    }

    @Test
    void testIdIsStableAcrossAdditions() {
        // Добавляем мебель до вызова place_furniture
        HomePieceOfFurniture existing = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(
                        "Existing Sofa", null, null, 200f, 80f, 85f, true, false));
        home.addPieceOfFurniture(existing);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);

        Response resp = handler.execute(new Request("place_furniture", params), accessor);

        assertTrue(resp.isOk());
        String id = (String) resp.getData().get("id");
        assertNotEquals(existing.getId(), id, "New furniture must have different ID");

        // Verify the returned ID matches the placed furniture
        HomePieceOfFurniture placed = home.getFurniture().stream()
                .filter(p -> p.getId().equals(id)).findFirst().orElse(null);
        assertNotNull(placed);
        assertEquals("Dining Table", placed.getName());
    }

    // ==================== elevation ====================

    @Test
    void testElevationApplied() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);
        params.put("elevation", 150.0);

        Response resp = handler.execute(new Request("place_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(150.0, (double) resp.getData().get("elevation"), 0.01);
        assertEquals(150f, home.getFurniture().get(0).getElevation(), 0.01f);
    }

    @Test
    void testDefaultElevationIsZero() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);

        Response resp = handler.execute(new Request("place_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.0, (double) resp.getData().get("elevation"), 0.01);
        assertEquals(0f, home.getFurniture().get(0).getElevation(), 0.01f);
    }

    @Test
    void testResponseContainsElevation() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Response resp = handler.execute(new Request("place_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertTrue(resp.getData().containsKey("elevation"), "Response must contain elevation");
    }

    @Test
    void testBothNameAndCatalogIdMissingReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);

        Response resp = handler.execute(new Request("place_furniture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
        assertTrue(resp.getMessage().contains("catalogId"));
    }
}
