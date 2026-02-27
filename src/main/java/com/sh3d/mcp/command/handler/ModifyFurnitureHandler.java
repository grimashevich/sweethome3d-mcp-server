package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.ColorParser;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import static com.sh3d.mcp.command.util.FormatUtil.colorToHex;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "modify_furniture".
 * Изменяет свойства мебели по стабильному строковому ID.
 */
public class ModifyFurnitureHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "x", "y", "angle", "elevation", "width", "depth", "height",
            "color", "visible", "mirrored", "name"
    );

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> params = request.getParams();
        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. "
                    + "Supported: x, y, angle, elevation, width, depth, height, color, visible, mirrored, name");
        }

        // Validate color format before EDT
        ColorParser.ColorResult colorResult = ColorParser.parseNullable(params, "color");
        if (colorResult != null && colorResult.hasError()) {
            return Response.error(colorResult.error);
        }

        final Integer colorToSet = (colorResult != null && !colorResult.clear) ? colorResult.value : null;
        final boolean doClearColor = colorResult != null && colorResult.clear;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, id);

            if (piece == null) {
                return null;
            }

            // Position
            if (params.containsKey("x")) {
                piece.setX(request.getFloat("x"));
            }
            if (params.containsKey("y")) {
                piece.setY(request.getFloat("y"));
            }
            if (params.containsKey("angle")) {
                piece.setAngle((float) Math.toRadians(request.getFloat("angle")));
            }
            if (params.containsKey("elevation")) {
                piece.setElevation(request.getFloat("elevation"));
            }

            // Dimensions
            if (params.containsKey("width")) {
                piece.setWidth(request.getFloat("width"));
            }
            if (params.containsKey("depth")) {
                piece.setDepth(request.getFloat("depth"));
            }
            if (params.containsKey("height")) {
                piece.setHeight(request.getFloat("height"));
            }

            // Appearance
            if (doClearColor) {
                piece.setColor(null);
            } else if (colorToSet != null) {
                piece.setColor(colorToSet);
            }

            // Flags
            Boolean visible = request.getBoolean("visible");
            if (visible != null) {
                piece.setVisible(visible);
            }
            Boolean mirrored = request.getBoolean("mirrored");
            if (mirrored != null) {
                piece.setModelMirrored(mirrored);
            }

            // Name
            if (params.containsKey("name")) {
                String newName = request.getString("name");
                if (newName != null) {
                    piece.setName(newName);
                }
            }

            // Build response with current state
            Map<String, Object> result = FormatUtil.buildFurnitureInfo(piece);
            result.put("color", colorToHex(piece.getColor()));
            result.put("visible", piece.isVisible());
            result.put("mirrored", piece.isModelMirrored());
            return result;
        });

        if (data == null) {
            return Response.error("Furniture not found: " + id);
        }

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Modifies properties of existing furniture by ID. Use get_state to find furniture IDs. "
                + "Only provided properties are changed; omitted properties remain unchanged. "
                + "Coordinates are in centimeters, angle in degrees. "
                + "Color is a hex string like '#FF0000' (red) or null to reset to default.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Furniture ID from get_state")
                .number("x", "New X coordinate in cm")
                .number("y", "New Y coordinate in cm")
                .number("angle", "New rotation angle in degrees (0-360)")
                .number("elevation", "Height above floor in cm")
                .number("width", "New width in cm")
                .number("depth", "New depth in cm")
                .number("height", "New height in cm")
                .nullableString("color", "Color as hex '#RRGGBB' (e.g. '#FF0000' for red), or null to reset")
                .bool("visible", "Whether furniture is visible in the scene")
                .bool("mirrored", "Whether furniture model is mirrored")
                .string("name", "New display name for the furniture")
                .build();
    }

}
