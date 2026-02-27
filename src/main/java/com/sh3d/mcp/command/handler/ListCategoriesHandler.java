package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "list_categories".
 * Возвращает список категорий мебельного каталога с количеством предметов в каждой.
 *
 * <pre>
 * Параметры: нет
 *
 * Ответ:
 *   categories — массив [{name, count}, ...]
 *   totalItems — общее количество предметов в каталоге
 *
 * EDT: не требуется (каталог read-only, thread-safe)
 * </pre>
 */
public class ListCategoriesHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        FurnitureCatalog catalog = accessor.getFurnitureCatalog();
        List<Object> categories = new ArrayList<>();
        int totalItems = 0;

        for (FurnitureCategory cat : catalog.getCategories()) {
            String catName = cat.getName();
            if (catName == null) {
                continue;
            }
            int count = cat.getFurniture().size();
            totalItems += count;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", catName);
            item.put("count", count);
            categories.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categories", categories);
        data.put("totalItems", totalItems);
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Lists all furniture catalog categories with item counts. "
                + "RECOMMENDED first step before browsing furniture — "
                + "call this to see available categories, then use list_furniture_catalog with "
                + "a category filter to browse items within a specific category. "
                + "This saves tokens by avoiding a full catalog dump.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create().build();
    }
}
