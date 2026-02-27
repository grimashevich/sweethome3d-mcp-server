package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.ColorParser;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.TextStyle;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.colorToHex;
import static com.sh3d.mcp.command.util.FormatUtil.round2;
import static com.sh3d.mcp.command.util.SchemaUtil.enumProp;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "add_label".
 * Добавляет текстовую метку (аннотацию) на 2D-план.
 */
public class AddLabelHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> params = request.getParams();

        // Required: text
        String text = request.getString("text");
        if (text == null || text.isEmpty()) {
            return Response.error("Missing required parameter: 'text'");
        }

        // Required: x, y
        Object xVal = params.get("x");
        Object yVal = params.get("y");
        if (!(xVal instanceof Number) || !(yVal instanceof Number)) {
            return Response.error("Missing required numeric parameters: 'x' and 'y'");
        }
        float x = ((Number) xVal).floatValue();
        float y = ((Number) yVal).floatValue();

        // Optional: color
        ColorParser.ColorResult colorResult = ColorParser.parseNullable(params, "color");
        if (colorResult != null && colorResult.hasError()) {
            return Response.error(colorResult.error);
        }

        // Optional: outlineColor
        ColorParser.ColorResult outlineColorResult = ColorParser.parseNullable(params, "outlineColor");
        if (outlineColorResult != null && outlineColorResult.hasError()) {
            return Response.error(outlineColorResult.error);
        }

        // Optional: angle (degrees)
        float angle = request.getFloat("angle", 0f);

        // Optional: elevation
        float elevation = request.getFloat("elevation", 0f);

        // Optional: pitch (degrees, nullable)
        Float pitch = null;
        boolean hasPitch = params.containsKey("pitch");
        if (hasPitch) {
            Object pitchVal = params.get("pitch");
            if (pitchVal instanceof Number) {
                pitch = (float) Math.toRadians(((Number) pitchVal).floatValue());
            }
            // null pitch = horizontal (on plan) — leave as null
        }

        // Optional: TextStyle (fontSize triggers creation)
        TextStyle style = null;
        if (params.containsKey("fontSize")) {
            float fontSize = request.getFloat("fontSize");
            Boolean bold = request.getBoolean("bold");
            Boolean italic = request.getBoolean("italic");

            TextStyle.Alignment alignment = TextStyle.Alignment.CENTER;
            String alignStr = request.getString("alignment");
            if (alignStr != null) {
                try {
                    alignment = TextStyle.Alignment.valueOf(alignStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Response.error("Invalid alignment: '" + alignStr
                            + "'. Expected LEFT, CENTER, or RIGHT");
                }
            }

            style = new TextStyle(null, fontSize, bold != null && bold, italic != null && italic, alignment);
        }

        final ColorParser.ColorResult finalColor = colorResult;
        final ColorParser.ColorResult finalOutlineColor = outlineColorResult;
        final Float finalPitch = pitch;
        final boolean setPitch = hasPitch;
        final TextStyle finalStyle = style;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Label label = new Label(text, x, y);

            if (angle != 0f) {
                label.setAngle((float) Math.toRadians(angle));
            }

            if (finalColor != null) {
                label.setColor(finalColor.clear ? null : finalColor.value);
            }

            if (finalOutlineColor != null) {
                label.setOutlineColor(finalOutlineColor.clear ? null : finalOutlineColor.value);
            }

            if (elevation != 0f) {
                label.setElevation(elevation);
            }

            if (setPitch) {
                label.setPitch(finalPitch);
            }

            if (finalStyle != null) {
                label.setStyle(finalStyle);
            }

            home.addLabel(label);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", label.getId());
            result.put("text", label.getText());
            result.put("x", round2(label.getX()));
            result.put("y", round2(label.getY()));
            result.put("angle", round2((float) Math.toDegrees(label.getAngle())));
            result.put("color", colorToHex(label.getColor()));
            result.put("outlineColor", colorToHex(label.getOutlineColor()));
            result.put("elevation", round2(label.getElevation()));
            Float lp = label.getPitch();
            result.put("pitch", lp != null ? round2((float) Math.toDegrees(lp)) : null);
            if (label.getStyle() != null) {
                TextStyle ts = label.getStyle();
                Map<String, Object> styleMap = new LinkedHashMap<>();
                styleMap.put("fontSize", ts.getFontSize());
                styleMap.put("bold", ts.isBold());
                styleMap.put("italic", ts.isItalic());
                styleMap.put("alignment", ts.getAlignment().name());
                result.put("style", styleMap);
            }
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Add a text label (annotation) to the 2D plan. "
                + "Labels can display room names, dimensions, notes, or any text. "
                + "Coordinates are in centimeters. "
                + "Optionally set font size, style, color, rotation angle, and 3D elevation.";
    }

    @Override
    public Map<String, Object> getSchema() {
        // alignment: enum with default — use raw escape hatch
        Map<String, Object> alignProp = enumProp(
                "Text alignment (requires fontSize). Default: CENTER",
                "LEFT", "CENTER", "RIGHT");
        alignProp.put("default", "CENTER");

        return SchemaBuilder.create()
                .requiredString("text", "Label text content (e.g. 'Living Room', '5.0m', 'Entry')")
                .requiredNumber("x", "X position in centimeters")
                .requiredNumber("y", "Y position in centimeters")
                .nullableString("color",
                        "Text color as hex '#RRGGBB' (e.g. '#000000' for black), or null for default")
                .nullableString("outlineColor",
                        "Outline color as hex '#RRGGBB', or null for no outline")
                .numberWithDefault("angle", "Rotation angle in degrees (clockwise)", 0)
                .number("fontSize", "Font size in points (e.g. 18, 24, 36). If set, creates a TextStyle")
                .boolWithDefault("bold", "Bold text (requires fontSize)", false)
                .boolWithDefault("italic", "Italic text (requires fontSize)", false)
                .raw("alignment", alignProp)
                .numberWithDefault("elevation", "Elevation in 3D view (cm). 0 = on the floor", 0)
                .nullableNumber("pitch",
                        "Pitch angle in degrees for 3D view. null = flat on plan (default). "
                                + "90 = vertical (like a wall sign)")
                .build();
    }

}
