package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

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
 * Обработчик команды "ungroup_furniture".
 * Разгруппирует {@link HomeFurnitureGroup}: удаляет группу, возвращает предметы в root-список.
 */
public class UngroupFurnitureHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, id);

            if (piece == null) {
                return null;
            }

            if (!(piece instanceof HomeFurnitureGroup)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("_error", "Furniture '" + piece.getName() + "' (id=" + id + ") is not a group");
                return err;
            }

            HomeFurnitureGroup group = (HomeFurnitureGroup) piece;
            List<HomePieceOfFurniture> children = new ArrayList<>(group.getFurniture());

            // Remove group from home
            home.deletePieceOfFurniture(group);

            // Add children back to home's root list
            for (HomePieceOfFurniture child : children) {
                home.addPieceOfFurniture(child);
            }

            // Build response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("groupName", group.getName());
            result.put("itemCount", children.size());
            List<String> itemIds = new ArrayList<>();
            for (HomePieceOfFurniture child : children) {
                itemIds.add(child.getId());
            }
            result.put("itemIds", itemIds);
            return result;
        });

        if (data == null) {
            return Response.error("Furniture not found: " + id);
        }

        if (data.containsKey("_error")) {
            return Response.error((String) data.get("_error"));
        }

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Use ungroup_furniture to break a furniture group apart, returning its items to the scene "
                + "as individual pieces. Only works on groups created by group_furniture or SH3D UI "
                + "(Furniture > Group). Use get_state to find group IDs (items with isGroup: true).";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "ID of the furniture group to ungroup (from get_state)")
                .build();
    }
}
