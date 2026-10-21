package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.ColorParser;
import com.sh3d.mcp.command.util.LabelStyleUtil;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.TextStyle;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.command.util.ValidationUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "modify_label".
 * Изменяет текст, положение, поворот, цвета, стиль и 3D-параметры текстовой метки по стабильному ID.
 *
 * Метка без собственного стиля рисуется стилем по умолчанию из настроек SH3D; при изменении
 * fontSize/bold/italic/alignment такой метке стиль строится от него, чтобы не заданные поля
 * сохранили видимые значения.
 */
public class ModifyLabelHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "text", "x", "y", "angle", "color", "outlineColor",
            "fontSize", "bold", "italic", "alignment", "elevation", "pitch"
    );

    private static final String[] NUMBER_KEYS = {"x", "y", "angle", "fontSize", "elevation"};

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> params = request.getParams();
        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. Supported: "
                    + String.join(", ", MODIFIABLE_KEYS));
        }

        // Validate everything before EDT so callers get a clear message and a bad value changes nothing
        String error = validate(params);
        if (error != null) {
            return Response.error(error);
        }

        ColorParser.ColorResult color = ColorParser.parseNullable(params, "color");
        if (color != null && color.hasError()) {
            return Response.error(color.error);
        }
        ColorParser.ColorResult outlineColor = ColorParser.parseNullable(params, "outlineColor");
        if (outlineColor != null && outlineColor.hasError()) {
            return Response.error(outlineColor.error);
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Label label = ObjectResolver.findLabel(home, id);

            if (label == null) {
                return null;
            }

            apply(label, params, color, outlineColor, accessor.getUserPreferences());

            Map<String, Object> result = FormatUtil.buildLabelDetails(label);
            result.put("level", FormatUtil.levelName(label.getLevel()));
            return result;
        });

        if (data == null) {
            return Response.error("Label not found: id '" + id + "'");
        }

        return Response.ok(data);
    }

    private static String validate(Map<String, Object> params) {
        if (params.containsKey("text")) {
            Object text = params.get("text");
            if (!(text instanceof String) || ((String) text).isEmpty()) {
                return "Parameter 'text' must be a non-empty string";
            }
        }

        String numberError = ValidationUtil.validateFiniteNumbers(params, NUMBER_KEYS);
        if (numberError != null) {
            return numberError;
        }
        if (params.containsKey("fontSize") && ((Number) params.get("fontSize")).floatValue() <= 0) {
            return "Parameter 'fontSize' must be greater than 0, got: " + params.get("fontSize");
        }

        // pitch: null = flat on the plan, otherwise a finite number of degrees
        if (params.containsKey("pitch") && params.get("pitch") != null) {
            String pitchError = ValidationUtil.validateFiniteNumbers(params, "pitch");
            if (pitchError != null) {
                return pitchError;
            }
        }

        for (String key : new String[] {"bold", "italic"}) {
            if (params.containsKey(key) && !(params.get(key) instanceof Boolean)) {
                return "Parameter '" + key + "' must be true or false, got: " + params.get(key);
            }
        }

        if (params.containsKey("alignment")) {
            Object align = params.get("alignment");
            if (parseAlignment(align) == null) {
                return "Invalid alignment: '" + align + "'. Expected LEFT, CENTER, or RIGHT";
            }
        }
        return null;
    }

    private static TextStyle.Alignment parseAlignment(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        try {
            return TextStyle.Alignment.valueOf(((String) value).toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void apply(Label label, Map<String, Object> params,
                              ColorParser.ColorResult color, ColorParser.ColorResult outlineColor,
                              UserPreferences preferences) {
        if (params.containsKey("text")) {
            label.setText((String) params.get("text"));
        }
        if (params.containsKey("x")) {
            label.setX(floatParam(params, "x"));
        }
        if (params.containsKey("y")) {
            label.setY(floatParam(params, "y"));
        }
        if (params.containsKey("angle")) {
            label.setAngle((float) Math.toRadians(floatParam(params, "angle")));
        }
        if (color != null) {
            label.setColor(color.clear ? null : color.value);
        }
        if (outlineColor != null) {
            label.setOutlineColor(outlineColor.clear ? null : outlineColor.value);
        }
        if (params.containsKey("elevation")) {
            label.setElevation(floatParam(params, "elevation"));
        }
        if (params.containsKey("pitch")) {
            Object pitch = params.get("pitch");
            label.setPitch(pitch != null
                    ? Float.valueOf((float) Math.toRadians(((Number) pitch).floatValue())) : null);
        }
        applyStyle(label, params, preferences);
    }

    private static void applyStyle(Label label, Map<String, Object> params, UserPreferences preferences) {
        boolean hasStyle = params.containsKey("fontSize") || params.containsKey("bold")
                || params.containsKey("italic") || params.containsKey("alignment");
        // SH3D displays a label in 3D only if both pitch and style are set.
        // Only an explicitly supplied numeric pitch requests a style for 3D.
        boolean hasNumericPitch = params.get("pitch") instanceof Number;
        if (!hasStyle && !hasNumericPitch) {
            return;
        }

        TextStyle style = label.getStyle() != null ? label.getStyle() : LabelStyleUtil.defaultStyle(preferences);
        if (params.containsKey("fontSize")) {
            style = style.deriveStyle(floatParam(params, "fontSize"));
        }
        if (params.containsKey("bold")) {
            style = style.deriveBoldStyle((Boolean) params.get("bold"));
        }
        if (params.containsKey("italic")) {
            style = style.deriveItalicStyle((Boolean) params.get("italic"));
        }
        if (params.containsKey("alignment")) {
            style = style.deriveStyle(parseAlignment(params.get("alignment")));
        }
        label.setStyle(style);
    }

    private static float floatParam(Map<String, Object> params, String key) {
        return ((Number) params.get(key)).floatValue();
    }

    @Override
    public String getDescription() {
        return "Modifies an existing text label on the 2D plan by ID. "
                + "Use get_state to find label IDs. "
                + "Only provided properties are changed; omitted ones remain unchanged. "
                + "fontSize, bold, italic and alignment can each be changed on their own; "
                + "a label without its own text style starts from Sweet Home 3D's default label style. "
                + "Works on labels of any level; the label stays on its level.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Label ID from get_state")
                .string("text", "New label text (non-empty)")
                .number("x", "New X position in centimeters")
                .number("y", "New Y position in centimeters")
                .number("angle", "Rotation angle in degrees (clockwise)")
                .nullableString("color",
                        "Text color as hex '#RRGGBB', or null for the default color")
                .nullableString("outlineColor",
                        "Outline color as hex '#RRGGBB', or null for no outline")
                .number("fontSize", "Font size in points (greater than 0)")
                .bool("bold", "Bold text")
                .bool("italic", "Italic text")
                .enumProp("alignment", "Text alignment", "LEFT", "CENTER", "RIGHT")
                .number("elevation", "Elevation in 3D view (cm). 0 = on the floor")
                .nullableNumber("pitch",
                        "Pitch angle in degrees for 3D view. null = flat on plan. "
                                + "90 = vertical (like a wall sign)")
                .build();
    }

}
