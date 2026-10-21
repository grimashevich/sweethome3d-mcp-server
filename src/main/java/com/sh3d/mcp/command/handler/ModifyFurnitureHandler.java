package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.ColorParser;
import com.sh3d.mcp.command.util.SashUtil;
import com.sh3d.mcp.command.util.ValidationUtil;

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
            "color", "visible", "mirrored", "name", SashUtil.PARAM_PRESET, SashUtil.PARAM_SASHES
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
                    + "Supported: x, y, angle, elevation, width, depth, height, color, visible, mirrored, name, sashPreset, sashes");
        }

        String finiteError = ValidationUtil.validateFiniteInputs(params,
                "x", "y", "angle", "elevation", "width", "depth", "height");
        if (finiteError != null) {
            return Response.error(finiteError);
        }

        // Parse every value before touching the live piece.
        final Map<String, Float> numbers = new LinkedHashMap<>();
        final Boolean visible;
        final Boolean mirrored;
        final String newName;
        try {
            for (String key : Arrays.asList("x", "y", "angle", "elevation", "width", "depth", "height")) {
                if (params.containsKey(key)) {
                    numbers.put(key, request.getFloat(key));
                }
            }
            visible = request.getBoolean("visible");
            mirrored = request.getBoolean("mirrored");
            newName = request.getString("name");
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        }

        // A zero group dimension would make the next resize divide by zero in SH3D.
        for (String key : Arrays.asList("width", "depth", "height")) {
            Float size = numbers.get(key);
            if (size != null && size <= 0) {
                return Response.error("Parameter '" + key + "' must be greater than 0");
            }
        }

        // Validate color format before EDT
        ColorParser.ColorResult colorResult = ColorParser.parseNullable(params, "color");
        if (colorResult != null && colorResult.hasError()) {
            return Response.error(colorResult.error);
        }

        // Створки — тоже до EDT: при ошибке изделие не меняется
        final SashUtil.Spec sashSpec;
        try {
            sashSpec = SashUtil.parse(params);
        } catch (IllegalArgumentException e) {
            return Response.error(e.getMessage());
        }

        final Integer colorToSet = (colorResult != null && !colorResult.clear) ? colorResult.value : null;
        final boolean doClearColor = colorResult != null && colorResult.clear;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, id);

            if (piece == null) {
                return null;
            }
            String sashError = SashUtil.checkApplicable(sashSpec, piece);
            if (sashError != null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("_error", sashError);
                return err;
            }

            if (!piece.isResizable() && (numbers.containsKey("width")
                    || numbers.containsKey("depth") || numbers.containsKey("height") || mirrored != null)) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("_error", "Piece isn't resizable");
                return err;
            }

            // SH3D checks this even when clearing an already-null color.
            if (colorResult != null && !piece.isTexturable()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("_error", "Piece isn't texturable");
                return err;
            }

            // Position
            if (params.containsKey("x")) {
                piece.setX(numbers.get("x"));
            }
            if (params.containsKey("y")) {
                piece.setY(numbers.get("y"));
            }
            if (params.containsKey("angle")) {
                piece.setAngle((float) Math.toRadians(numbers.get("angle")));
            }
            if (params.containsKey("elevation")) {
                piece.setElevation(numbers.get("elevation"));
            }

            // Dimensions
            if (params.containsKey("width")) {
                piece.setWidth(numbers.get("width"));
            }
            if (params.containsKey("depth")) {
                piece.setDepth(numbers.get("depth"));
            }
            if (params.containsKey("height")) {
                piece.setHeight(numbers.get("height"));
            }

            // Without the application's PlanController there is no listener to
            // update these dimensions. Rotated models still use its model-based bounds.
            if (!piece.isHorizontallyRotated() && (numbers.containsKey("width")
                    || numbers.containsKey("depth") || numbers.containsKey("height"))) {
                piece.setWidthInPlan(piece.getWidth());
                piece.setDepthInPlan(piece.getDepth());
                piece.setHeightInPlan(piece.getHeight());
            }

            // Appearance
            if (doClearColor) {
                piece.setColor(null);
            } else if (colorToSet != null) {
                piece.setColor(colorToSet);
            }

            // Flags
            if (visible != null) {
                piece.setVisible(visible);
            }
            if (mirrored != null) {
                piece.setModelMirrored(mirrored);
            }

            // Name
            if (params.containsKey("name")) {
                if (newName != null) {
                    piece.setName(newName);
                }
            }

            // Sashes (door/window swing arcs)
            if (sashSpec != null) {
                sashSpec.applyTo(piece);
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
        if (data.containsKey("_error")) {
            return Response.error(String.valueOf(data.get("_error")));
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
                .enumProp(SashUtil.PARAM_PRESET, SashUtil.presetDescription(), SashUtil.PRESETS)
                .array(SashUtil.PARAM_SASHES, SashUtil.sashArraySchema())
                .build();
    }

}
