package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import static com.sh3d.mcp.command.util.FormatUtil.round2;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "set_selected_level".
 * Переключает активный уровень по стабильному ID.
 */
public class SetSelectedLevelHandler implements CommandHandler, CommandDescriptor {

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
            home.setSelectedLevel(level);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("name", level.getName());
            result.put("elevation", round2(level.getElevation()));
            result.put("height", round2(level.getHeight()));
            result.put("floorThickness", round2(level.getFloorThickness()));
            result.put("viewable", level.isViewable());
            return result;
        });

        if (data == null) {
            return Response.error("Level not found: " + id);
        }

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Sets the active (selected) level by its ID. "
                + "All subsequent create commands (walls, rooms, furniture) will be placed on the selected level. "
                + "Use list_levels or get_state to find level IDs.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Level ID from list_levels or get_state")
                .build();
    }

}
