package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.ColorParser;
import com.sh3d.mcp.command.util.SashUtil;

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

            // Sashes (door/window swing arcs)
            String sashError = SashUtil.applyFromParams(piece, params);
            if (sashError != null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", sashError);
                return err;
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
        if (data.containsKey("error")) {
            return Response.error(String.valueOf(data.get("error")));
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
                .enumProp(SashUtil.PARAM_PRESET,
                        "Doors/windows only. Swing arc preset drawn in the 2D plan: 'single_left' (hinge at the "
                        + "piece's left end), 'single_right', 'double' (two leaves meeting in the middle), 'none'. "
                        + "Use when the catalog model has no sash data (get_state reports sashes=0)",
                        SashUtil.PRESETS)
                .array(SashUtil.PARAM_SASHES, sashArraySchema())
                .build();
    }

    /** JSON Schema for an explicit sash list; shared with place_door_or_window. */
    static Map<String, Object> sashArraySchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("xAxis", numberProp("Hinge X as a fraction of width: 0 = left end, 1 = right end"));
        props.put("yAxis", numberProp("Hinge Y as a fraction of depth (default 0.5)"));
        props.put("width", numberProp("Leaf width as a fraction of piece width (default 1)"));
        props.put("startAngle", numberProp("Closed-leaf angle in degrees (default 0)"));
        props.put("endAngle", numberProp("Open-leaf angle in degrees (default 90)"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", props);
        Map<String, Object> arr = new LinkedHashMap<>();
        arr.put("type", "array");
        arr.put("description", "Doors/windows only. Explicit sash list; overrides sashPreset");
        arr.put("items", item);
        return arr;
    }

    private static Map<String, Object> numberProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "number");
        prop.put("description", description);
        return prop;
    }

}
