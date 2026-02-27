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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListCategoriesHandlerTest {

    private ListCategoriesHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new ListCategoriesHandler();

        FurnitureCatalog catalog = new FurnitureCatalog();

        FurnitureCategory livingRoom = new FurnitureCategory("Living Room");
        FurnitureCategory bedroom = new FurnitureCategory("Bedroom");

        CatalogPieceOfFurniture table = new CatalogPieceOfFurniture(
                "Dining Table", null, null, 120f, 80f, 75f, true, false);
        CatalogPieceOfFurniture chair = new CatalogPieceOfFurniture(
                "Office Chair", null, null, 50f, 50f, 90f, true, false);
        CatalogPieceOfFurniture bed = new CatalogPieceOfFurniture(
                "Double Bed", null, null, 160f, 200f, 50f, true, false);

        catalog.add(livingRoom, table);
        catalog.add(livingRoom, chair);
        catalog.add(bedroom, bed);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(new Home(), prefs);
    }

    // ==================== Descriptor ====================

    @Test
    void testDescriptorName() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
    }

    @Test
    void testDescriptorSchema() {
        Map<String, Object> schema = handler.getSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertNotNull(schema.get("properties"));
    }

    @Test
    void testDescriptorToolNameIsNull() {
        // Default getToolName() returns null — action name from registry is used
        assertNull(handler.getToolName());
    }

    // ==================== Execute ====================

    @Test
    void testListCategoriesReturnsOk() {
        Request req = new Request("list_categories", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
    }

    @Test
    void testListCategoriesReturnsCorrectCount() {
        Request req = new Request("list_categories", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        List<?> categories = (List<?>) resp.getData().get("categories");
        assertEquals(2, categories.size());
    }

    @Test
    void testCategoryContainsNameAndCount() {
        Request req = new Request("list_categories", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        List<?> categories = (List<?>) resp.getData().get("categories");
        Map<?, ?> first = (Map<?, ?>) categories.get(0);

        assertNotNull(first.get("name"));
        assertNotNull(first.get("count"));
    }

    @Test
    void testCategoryItemCounts() {
        Request req = new Request("list_categories", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        List<?> categories = (List<?>) resp.getData().get("categories");

        // Find each category and verify count
        int bedroomCount = -1;
        int livingRoomCount = -1;
        for (Object cat : categories) {
            Map<?, ?> m = (Map<?, ?>) cat;
            if ("Bedroom".equals(m.get("name"))) {
                bedroomCount = ((Number) m.get("count")).intValue();
            } else if ("Living Room".equals(m.get("name"))) {
                livingRoomCount = ((Number) m.get("count")).intValue();
            }
        }

        assertEquals(1, bedroomCount, "Bedroom should have 1 item");
        assertEquals(2, livingRoomCount, "Living Room should have 2 items");
    }

    @Test
    void testTotalItemsCount() {
        Request req = new Request("list_categories", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        int totalItems = ((Number) resp.getData().get("totalItems")).intValue();
        assertEquals(3, totalItems);
    }

    // ==================== Empty catalog ====================

    @Test
    void testEmptyCatalogReturnsEmptyList() {
        FurnitureCatalog emptyCatalog = new FurnitureCatalog();
        UserPreferences emptyPrefs = mock(UserPreferences.class);
        when(emptyPrefs.getFurnitureCatalog()).thenReturn(emptyCatalog);
        HomeAccessor emptyAccessor = new HomeAccessor(new Home(), emptyPrefs);

        Request req = new Request("list_categories", Collections.emptyMap());
        Response resp = handler.execute(req, emptyAccessor);

        assertTrue(resp.isOk());
        List<?> categories = (List<?>) resp.getData().get("categories");
        assertTrue(categories.isEmpty());
        assertEquals(0, ((Number) resp.getData().get("totalItems")).intValue());
    }

    // ==================== Null category name is skipped ====================

    @Test
    void testNullCategoryNameIsSkipped() {
        FurnitureCategory normalCat = mock(FurnitureCategory.class);
        when(normalCat.getName()).thenReturn("Kitchen");
        CatalogPieceOfFurniture piece = mock(CatalogPieceOfFurniture.class);
        when(piece.getName()).thenReturn("Sink");
        when(normalCat.getFurniture()).thenReturn(List.of(piece));

        FurnitureCategory nullCat = mock(FurnitureCategory.class);
        when(nullCat.getName()).thenReturn(null);
        CatalogPieceOfFurniture piece2 = mock(CatalogPieceOfFurniture.class);
        when(piece2.getName()).thenReturn("Oven");
        when(nullCat.getFurniture()).thenReturn(List.of(piece2));

        FurnitureCatalog catalog = mock(FurnitureCatalog.class);
        when(catalog.getCategories()).thenReturn(List.of(normalCat, nullCat));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor testAccessor = new HomeAccessor(new Home(), prefs);

        Request req = new Request("list_categories", Collections.emptyMap());
        Response resp = handler.execute(req, testAccessor);

        assertTrue(resp.isOk());
        List<?> categories = (List<?>) resp.getData().get("categories");
        assertEquals(1, categories.size());

        Map<?, ?> cat = (Map<?, ?>) categories.get(0);
        assertEquals("Kitchen", cat.get("name"));
        assertEquals(1, ((Number) cat.get("count")).intValue());
    }

    // ==================== Null params ====================

    @Test
    void testNullParamsExecutesSuccessfully() {
        Request req = new Request("list_categories", null);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> categories = (List<?>) resp.getData().get("categories");
        assertEquals(2, categories.size());
    }
}
