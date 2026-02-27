package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.ValidationUtil;
import com.sh3d.mcp.command.util.ColorParser;
import com.sh3d.mcp.command.util.FormatUtil;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "modify_room".
 * Изменяет свойства комнаты по стабильному ID.
 */
public class ModifyRoomHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "name", "floorVisible", "ceilingVisible", "areaVisible",
            "floorColor", "ceilingColor",
            "floorShininess", "ceilingShininess"
    );

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null || id.trim().isEmpty()) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> params = request.getParams();
        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. "
                    + "Supported: name, floorVisible, ceilingVisible, areaVisible, "
                    + "floorColor, ceilingColor, floorShininess, ceilingShininess");
        }

        // Parse and validate colors before EDT
        ColorParser.ColorResult floorColorResult = ColorParser.parseNullable(params, "floorColor");
        if (floorColorResult != null && floorColorResult.hasError()) {
            return Response.error(floorColorResult.error);
        }

        ColorParser.ColorResult ceilingColorResult = ColorParser.parseNullable(params, "ceilingColor");
        if (ceilingColorResult != null && ceilingColorResult.hasError()) {
            return Response.error(ceilingColorResult.error);
        }

        // Validate shininess before EDT
        String shininessError = ValidationUtil.validateRange(params, 0f, 1f,
                "floorShininess", "ceilingShininess");
        if (shininessError != null) {
            return Response.error(shininessError);
        }

        // Capture for lambda
        final boolean doSetFloorColor = floorColorResult != null;
        final boolean doClearFloorColor = doSetFloorColor && floorColorResult.clear;
        final int finalFloorColor = doSetFloorColor ? floorColorResult.value : 0;
        final boolean doSetCeilingColor = ceilingColorResult != null;
        final boolean doClearCeilingColor = doSetCeilingColor && ceilingColorResult.clear;
        final int finalCeilingColor = doSetCeilingColor ? ceilingColorResult.value : 0;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Room room = ObjectResolver.findRoom(home, id);
            if (room == null) {
                return null;
            }

            // Name
            if (params.containsKey("name")) {
                room.setName(request.getString("name"));
            }

            // Visibility
            Boolean floorVisible = request.getBoolean("floorVisible");
            if (floorVisible != null) {
                room.setFloorVisible(floorVisible);
            }
            Boolean ceilingVisible = request.getBoolean("ceilingVisible");
            if (ceilingVisible != null) {
                room.setCeilingVisible(ceilingVisible);
            }
            Boolean areaVisible = request.getBoolean("areaVisible");
            if (areaVisible != null) {
                room.setAreaVisible(areaVisible);
            }

            // Colors
            if (doSetFloorColor) {
                room.setFloorColor(doClearFloorColor ? null : finalFloorColor);
            }
            if (doSetCeilingColor) {
                room.setCeilingColor(doClearCeilingColor ? null : finalCeilingColor);
            }

            // Shininess
            if (params.containsKey("floorShininess")) {
                room.setFloorShininess(request.getFloat("floorShininess"));
            }
            if (params.containsKey("ceilingShininess")) {
                room.setCeilingShininess(request.getFloat("ceilingShininess"));
            }

            return buildResponse(id, room);
        });

        if (data == null) {
            return Response.error("Room not found: " + id);
        }

        return Response.ok(data);
    }

    private static Map<String, Object> buildResponse(String id, Room room) {
        return FormatUtil.buildRoomInfo(room);
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Modifies properties of an existing room by ID. Use get_state to find room IDs. "
                + "Only provided properties are changed; omitted ones remain unchanged. "
                + "Colors are hex strings like '#CCBB99' (beige floor), or null to reset to default. "
                + "Shininess ranges from 0.0 (matte) to 1.0 (glossy). "
                + "Room geometry (points) cannot be modified — use delete_room + create_room_polygon instead.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Room ID from get_state")
                .string("name", "Room name (e.g. 'Kitchen', 'Living Room')")
                .bool("floorVisible", "Whether floor surface is visible in 3D")
                .bool("ceilingVisible", "Whether ceiling surface is visible in 3D")
                .bool("areaVisible", "Whether area label is shown on the plan")
                .nullableString("floorColor",
                        "Floor color as '#RRGGBB' (e.g. '#CCBB99' for beige), or null to reset")
                .nullableString("ceilingColor", "Ceiling color as '#RRGGBB', or null to reset")
                .number("floorShininess", "Floor shininess: 0.0 (matte) to 1.0 (glossy)")
                .number("ceilingShininess", "Ceiling shininess: 0.0 (matte) to 1.0 (glossy)")
                .build();
    }

}
