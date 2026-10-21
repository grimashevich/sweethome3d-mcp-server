package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.viewcontroller.PlanController;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.command.util.ValidationUtil;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Map;
import com.sh3d.mcp.command.util.FormatUtil;

/**
 * Обработчик команды "add_level".
 * Создаёт новый уровень (этаж) и делает его активным.
 */
public class AddLevelHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String finiteError = ValidationUtil.validateFiniteInputs(request.getParams(),
                "elevation", "height", "floorThickness");
        if (finiteError != null) {
            return Response.error(finiteError);
        }

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
            if (home.getLevels().isEmpty()) {
                List<Consumer<Level>> existing = new ArrayList<>();
                home.getFurniture().forEach(item -> existing.add(item::setLevel));
                home.getWalls().forEach(item -> existing.add(item::setLevel));
                home.getRooms().forEach(item -> existing.add(item::setLevel));
                home.getPolylines().forEach(item -> existing.add(item::setLevel));
                home.getDimensionLines().forEach(item -> existing.add(item::setLevel));
                home.getLabels().forEach(item -> existing.add(item::setLevel));
                if (!existing.isEmpty()) {
                    Level lower = level;
                    if (elevation != 0) {
                        UserPreferences preferences = accessor.getUserPreferences();
                        lower = preferences != null
                                ? new Level(preferences.getLocalizedString(PlanController.class, "levelName", 0),
                                        0, preferences.getNewFloorThickness(), preferences.getNewWallHeight())
                                : new Level("Level 0", 0, 12f, home.getWallHeight());
                        home.addLevel(lower);
                    }
                    for (Consumer<Level> assignment : existing) {
                        assignment.accept(lower);
                    }
                }
            }
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
                + "Elevation is the bottom height of the level in cm (e.g., 0 for ground floor, 262 for second floor). "
                + "Height is the floor-to-ceiling height of the level (default 250 cm), separate from floorThickness. "
                + "An upper level typically starts at the previous level's elevation + height + floorThickness. "
                + "Use list_levels to see all levels, set_selected_level to switch between them."
                + " When the home has objects but no levels, existing objects are assigned to the new level at elevation 0, "
                + "or to an automatically created ground level if the requested elevation is nonzero.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("name", "Name of the level (e.g., 'Ground Floor', 'Second Floor', 'Attic')")
                .requiredNumber("elevation", "Bottom elevation of the level in cm. 0 for ground floor, typically previous level's elevation + height + floorThickness for upper floors")
                .numberWithDefault("height", "Floor-to-ceiling height of this level in cm", 250)
                .numberWithDefault("floorThickness", "Floor/ceiling slab thickness in cm", 12)
                .build();
    }

}
