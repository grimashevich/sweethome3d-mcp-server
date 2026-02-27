package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
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
 * Обработчик команды "list_furniture_catalog".
 * Возвращает содержимое каталога мебели с фильтрацией.
 *
 * <pre>
 * Параметры:
 *   query    — поисковый запрос по имени (string, опционально)
 *   category — фильтр по категории (string, опционально)
 *   type     — фильтр по типу: "all" (default), "furniture", "doorOrWindow" (опционально)
 *
 * Ответ:
 *   furniture — массив [{name, category, width, depth, height, isDoorOrWindow}, ...]
 *
 * EDT: не требуется (каталог read-only, thread-safe)
 * </pre>
 */
public class ListFurnitureCatalogHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String query = request.getString("query");
        String categoryFilter = request.getString("category");
        String typeFilter = request.getString("type");
        String lowerQuery = query != null ? query.toLowerCase() : null;
        String lowerCategory = categoryFilter != null ? categoryFilter.toLowerCase() : null;

        if (typeFilter != null) {
            String lowerType = typeFilter.toLowerCase();
            if (!"all".equals(lowerType) && !"furniture".equals(lowerType)
                    && !"doororwindow".equals(lowerType)) {
                return Response.error("Parameter 'type' must be 'all', 'furniture', "
                        + "or 'doorOrWindow', got '" + typeFilter + "'");
            }
            typeFilter = lowerType;
        }

        FurnitureCatalog catalog = accessor.getFurnitureCatalog();
        List<Object> results = new ArrayList<>();

        for (FurnitureCategory cat : catalog.getCategories()) {
            String catName = cat.getName();
            if (catName == null) {
                continue;
            }
            if (lowerCategory != null
                    && !catName.toLowerCase().contains(lowerCategory)) {
                continue;
            }
            for (CatalogPieceOfFurniture piece : cat.getFurniture()) {
                String pieceName = piece.getName();
                if (pieceName == null) {
                    continue;
                }
                if (lowerQuery != null
                        && !pieceName.toLowerCase().contains(lowerQuery)) {
                    continue;
                }
                if (typeFilter != null && !"all".equals(typeFilter)) {
                    boolean isDoorOrWindow = piece.isDoorOrWindow();
                    if ("furniture".equals(typeFilter) && isDoorOrWindow) continue;
                    if ("doororwindow".equals(typeFilter) && !isDoorOrWindow) continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", pieceName);
                String pieceId = piece.getId();
                if (pieceId != null) {
                    item.put("catalogId", pieceId);
                }
                item.put("category", catName);
                item.put("width", round2(piece.getWidth()));
                item.put("depth", round2(piece.getDepth()));
                item.put("height", round2(piece.getHeight()));
                item.put("isDoorOrWindow", piece.isDoorOrWindow());
                results.add(item);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("furniture", results);
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Lists furniture in the Sweet Home 3D catalog. "
                + "Can filter by name query, category, and/or type (furniture vs doors/windows). "
                + "Returns furniture names, categories, dimensions, and isDoorOrWindow flag. "
                + "TIP: Call list_categories first to see available categories, "
                + "then use the category filter here to browse a specific category. "
                + "Use query for direct name search when you know what you're looking for. "
                + "Use type='doorOrWindow' to list only doors and windows for place_door_or_window.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("query", "Search query for furniture name")
                .string("category", "Filter by category name")
                .enumProp("type",
                        "Filter by type: 'all' (default), 'furniture' (excludes doors/windows), "
                                + "'doorOrWindow' (only doors and windows)",
                        "all", "furniture", "doorOrWindow")
                .build();
    }

}
