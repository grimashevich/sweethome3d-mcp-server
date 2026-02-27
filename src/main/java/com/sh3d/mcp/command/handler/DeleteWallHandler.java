package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.round2;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "delete_wall".
 * Удаляет стену из сцены по стабильному ID.
 */
public class DeleteWallHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Wall wall = ObjectResolver.findWall(home, id);

            if (wall == null) {
                return null;
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("xStart", round2(wall.getXStart()));
            info.put("yStart", round2(wall.getYStart()));
            info.put("xEnd", round2(wall.getXEnd()));
            info.put("yEnd", round2(wall.getYEnd()));
            info.put("length", round2(wall.getLength()));

            home.deleteWall(wall);
            return info;
        });

        if (data == null) {
            return Response.error("Wall not found: id '" + id + "'");
        }

        data.put("message", "Wall deleted (id " + id + ")");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Deletes a wall from the scene by its ID. "
                + "Use get_state to find wall IDs before deleting.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Wall ID from get_state")
                .build();
    }

}
