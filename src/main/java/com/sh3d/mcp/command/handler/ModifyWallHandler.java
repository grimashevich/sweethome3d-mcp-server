package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.ValidationUtil;
import com.sh3d.mcp.command.util.ColorParser;
import com.sh3d.mcp.command.util.FormatUtil;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "modify_wall".
 * Изменяет свойства стены по стабильному ID.
 *
 * Стороны стены определяются направлением от (xStart,yStart) к (xEnd,yEnd):
 * - Left side — сторона слева при движении от start к end
 * - Right side — сторона справа при движении от start к end
 */
public class ModifyWallHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "xStart", "yStart", "xEnd", "yEnd",
            "height", "heightAtEnd", "thickness", "arcExtent",
            "leftSideColor", "rightSideColor", "topColor", "color",
            "leftSideShininess", "rightSideShininess", "shininess"
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
                    + "Supported: xStart, yStart, xEnd, yEnd, "
                    + "height, heightAtEnd, thickness, arcExtent, "
                    + "leftSideColor, rightSideColor, topColor, color, "
                    + "leftSideShininess, rightSideShininess, shininess");
        }

        // Parse and validate colors before EDT
        ParsedColors colors = parseColors(params);
        if (colors.error != null) {
            return Response.error(colors.error);
        }

        // Validate shininess before EDT
        String shininessError = ValidationUtil.validateRange(params, 0f, 1f,
                "shininess", "leftSideShininess", "rightSideShininess");
        if (shininessError != null) {
            return Response.error(shininessError);
        }

        // Validate height/heightAtEnd/thickness before EDT
        if (params.containsKey("height")) {
            float h = request.getFloat("height");
            if (h <= 0) {
                return Response.error("Parameter 'height' must be positive, got " + h);
            }
        }
        if (params.containsKey("heightAtEnd")) {
            Object val = params.get("heightAtEnd");
            if (val != null) {
                float h = request.getFloat("heightAtEnd");
                if (h <= 0) {
                    return Response.error("Parameter 'heightAtEnd' must be positive, got " + h);
                }
            }
        }
        if (params.containsKey("thickness")) {
            float t = request.getFloat("thickness");
            if (t <= 0) {
                return Response.error("Parameter 'thickness' must be positive, got " + t);
            }
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Wall wall = ObjectResolver.findWall(home, id);

            if (wall == null) {
                return null;
            }

            // Coordinates
            if (params.containsKey("xStart")) {
                wall.setXStart(request.getFloat("xStart"));
            }
            if (params.containsKey("yStart")) {
                wall.setYStart(request.getFloat("yStart"));
            }
            if (params.containsKey("xEnd")) {
                wall.setXEnd(request.getFloat("xEnd"));
            }
            if (params.containsKey("yEnd")) {
                wall.setYEnd(request.getFloat("yEnd"));
            }

            // Height (already validated)
            if (params.containsKey("height")) {
                wall.setHeight(request.getFloat("height"));
            }

            if (params.containsKey("heightAtEnd")) {
                Object val = params.get("heightAtEnd");
                if (val == null) {
                    wall.setHeightAtEnd(null);
                } else {
                    wall.setHeightAtEnd(request.getFloat("heightAtEnd"));
                }
            }

            // Thickness (already validated)
            if (params.containsKey("thickness")) {
                wall.setThickness(request.getFloat("thickness"));
            }

            // Arc extent
            if (params.containsKey("arcExtent")) {
                Object val = params.get("arcExtent");
                if (val == null) {
                    wall.setArcExtent(null);
                } else {
                    float arc = request.getFloat("arcExtent");
                    wall.setArcExtent((float) Math.toRadians(arc));
                }
            }

            // Colors — 'color' shortcut sets both sides + top
            if (colors.colorBoth != null) {
                if (colors.colorBothClear) {
                    wall.setLeftSideColor(null);
                    wall.setRightSideColor(null);
                    wall.setTopColor(null);
                } else {
                    wall.setLeftSideColor(colors.colorBoth);
                    wall.setRightSideColor(colors.colorBoth);
                    wall.setTopColor(colors.colorBoth);
                }
            }
            // Individual colors override 'color' if both specified
            if (colors.leftColor != null) {
                wall.setLeftSideColor(colors.leftColorClear ? null : colors.leftColor);
            }
            if (colors.rightColor != null) {
                wall.setRightSideColor(colors.rightColorClear ? null : colors.rightColor);
            }
            if (colors.topColor != null) {
                wall.setTopColor(colors.topColorClear ? null : colors.topColor);
            }

            // Shininess — 'shininess' shortcut sets both sides
            if (params.containsKey("shininess")) {
                float s = request.getFloat("shininess");
                wall.setLeftSideShininess(s);
                wall.setRightSideShininess(s);
            }
            if (params.containsKey("leftSideShininess")) {
                wall.setLeftSideShininess(request.getFloat("leftSideShininess"));
            }
            if (params.containsKey("rightSideShininess")) {
                wall.setRightSideShininess(request.getFloat("rightSideShininess"));
            }

            // Build response
            return buildResponse(id, wall);
        });

        if (data == null) {
            return Response.error("Wall not found: id '" + id + "'");
        }

        return Response.ok(data);
    }

    private static Map<String, Object> buildResponse(String id, Wall wall) {
        return FormatUtil.buildWallInfo(wall);
    }

    // --- Color parsing ---

    private static ParsedColors parseColors(Map<String, Object> params) {
        ParsedColors c = new ParsedColors();

        // 'color' shortcut
        ColorParser.ColorResult colorBothResult = ColorParser.parseNullable(params, "color");
        if (colorBothResult != null) {
            if (colorBothResult.hasError()) {
                c.error = colorBothResult.error;
                return c;
            }
            c.colorBoth = colorBothResult.clear ? 0 : colorBothResult.value;
            c.colorBothClear = colorBothResult.clear;
        }

        // Individual colors
        ColorParser.ColorResult leftResult = ColorParser.parseNullable(params, "leftSideColor");
        if (leftResult != null) {
            if (leftResult.hasError()) { c.error = leftResult.error; return c; }
            c.leftColor = leftResult.clear ? 0 : leftResult.value;
            c.leftColorClear = leftResult.clear;
        }

        ColorParser.ColorResult rightResult = ColorParser.parseNullable(params, "rightSideColor");
        if (rightResult != null) {
            if (rightResult.hasError()) { c.error = rightResult.error; return c; }
            c.rightColor = rightResult.clear ? 0 : rightResult.value;
            c.rightColorClear = rightResult.clear;
        }

        ColorParser.ColorResult topResult = ColorParser.parseNullable(params, "topColor");
        if (topResult != null) {
            if (topResult.hasError()) { c.error = topResult.error; return c; }
            c.topColor = topResult.clear ? 0 : topResult.value;
            c.topColorClear = topResult.clear;
        }

        return c;
    }

    // --- Parsed colors holder ---

    private static class ParsedColors {
        String error;
        Integer colorBoth;
        boolean colorBothClear;
        Integer leftColor;
        boolean leftColorClear;
        Integer rightColor;
        boolean rightColorClear;
        Integer topColor;
        boolean topColorClear;
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Modifies properties of an existing wall by ID. Use get_state to find wall IDs. "
                + "Only provided properties are changed; omitted ones remain unchanged. "
                + "Wall sides are relative to direction from start to end point: "
                + "'left' is the side on your left when walking from start to end, 'right' is the other side. "
                + "Use 'color' to set all sides at once, or 'leftSideColor'/'rightSideColor'/'topColor' individually. "
                + "Shininess 0.0 = matte, 1.0 = glossy.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Wall ID from get_state")
                .number("xStart", "New X coordinate of wall start point in cm")
                .number("yStart", "New Y coordinate of wall start point in cm")
                .number("xEnd", "New X coordinate of wall end point in cm")
                .number("yEnd", "New Y coordinate of wall end point in cm")
                .number("height", "Wall height in cm (e.g. 250 for 2.5m)")
                .nullableNumber("heightAtEnd", "Height at end point in cm for sloped walls (null = same as height)")
                .number("thickness", "Wall thickness in cm")
                .nullableNumber("arcExtent",
                        "Arc extent in degrees for curved walls (positive = curve left, negative = curve right, null = straight)")
                .nullableString("color", "Color as '#RRGGBB' for all sides at once, or null to reset all sides")
                .nullableString("leftSideColor", "Left side color as '#RRGGBB', or null to reset")
                .nullableString("rightSideColor", "Right side color as '#RRGGBB', or null to reset")
                .nullableString("topColor", "Top color as '#RRGGBB', or null to reset")
                .number("shininess", "Shininess for both sides: 0.0 (matte) to 1.0 (glossy)")
                .number("leftSideShininess", "Left side shininess: 0.0 (matte) to 1.0 (glossy)")
                .number("rightSideShininess", "Right side shininess: 0.0 (matte) to 1.0 (glossy)")
                .build();
    }

}
