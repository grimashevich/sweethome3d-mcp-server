package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListTexturesCatalogHandlerTest {

    private ListTexturesCatalogHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new ListTexturesCatalogHandler();

        TexturesCatalog catalog = new TexturesCatalog();

        TexturesCategory walls = new TexturesCategory("Walls");
        TexturesCategory floors = new TexturesCategory("Floors");

        CatalogTexture brick = new CatalogTexture("Red Brick", null, 20f, 10f);
        CatalogTexture plaster = new CatalogTexture("White Plaster", null, 50f, 50f);
        CatalogTexture parquet = new CatalogTexture(
                "parquet1", "Oak Parquet", null, 40f, 40f, "eTeks");

        catalog.add(walls, brick);
        catalog.add(walls, plaster);
        catalog.add(floors, parquet);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getTexturesCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(new Home(), prefs);
    }

    @Test
    void testListAllWithoutFilters() {
        Request req = new Request("list_textures_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(3, textures.size());
    }

    @Test
    void testFilterByQuery() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "brick");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());

        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals("Red Brick", item.get("name"));
    }

    @Test
    void testFilterByCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "floors");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());

        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals("Oak Parquet", item.get("name"));
        assertEquals("Floors", item.get("category"));
    }

    @Test
    void testFilterByQueryAndCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "plaster");
        params.put("category", "walls");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());

        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals("White Plaster", item.get("name"));
    }

    @Test
    void testCaseInsensitiveQuery() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "RED BRICK");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());

        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals("Red Brick", item.get("name"));
    }

    @Test
    void testCaseInsensitiveCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "WALLS");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(2, textures.size());
    }

    @Test
    void testNoMatchReturnsEmptyList() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "nonexistent");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertTrue(textures.isEmpty());
    }

    @Test
    void testCategoryNoMatchReturnsEmptyList() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "ceilings");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertTrue(textures.isEmpty());
    }

    @Test
    void testResponseContainsDimensions() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Red Brick");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());

        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals(20.0, (double) item.get("width"), 0.01);
        assertEquals(10.0, (double) item.get("height"), 0.01);
    }

    @Test
    void testResponseContainsCreator() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Oak Parquet");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());

        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals("eTeks", item.get("creator"));
    }

    @Test
    void testCreatorOmittedWhenNull() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Red Brick");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertFalse(item.containsKey("creator"));
    }

    @Test
    void testPartialQueryMatch() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "quet");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());

        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals("Oak Parquet", item.get("name"));
    }

    @Test
    void testNullParams() {
        Request req = new Request("list_textures_catalog", null);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(3, textures.size());
    }

    @Test
    void testEmptyStringQueryReturnsAll() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(3, textures.size());
    }

    @Test
    void testEmptyStringCategoryReturnsAll() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(3, textures.size());
    }

    @Test
    void testCatalogIdInOutputWhenPresent() {
        // Oak Parquet was created with id "parquet1" in setUp
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Oak Parquet");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());
        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals("parquet1", item.get("catalogId"));
    }

    @Test
    void testCatalogIdNotInOutputWhenNull() {
        // Red Brick was created without id
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Red Brick");

        Request req = new Request("list_textures_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());
        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertNull(item.get("catalogId"));
    }

    @Test
    void testNullNamesInCatalogAreSkipped() {
        CatalogTexture normalTexture = mock(CatalogTexture.class);
        when(normalTexture.getName()).thenReturn("Marble");
        when(normalTexture.getWidth()).thenReturn(30f);
        when(normalTexture.getHeight()).thenReturn(30f);
        when(normalTexture.getCreator()).thenReturn(null);

        CatalogTexture nullNameTexture = mock(CatalogTexture.class);
        when(nullNameTexture.getName()).thenReturn(null);

        TexturesCategory normalCat = mock(TexturesCategory.class);
        when(normalCat.getName()).thenReturn("Stone");
        when(normalCat.getTextures()).thenReturn(List.of(normalTexture, nullNameTexture));

        TexturesCategory nullCat = mock(TexturesCategory.class);
        when(nullCat.getName()).thenReturn(null);
        CatalogTexture anotherTexture = mock(CatalogTexture.class);
        when(anotherTexture.getName()).thenReturn("Granite");
        when(nullCat.getTextures()).thenReturn(List.of(anotherTexture));

        TexturesCatalog catalog = mock(TexturesCatalog.class);
        when(catalog.getCategories()).thenReturn(List.of(normalCat, nullCat));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getTexturesCatalog()).thenReturn(catalog);
        HomeAccessor testAccessor = new HomeAccessor(new Home(), prefs);

        Request req = new Request("list_textures_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, testAccessor);

        assertTrue(resp.isOk());
        List<?> textures = (List<?>) resp.getData().get("textures");
        assertEquals(1, textures.size());

        Map<?, ?> item = (Map<?, ?>) textures.get(0);
        assertEquals("Marble", item.get("name"));
        assertEquals("Stone", item.get("category"));
    }
}
