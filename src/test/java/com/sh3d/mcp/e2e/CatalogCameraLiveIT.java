package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Команды чтения: категории и текстуры каталога, сохранённые виды камеры. */
class CatalogCameraLiveIT extends LiveEditingTestBase {

    @Test
    void listCategoriesReportsCountsThatAgreeWithTheFurnitureCatalog() {
        Map<String, Object> reply = mcp.call("list_categories");
        List<Map<String, Object>> categories = objects(reply, "categories");
        List<Map<String, Object>> furniture = objects(mcp.call("list_furniture_catalog"), "furniture");
        assertFalse(categories.isEmpty());
        Set<String> expectedNames = furniture.stream().map(p -> str(p, "category")).collect(Collectors.toSet());
        assertEquals(expectedNames, categories.stream().map(c -> str(c, "name")).collect(Collectors.toSet()));
        assertEquals(expectedNames.size(), categories.size(), "categories are unique");
        int sum = 0;
        for (Map<String, Object> category : categories) {
            long count = furniture.stream().filter(p -> category.get("name").equals(p.get("category"))).count();
            assertEquals(count, num(category, "count"), 0.0, "count for " + category.get("name"));
            sum += (int) num(category, "count");
        }
        assertEquals(furniture.size(), sum);
        assertEquals(sum, num(reply, "totalItems"), 0.0);

        // По карточке неизвестные ключи команды без параметров игнорируются с предупреждением.
        Map<String, Object> ignored = mcp.call("list_categories", "e2eUnknown", "ignored");
        assertEquals(categories, objects(ignored, "categories"), "unknown key must not cause a refusal");
    }

    @Test
    void listTexturesCanBeFilteredByNameAndCategory() {
        List<Map<String, Object>> all = objects(mcp.call("list_textures_catalog"), "textures");
        assertFalse(all.isEmpty());
        for (Map<String, Object> texture : all) {
            str(texture, "name");
            str(texture, "category");
            assertTrue(num(texture, "width") > 0);
            assertTrue(num(texture, "height") > 0);
            assertTrue(texture.containsKey("creator"), "creator is promised by tools/list: " + texture);
        }
        Map<String, Object> item = all.get(0);
        String name = str(item, "name");
        String category = str(item, "category");
        List<Map<String, Object>> filtered = objects(mcp.call("list_textures_catalog",
                "query", name, "category", category), "textures");
        assertFalse(filtered.isEmpty(), "a texture from the catalog must be found by its own name/category");
        for (Map<String, Object> texture : filtered) {
            assertTrue(str(texture, "name").toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)));
            assertEquals(category, texture.get("category"));
        }
        assertTrue(filtered.contains(item));
    }

    @Test
    @Disabled("Not in 1.2.0: rejecting non-string query values; the schema types query as a string, but all catalog commands accept non-string values")
    void listTexturesRejectsANonStringQueryWithoutChangingTheScene() {
        createRoom(0, 0, "E2E kept");
        // Уточнение: в tools/list у query задан "type": "string", но query:123 принимается как строка поиска.
        assertRejectedWithoutChange("list_textures_catalog", McpLiveClient.args("query", 123));
    }

    @Test
    void getCamerasReturnsBookmarkedPositionsAndDegreesAndIgnoresUnknownKeys() {
        assertTrue(objects(mcp.call("get_cameras"), "storedCameras").isEmpty());
        saveAndReadHome("pristine-camera");
        try {
            mcp.call("set_camera", "mode", "observer", "x", 120, "y", 700, "z", 170,
                    "yaw", 200, "pitch", 15, "fov", 60);
            mcp.call("store_camera", "name", "E2E first view");
            mcp.call("set_camera", "mode", "observer", "x", 350, "y", 400, "z", 210,
                    "yaw", 90, "pitch", 25, "fov", 45);
            mcp.call("store_camera", "name", "E2E second view");

            Map<String, Object> reply = mcp.call("get_cameras");
            List<Map<String, Object>> cameras = objects(reply, "storedCameras");
            assertEquals(2, cameras.size());
            assertEquals(2, num(reply, "storedCameraCount"), 0.0);
            Map<String, Object> first = cameraNamed(cameras, "E2E first view");
            Map<String, Object> second = cameraNamed(cameras, "E2E second view");
            assertNotNull(first.get("id"), "each bookmarked camera has an id");
            assertNotNull(second.get("id"));
            assertCamera(first, 120, 700, 170, 200, 15, 60);
            assertCamera(second, 350, 400, 210, 90, 25, 45);

            Map<String, Object> ignored = mcp.call("get_cameras", "e2eUnknown", true);
            assertEquals(cameras, objects(ignored, "storedCameras"), "unknown key must still succeed");
            Home home = saveAndReadHome("bookmarked-cameras");
            assertEquals(2, home.getStoredCameras().size(), "bookmarks really belong to the saved home");
        } finally {
            mcp.call("load_home", "filePath", workDir.resolve("pristine-camera.sh3d").toString());
        }
    }

    private static Map<String, Object> cameraNamed(List<Map<String, Object>> cameras, String name) {
        return cameras.stream().filter(c -> name.equals(c.get("name"))).findFirst()
                .orElseThrow(() -> new AssertionError("No camera named " + name + " in " + cameras));
    }

    private static void assertCamera(Map<String, Object> camera, double x, double y, double z,
                                     double yaw, double pitch, double fov) {
        assertEquals(x, num(camera, "x"), RESPONSE_EPS);
        assertEquals(y, num(camera, "y"), RESPONSE_EPS);
        assertEquals(z, num(camera, "z"), RESPONSE_EPS);
        assertEquals(yaw, num(camera, "yaw_degrees"), RESPONSE_EPS);
        assertEquals(pitch, num(camera, "pitch_degrees"), RESPONSE_EPS);
        assertEquals(fov, num(camera, "fov_degrees"), RESPONSE_EPS);
    }
}
