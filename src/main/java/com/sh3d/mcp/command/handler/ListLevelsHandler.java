package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
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
 * Обработчик команды "list_levels".
 * Возвращает все уровни с указанием текущего выбранного.
 */
public class ListLevelsHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Level> levels = home.getLevels();
            Level selected = home.getSelectedLevel();

            List<Object> levelList = new ArrayList<>();
            int index = 0;
            for (Level level : levels) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", index);
                item.put("name", level.getName());
                item.put("elevation", round2(level.getElevation()));
                item.put("height", round2(level.getHeight()));
                item.put("floorThickness", round2(level.getFloorThickness()));
                item.put("viewable", level.isViewable());
                item.put("selected", level.equals(selected));
                levelList.add(item);
                index++;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("levelCount", levelList.size());
            result.put("levels", levelList);
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Returns all levels (floors) in the home with their properties. "
                + "Each level has: id (index), name, elevation, height, floorThickness, viewable, "
                + "and selected (true if this is the currently active level). "
                + "Use set_selected_level to switch between levels. "
                + "If no levels exist, returns an empty list (single-level homes have no explicit levels).";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create().build();
    }

}
