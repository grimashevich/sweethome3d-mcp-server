package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import static com.sh3d.mcp.command.util.FormatUtil.round2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "delete_level".
 * Удаляет уровень по ID и все объекты на этом уровне.
 * Каскадное удаление реализовано вручную, т.к. Home.deleteLevel()
 * не каскадирует без контроллера SH3D.
 */
public class DeleteLevelHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null || id.trim().isEmpty()) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Level level = ObjectResolver.findLevel(home, id);
            if (level == null) {
                return null;
            }

            // Manually cascade-delete all objects on this level
            int wallCount = 0;
            for (Wall w : new ArrayList<>(home.getWalls())) {
                if (w.getLevel() == level) {
                    home.deleteWall(w);
                    wallCount++;
                }
            }

            int furnitureCount = 0;
            for (HomePieceOfFurniture p : new ArrayList<>(home.getFurniture())) {
                if (p.getLevel() == level) {
                    home.deletePieceOfFurniture(p);
                    furnitureCount++;
                }
            }

            int roomCount = 0;
            for (Room r : new ArrayList<>(home.getRooms())) {
                if (r.getLevel() == level) {
                    home.deleteRoom(r);
                    roomCount++;
                }
            }

            int labelCount = 0;
            for (Label l : new ArrayList<>(home.getLabels())) {
                if (l.getLevel() == level) {
                    home.deleteLabel(l);
                    labelCount++;
                }
            }

            int dimLineCount = 0;
            for (DimensionLine d : new ArrayList<>(home.getDimensionLines())) {
                if (d.getLevel() == level) {
                    home.deleteDimensionLine(d);
                    dimLineCount++;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", level.getName());
            result.put("elevation", round2(level.getElevation()));
            result.put("deletedWalls", wallCount);
            result.put("deletedFurniture", furnitureCount);
            result.put("deletedRooms", roomCount);
            result.put("deletedLabels", labelCount);
            result.put("deletedDimensionLines", dimLineCount);

            home.deleteLevel(level);

            result.put("remainingLevels", home.getLevels().size());
            return result;
        });

        if (data == null) {
            return Response.error("Level not found: " + id);
        }

        String name = data.get("name") != null ? "'" + data.get("name") + "'" : "(unnamed)";
        data.put("message", "Level " + name + " deleted (id " + id + ")");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Deletes a level (floor) by its ID. WARNING: this also deletes ALL objects on that level "
                + "(walls, furniture, rooms, labels, dimension lines). "
                + "Use list_levels or get_state to find level IDs. "
                + "Returns counts of deleted objects for each type.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Level ID from list_levels or get_state")
                .build();
    }

}
