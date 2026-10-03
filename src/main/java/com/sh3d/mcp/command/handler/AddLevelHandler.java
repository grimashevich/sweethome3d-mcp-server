package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.List;
import java.util.Map;
import com.sh3d.mcp.command.util.FormatUtil;

/**
 * Обработчик команды "add_level".
 * Создаёт новый уровень (этаж) и делает его активным.
 */
public class AddLevelHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            return Response.error("Parameter 'name' is required and must not be empty");
        }

        float elevation = request.getFloat("elevation");
        float height = request.getFloat("height", 250f);
        float floorThickness = request.getFloat("floorThickness", 12f);

        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive, got " + height);
        }
        if (floorThickness < 0) {
            return Response.error("Parameter 'floorThickness' must be non-negative, got " + floorThickness);
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Level level = new Level(name.trim(), elevation, floorThickness, height);
            home.addLevel(level);
            home.setSelectedLevel(level);

            Map<String, Object> result = FormatUtil.buildLevelInfo(level.getId(), level);
            result.put("levelCount", home.getLevels().size());
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Creates a new level (floor/storey) in the home. The new level becomes the active (selected) level. "
                + "All subsequent create commands (walls, rooms, furniture) will be placed on the selected level. "
                + "Elevation is the bottom height of the level in cm (e.g., 0 for ground floor, 250 for second floor). "
                + "Height is the wall height on this level (default 250 cm). "
                + "Use list_levels to see all levels, set_selected_level to switch between them."
                + "\n\nIMPORTANT: Objects created before any levels exist have no level assignment (level=null) "
                + "and remain visible on ALL levels. For clean multi-level projects, create levels first "
                + "with add_level before placing walls/rooms/furniture, or use clear_scene to start fresh.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("name", "Name of the level (e.g., 'Ground Floor', 'Second Floor', 'Attic')")
                .requiredNumber("elevation", "Bottom elevation of the level in cm. 0 for ground floor, typically previous level's elevation + height for upper floors")
                .numberWithDefault("height", "Wall height on this level in cm", 250)
                .numberWithDefault("floorThickness", "Floor/ceiling slab thickness in cm", 12)
                .build();
    }

}
