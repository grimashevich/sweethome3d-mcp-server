package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.CatalogSearchUtil;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "place_furniture".
 * Ищет мебель в каталоге SH3D и размещает на плане.
 *
 * <p>Поиск: exact match имени приоритетнее substring.
 * При нескольких exact match — ошибка disambiguации.
 * Параметр catalogId позволяет выбрать конкретный элемент по ID каталога.
 */
public class PlaceFurnitureHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        String catalogId = request.getString("catalogId");

        if ((name == null || name.trim().isEmpty())
                && (catalogId == null || catalogId.trim().isEmpty())) {
            return Response.error("Either 'name' or 'catalogId' must be provided");
        }

        if (!request.getParams().containsKey("x")) {
            return Response.error("Missing required parameter: x");
        }
        if (!request.getParams().containsKey("y")) {
            return Response.error("Missing required parameter: y");
        }

        float x = request.getFloat("x");
        float y = request.getFloat("y");
        float angle = request.getFloat("angle", 0f);
        boolean hasElevation = request.getParams().containsKey("elevation");
        float elevation = hasElevation ? request.getFloat("elevation") : 0f;

        CatalogSearchUtil.FurnitureSearchResult searchResult =
                CatalogSearchUtil.findFurniture(
                        accessor.getFurnitureCatalog(), name, catalogId, null);
        if (searchResult.isError()) {
            return Response.error(searchResult.getError());
        }
        if (!searchResult.isFound()) {
            return Response.error("Furniture not found: " + name);
        }
        CatalogPieceOfFurniture found = searchResult.getFound();

        float angleRad = (float) Math.toRadians(angle);

        HomePieceOfFurniture placed = accessor.runOnEDT(() -> {
            HomePieceOfFurniture piece = new HomePieceOfFurniture(found);
            piece.setX(x);
            piece.setY(y);
            piece.setAngle(angleRad);
            if (hasElevation) {
                piece.setElevation(elevation);
            }
            accessor.getHome().addPieceOfFurniture(piece);
            return piece;
        });

        return Response.ok(FormatUtil.buildFurnitureInfo(placed));
    }

    @Override
    public String getDescription() {
        return "Places a piece of furniture from the Sweet Home 3D catalog. "
                + "Searches the catalog by name (case-insensitive, partial match). "
                + "Coordinates are in centimeters. "
                + "Angle is in degrees (0 = default orientation, 90 = rotated clockwise). "
                + "Use 'catalogId' for precise selection when multiple items share the same name. "
                + "Returns the furniture id for use with modify_furniture, delete_furniture.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .string("name", "Furniture name to search in catalog (e.g., 'bed', 'sofa', 'table')")
                .string("catalogId",
                        "Exact catalog ID for precise selection (bypasses name search). "
                                + "Use list_furniture_catalog to find catalog IDs")
                .requiredNumber("x", "X coordinate in cm")
                .requiredNumber("y", "Y coordinate in cm")
                .numberWithDefault("angle", "Rotation angle in degrees", 0)
                .numberWithDefault("elevation", "Elevation above floor in cm (e.g., for wall-mounted items)", 0)
                .build();
    }

}
