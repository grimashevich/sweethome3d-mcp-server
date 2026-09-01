package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "modify_dimension_line".
 * Изменяет свойства размерной линии по стабильному ID.
 *
 * Смещение (offset) отсчитывается перпендикулярно направлению от start к end:
 * положительное значение сдвигает линию вправо при движении от start к end.
 */
public class ModifyDimensionLineHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "xStart", "yStart", "xEnd", "yEnd", "offset"
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
                    + "Supported: xStart, yStart, xEnd, yEnd, offset");
        }

        // Validate types before EDT so callers get a clear message
        for (String key : MODIFIABLE_KEYS) {
            if (params.containsKey(key) && !(params.get(key) instanceof Number)) {
                return Response.error("Parameter '" + key + "' must be a number, got: "
                        + params.get(key));
            }
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            DimensionLine dim = ObjectResolver.findDimensionLine(home, id);

            if (dim == null) {
                return null;
            }

            if (params.containsKey("xStart")) {
                dim.setXStart(request.getFloat("xStart"));
            }
            if (params.containsKey("yStart")) {
                dim.setYStart(request.getFloat("yStart"));
            }
            if (params.containsKey("xEnd")) {
                dim.setXEnd(request.getFloat("xEnd"));
            }
            if (params.containsKey("yEnd")) {
                dim.setYEnd(request.getFloat("yEnd"));
            }
            if (params.containsKey("offset")) {
                dim.setOffset(request.getFloat("offset"));
            }

            return FormatUtil.buildDimensionLineInfo(dim);
        });

        if (data == null) {
            return Response.error("Dimension line not found: id '" + id + "'");
        }

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Modifies an existing dimension line on the 2D plan by ID. "
                + "Use get_state to find dimension line IDs. "
                + "Only provided properties are changed; omitted ones remain unchanged. "
                + "Moving the endpoints changes the measured length, which is always recalculated. "
                + "Offset is the perpendicular distance of the line from the measured segment "
                + "(positive = above/left, negative = below/right); "
                + "use it to shift a dimension outside a room without changing what it measures.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Dimension line ID from get_state")
                .number("xStart", "New X coordinate of the start point in centimeters")
                .number("yStart", "New Y coordinate of the start point in centimeters")
                .number("xEnd", "New X coordinate of the end point in centimeters")
                .number("yEnd", "New Y coordinate of the end point in centimeters")
                .number("offset", "Perpendicular distance (cm) of the dimension line from the "
                        + "measured segment. Positive = above/left, negative = below/right")
                .build();
    }

}
