package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.FormatUtil;
import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.command.util.ValidationUtil;

import java.util.Map;

/**
 * Обработчик команды "add_dimension_line".
 * Добавляет размерную линию (аннотацию измерения) на 2D-план.
 */
public class AddDimensionLineHandler implements CommandHandler, CommandDescriptor {

    /**
     * Сторона, в которую уходит линия при положительном смещении, — общий текст описаний
     * add_dimension_line и modify_dimension_line. SH3D (DimensionLine.getPoints) сдвигает линию на
     * (-sin a, cos a) * offset, где a — направление от start к end; ось Y плана направлена вниз,
     * поэтому на экране это правая сторона по ходу от start к end.
     */
    static final String OFFSET_SIDE = "Positive = on the right-hand side when going from start to end "
            + "(below a left-to-right line, left of a top-to-bottom line; the plan's Y axis points down), "
            + "negative = on the opposite side";

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

        // 1e40 — число, но во float это бесконечность: линия с таким концом ломает план.
        // offset: отсутствует или null — умолчание 25, иначе то же правило, что у концов
        Object offVal = params.get("offset");
        String numberError = ValidationUtil.validateFiniteNumbers(params, "xStart", "yStart", "xEnd", "yEnd");
        if (numberError == null && offVal != null) {
            numberError = ValidationUtil.validateFiniteNumbers(params, "offset");
        }
        if (numberError != null) {
            return Response.error(numberError);
        }
        float offset = offVal != null ? ((Number) offVal).floatValue() : 25.0f;

        // Линия ещё не в Home — проверяем её геометрию до EDT
        DimensionLine dim = new DimensionLine(((Number) xsVal).floatValue(), ((Number) ysVal).floatValue(),
                ((Number) xeVal).floatValue(), ((Number) yeVal).floatValue(), offset);
        String tooLarge = geometryError(dim);
        if (tooLarge != null) {
            return Response.error(tooLarge);
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            home.addDimensionLine(dim);

            return FormatUtil.buildDimensionLineInfo(dim);
        });

        return Response.ok(data);
    }

    /**
     * Конечные концы и смещение ещё не значат конечную линию: SH3D считает длину и точки во float,
     * и квадрат разности концов около 2e19 или сумма координаты со смещением переполняются в
     * бесконечность. Проверяется то, что SH3D рисует, — длина и точки самой линии.
     *
     * @return текст ошибки или {@code null}, если длина и все точки линии конечны
     */
    static String geometryError(DimensionLine line) {
        boolean finite = Float.isFinite(line.getLength());
        for (float[] point : line.getPoints()) {
            finite &= Float.isFinite(point[0]) && Float.isFinite(point[1]);
        }
        return finite ? null
                : "Dimension line is too large: its length or drawn position overflows to infinity. "
                        + "Use coordinates and an offset within the plan's extent";
    }

    @Override
    public String getDescription() {
        return "Add a dimension line (measurement annotation) to the 2D plan. "
                + "Shows the distance between two points with extension lines and an auto-calculated length label. "
                + "All coordinates in centimeters. "
                + "Offset is the perpendicular distance from the measured segment to the dimension line "
                + "(typical value: 20-50). " + OFFSET_SIDE + ".";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredNumber("xStart", "X coordinate of the start point in centimeters")
                .requiredNumber("yStart", "Y coordinate of the start point in centimeters")
                .requiredNumber("xEnd", "X coordinate of the end point in centimeters")
                .requiredNumber("yEnd", "Y coordinate of the end point in centimeters")
                .numberWithDefault("offset",
                        "Perpendicular distance (cm) from the measured segment to the dimension line. "
                                + OFFSET_SIDE + ". Typical: 20-50. Default: 25",
                        25)
                .build();
    }

}
