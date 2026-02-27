package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.FormatUtil;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "group_furniture".
 * Объединяет несколько предметов мебели в группу ({@link HomeFurnitureGroup}).
 * Группа ведёт себя как единый объект: движение, вращение, масштабирование.
 */
public class GroupFurnitureHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Object rawObj = request.getParams().get("ids");
        if (!(rawObj instanceof List)) {
            return Response.error("Parameter 'ids' is required and must contain at least 2 furniture IDs");
        }
        List<?> rawIds = (List<?>) rawObj;
        if (rawIds.size() < 2) {
            return Response.error("Parameter 'ids' is required and must contain at least 2 furniture IDs");
        }

        List<String> ids = new ArrayList<>();
        for (Object item : rawIds) {
            if (item instanceof String) {
                ids.add((String) item);
            } else if (item != null) {
                ids.add(String.valueOf(item));
            }
        }

        if (ids.size() < 2) {
            return Response.error("Parameter 'ids' must contain at least 2 furniture IDs");
        }

        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            name = "Group";
        }
        final String groupName = name;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            // Resolve all furniture pieces
            List<HomePieceOfFurniture> pieces = new ArrayList<>();
            for (String id : ids) {
                HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, id);
                if (piece == null) {
                    return errorMap("Furniture not found: " + id);
                }
                pieces.add(piece);
            }

            // Remove pieces from home's root list
            for (HomePieceOfFurniture piece : pieces) {
                home.deletePieceOfFurniture(piece);
            }

            // Create group and add to home
            HomeFurnitureGroup group = new HomeFurnitureGroup(pieces, groupName);
            home.addPieceOfFurniture(group);

            // Build response
            Map<String, Object> result = FormatUtil.buildFurnitureInfo(group);
            result.put("itemCount", pieces.size());
            return result;
        });

        if (data.containsKey("_error")) {
            return Response.error((String) data.get("_error"));
        }

        return Response.ok(data);
    }

    private static Map<String, Object> errorMap(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_error", message);
        return m;
    }

    @Override
    public String getDescription() {
        return "Groups multiple furniture pieces into a single object (HomeFurnitureGroup). "
                + "The group can be moved, rotated, and scaled as one unit. "
                + "Use this after building custom furniture from primitives (e.g., a table = 1 box tabletop + 4 cylinder legs). "
                + "Use get_state to find furniture IDs. "
                + "Use ungroup_furniture to break the group apart.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredArray("ids", SchemaBuilder.arrayDef(
                        "Array of furniture IDs to group (minimum 2). "
                                + "All items must be top-level furniture (not already inside another group).")
                        .itemsOfType("string")
                        .minItems(2)
                        .build())
                .string("name",
                        "Name for the group (e.g., 'Dining Table', 'Bookshelf'). Default: 'Group'")
                .build();
    }
}
