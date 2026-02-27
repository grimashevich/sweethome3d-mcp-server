package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.round2;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "delete_furniture".
 * Удаляет мебель из сцены по стабильному строковому ID.
 */
public class DeleteFurnitureHandler implements CommandHandler, CommandDescriptor {

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

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", piece.getName());
            info.put("x", round2(piece.getX()));
            info.put("y", round2(piece.getY()));

            home.deletePieceOfFurniture(piece);
            return info;
        });

        if (data == null) {
            return Response.error("Furniture not found: " + id);
        }

        data.put("message", "Furniture '" + data.get("name") + "' deleted");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Deletes a piece of furniture from the scene by its ID. "
                + "Use get_state to find furniture IDs before deleting.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Furniture ID from get_state")
                .build();
    }

}
