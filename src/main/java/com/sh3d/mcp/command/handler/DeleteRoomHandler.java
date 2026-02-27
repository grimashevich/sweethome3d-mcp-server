package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import static com.sh3d.mcp.command.util.FormatUtil.round2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "delete_room".
 * Удаляет комнату из сцены по стабильному ID.
 */
public class DeleteRoomHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null || id.trim().isEmpty()) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Room room = ObjectResolver.findRoom(home, id);
            if (room == null) {
                return null;
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", room.getName());
            info.put("area", round2(room.getArea()));
            info.put("xCenter", round2(room.getXCenter()));
            info.put("yCenter", round2(room.getYCenter()));

            float[][] points = room.getPoints();
            List<Object> pointList = new ArrayList<>();
            for (float[] pt : points) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("x", round2(pt[0]));
                p.put("y", round2(pt[1]));
                pointList.add(p);
            }
            info.put("points", pointList);

            home.deleteRoom(room);
            return info;
        });

        if (data == null) {
            return Response.error("Room not found: " + id);
        }

        String name = data.get("name") != null ? "'" + data.get("name") + "'" : "(unnamed)";
        data.put("message", "Room " + name + " deleted (id " + id + ")");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Deletes a room from the scene by its ID. "
                + "Use get_state to find room IDs before deleting. "
                + "Walls are NOT deleted — only the room polygon is removed.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Room ID from get_state")
                .build();
    }

}
