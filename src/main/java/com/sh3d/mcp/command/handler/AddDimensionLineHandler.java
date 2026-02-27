package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import static com.sh3d.mcp.command.util.FormatUtil.round2;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "add_dimension_line".
 * Добавляет размерную линию (аннотацию измерения) на 2D-план.
 */
public class AddDimensionLineHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> params = request.getParams();

        // Required: xStart, yStart, xEnd, yEnd
        Object xsVal = params.get("xStart");
        Object ysVal = params.get("yStart");
        Object xeVal = params.get("xEnd");
        Object yeVal = params.get("yEnd");

        if (!(xsVal instanceof Number) || !(ysVal instanceof Number)) {
            return Response.error("Missing required numeric parameters: 'xStart' and 'yStart'");
        }
        if (!(xeVal instanceof Number) || !(yeVal instanceof Number)) {
            return Response.error("Missing required numeric parameters: 'xEnd' and 'yEnd'");
        }

        // Optional: offset (default 25)
        float parsedOffset = 25.0f;
        Object offVal = params.get("offset");
        if (offVal != null) {
            if (!(offVal instanceof Number)) {
                return Response.error("Parameter 'offset' must be a number, got: " + offVal);
            }
            parsedOffset = ((Number) offVal).floatValue();
        }

        float xStart = ((Number) xsVal).floatValue();
        float yStart = ((Number) ysVal).floatValue();
        float xEnd = ((Number) xeVal).floatValue();
        float yEnd = ((Number) yeVal).floatValue();
        final float offset = parsedOffset;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            DimensionLine dim = new DimensionLine(xStart, yStart, xEnd, yEnd, offset);
            home.addDimensionLine(dim);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", dim.getId());
            result.put("xStart", round2(dim.getXStart()));
            result.put("yStart", round2(dim.getYStart()));
            result.put("xEnd", round2(dim.getXEnd()));
            result.put("yEnd", round2(dim.getYEnd()));
            result.put("offset", round2(dim.getOffset()));
            result.put("length", round2(dim.getLength()));
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Add a dimension line (measurement annotation) to the 2D plan. "
                + "Shows the distance between two points with extension lines and an auto-calculated length label. "
                + "All coordinates in centimeters. "
                + "Offset controls perpendicular distance of the label from the measured line "
                + "(positive = above/left, negative = below/right, typical value: 20-50).";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredNumber("xStart", "X coordinate of the start point in centimeters")
                .requiredNumber("yStart", "Y coordinate of the start point in centimeters")
                .requiredNumber("xEnd", "X coordinate of the end point in centimeters")
                .requiredNumber("yEnd", "Y coordinate of the end point in centimeters")
                .numberWithDefault("offset",
                        "Perpendicular distance (cm) of the dimension label from the measured line. "
                                + "Positive = above/left, negative = below/right. Typical: 20-50. Default: 25",
                        25)
                .build();
    }

}
