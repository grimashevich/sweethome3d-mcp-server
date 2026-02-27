package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListFurnitureCatalogHandlerTest {

    private ListFurnitureCatalogHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() throws Exception {
        handler = new ListFurnitureCatalogHandler();

        FurnitureCatalog catalog = new FurnitureCatalog();

        FurnitureCategory livingRoom = new FurnitureCategory("Living Room");
        FurnitureCategory bedroom = new FurnitureCategory("Bedroom");
        FurnitureCategory doors = new FurnitureCategory("Doors and windows");

        CatalogPieceOfFurniture table = new CatalogPieceOfFurniture(
                "Dining Table", null, null, 120f, 80f, 75f, true, false);
        CatalogPieceOfFurniture chair = new CatalogPieceOfFurniture(
                "Office Chair", null, null, 50f, 50f, 90f, true, false);
        CatalogPieceOfFurniture bed = new CatalogPieceOfFurniture(
                "Double Bed", null, null, 160f, 200f, 50f, true, false);
        CatalogPieceOfFurniture frontDoor = new CatalogPieceOfFurniture(
                "Front Door", null, null, 87f, 10f, 210f, false, false);
        setDoorOrWindow(frontDoor, true);

        catalog.add(livingRoom, table);
        catalog.add(livingRoom, chair);
        catalog.add(bedroom, bed);
        catalog.add(doors, frontDoor);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(new Home(), prefs);
    }

    @Test
    void testListAllWithoutFilters() {
        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(4, furniture.size());
    }

    @Test
    void testFilterByQuery() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "table");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Dining Table", item.get("name"));
    }

    @Test
    void testFilterByCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "bedroom");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Double Bed", item.get("name"));
        assertEquals("Bedroom", item.get("category"));
    }

    @Test
    void testFilterByQueryAndCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "chair");
        params.put("category", "living");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Office Chair", item.get("name"));
    }

    @Test
    void testCaseInsensitiveQuery() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "DINING");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Dining Table", item.get("name"));
    }

    @Test
    void testCaseInsensitiveCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "LIVING ROOM");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(2, furniture.size());
    }

    @Test
    void testNoMatchReturnsEmptyList() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "nonexistent");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertTrue(furniture.isEmpty());
    }

    @Test
    void testCategoryNoMatchReturnsEmptyList() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "kitchen");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertTrue(furniture.isEmpty());
    }

    @Test
    void testResponseContainsDimensions() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Dining Table");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals(120.0, (double) item.get("width"), 0.01);
        assertEquals(80.0, (double) item.get("depth"), 0.01);
        assertEquals(75.0, (double) item.get("height"), 0.01);
    }

    @Test
    void testResponseContainsCategoryField() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Double Bed");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Bedroom", item.get("category"));
    }

    @Test
    void testPartialQueryMatch() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "ble");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(2, furniture.size());
    }

    @Test
    void testNullParams() {
        Request req = new Request("list_furniture_catalog", null);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(4, furniture.size());
    }

    @Test
    void testEmptyStringQueryReturnsAll() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(4, furniture.size());
    }

    @Test
    void testEmptyStringCategoryReturnsAll() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(4, furniture.size());
    }

    @Test
    void testCatalogIdInOutputWhenPresent() {
        // Create catalog with piece that has an ID
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory cat = new FurnitureCategory("Test");
        CatalogPieceOfFurniture piece = new CatalogPieceOfFurniture(
                "table-001", "Test Table", null, null, null,
                100f, 80f, 75f, 0f, true, null, null, true, null, null);
        catalog.add(cat, piece);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor testAccessor = new HomeAccessor(new Home(), prefs);

        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, testAccessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("table-001", item.get("catalogId"));
    }

    @Test
    void testCatalogIdNotInOutputWhenNull() {
        // Standard catalog from setUp has no IDs
        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertFalse(furniture.isEmpty());
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertNull(item.get("catalogId"));
    }

    // ==================== isDoorOrWindow field ====================

    @Test
    void testIsDoorOrWindowFieldPresent() {
        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        // Every item must have isDoorOrWindow
        for (Object obj : furniture) {
            Map<?, ?> item = (Map<?, ?>) obj;
            assertNotNull(item.get("isDoorOrWindow"), "isDoorOrWindow field must be present");
            assertInstanceOf(Boolean.class, item.get("isDoorOrWindow"));
        }
    }

    @Test
    void testIsDoorOrWindowTrueForDoor() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Front Door");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals(true, item.get("isDoorOrWindow"));
    }

    @Test
    void testIsDoorOrWindowFalseForFurniture() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Dining Table");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals(false, item.get("isDoorOrWindow"));
    }

    // ==================== type filter ====================

    @Test
    void testTypeFilterFurnitureOnly() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "furniture");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(3, furniture.size());
        for (Object obj : furniture) {
            Map<?, ?> item = (Map<?, ?>) obj;
            assertEquals(false, item.get("isDoorOrWindow"));
        }
    }

    @Test
    void testTypeFilterDoorOrWindowOnly() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "doorOrWindow");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Front Door", item.get("name"));
        assertEquals(true, item.get("isDoorOrWindow"));
    }

    @Test
    void testTypeFilterAllReturnsEverything() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "all");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(4, furniture.size());
    }

    @Test
    void testTypeFilterCaseInsensitive() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "DOORORWINDOW");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());
    }

    @Test
    void testTypeFilterInvalidReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "invalid");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertFalse(resp.isOk());
        assertTrue(resp.getMessage().contains("'type'"));
    }

    @Test
    void testTypeFilterCombinedWithQuery() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "door");
        params.put("type", "doorOrWindow");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Front Door", item.get("name"));
    }

    @Test
    void testTypeFilterCombinedWithCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "Living");
        params.put("type", "furniture");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(2, furniture.size());
    }

    // ==================== null names handling ====================

    @Test
    void testNullNamesInCatalogAreSkipped() {
        CatalogPieceOfFurniture normalPiece = mock(CatalogPieceOfFurniture.class);
        when(normalPiece.getName()).thenReturn("Sink");
        when(normalPiece.getWidth()).thenReturn(60f);
        when(normalPiece.getDepth()).thenReturn(50f);
        when(normalPiece.getHeight()).thenReturn(85f);

        CatalogPieceOfFurniture nullNamePiece = mock(CatalogPieceOfFurniture.class);
        when(nullNamePiece.getName()).thenReturn(null);

        FurnitureCategory normalCat = mock(FurnitureCategory.class);
        when(normalCat.getName()).thenReturn("Kitchen");
        when(normalCat.getFurniture()).thenReturn(List.of(normalPiece, nullNamePiece));

        FurnitureCategory nullCat = mock(FurnitureCategory.class);
        when(nullCat.getName()).thenReturn(null);
        CatalogPieceOfFurniture anotherPiece = mock(CatalogPieceOfFurniture.class);
        when(anotherPiece.getName()).thenReturn("Oven");
        when(nullCat.getFurniture()).thenReturn(List.of(anotherPiece));

        FurnitureCatalog catalog = mock(FurnitureCatalog.class);
        when(catalog.getCategories()).thenReturn(List.of(normalCat, nullCat));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor testAccessor = new HomeAccessor(new Home(), prefs);

        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, testAccessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Sink", item.get("name"));
        assertEquals("Kitchen", item.get("category"));
    }

    // ==================== Helpers ====================

    private static void setDoorOrWindow(CatalogPieceOfFurniture piece, boolean value)
            throws Exception {
        Field field = CatalogPieceOfFurniture.class.getDeclaredField("doorOrWindow");
        field.setAccessible(true);
        field.set(piece, value);
    }
}
