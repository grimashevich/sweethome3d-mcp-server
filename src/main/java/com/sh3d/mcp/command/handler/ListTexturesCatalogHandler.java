package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.round2;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "list_textures_catalog".
 * Возвращает содержимое каталога текстур с фильтрацией.
 *
 * <pre>
 * Параметры:
 *   query    — поисковый запрос по имени (string, опционально)
 *   category — фильтр по категории (string, опционально)
 *
 * Ответ:
 *   textures — массив [{name, category, width, height, creator}, ...]
 *
 * EDT: не требуется (каталог read-only, thread-safe)
 * </pre>
 */
public class ListTexturesCatalogHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String query = request.getString("query");
        String categoryFilter = request.getString("category");
        String lowerQuery = query != null ? query.toLowerCase() : null;
        String lowerCategory = categoryFilter != null ? categoryFilter.toLowerCase() : null;

        TexturesCatalog catalog = accessor.getTexturesCatalog();
        List<Object> results = new ArrayList<>();

        for (TexturesCategory cat : catalog.getCategories()) {
            String catName = cat.getName();
            if (catName == null) {
                continue;
            }
            if (lowerCategory != null
                    && !catName.toLowerCase().contains(lowerCategory)) {
                continue;
            }
            for (CatalogTexture texture : cat.getTextures()) {
                String textureName = texture.getName();
                if (textureName == null) {
                    continue;
                }
                if (lowerQuery != null
                        && !textureName.toLowerCase().contains(lowerQuery)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", textureName);
                String texId = texture.getId();
                if (texId != null) {
                    item.put("catalogId", texId);
                }
                item.put("category", catName);
                item.put("width", round2(texture.getWidth()));
                item.put("height", round2(texture.getHeight()));
                String creator = texture.getCreator();
                if (creator != null) {
                    item.put("creator", creator);
                }
                results.add(item);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("textures", results);
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Lists available textures in the Sweet Home 3D catalog. "
                + "Can filter by name query and/or category. "
                + "Returns texture names, categories, dimensions (width x height in cm), and creator. "
                + "Use this to find the correct texture name before applying it to walls, floors, or ceilings.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("query", "Search query for texture name")
                .string("category", "Filter by category name")
                .build();
    }

}
