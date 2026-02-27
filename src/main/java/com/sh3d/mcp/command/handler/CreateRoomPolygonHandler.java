package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.ColorParser;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.util.FormatUtil.colorToHex;
import static com.sh3d.mcp.command.util.FormatUtil.round2;
import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "create_room_polygon".
 * Создаёт комнату (Room) по массиву точек полигона.
 */
public class CreateRoomPolygonHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> params = request.getParams();

        // Parse points
        Object pointsObj = params.get("points");
        if (pointsObj == null) {
            return Response.error("Missing required parameter: 'points'");
        }
        if (!(pointsObj instanceof List)) {
            return Response.error("Parameter 'points' must be an array of {x, y} objects");
        }

        @SuppressWarnings("unchecked")
        List<Object> pointsList = (List<Object>) pointsObj;

        if (pointsList.size() < 3) {
            return Response.error("Parameter 'points' must contain at least 3 points, got " + pointsList.size());
        }

        float[][] polygon = new float[pointsList.size()][2];
        for (int i = 0; i < pointsList.size(); i++) {
            Object ptObj = pointsList.get(i);
            if (!(ptObj instanceof Map)) {
                return Response.error("Point at index " + i + " must be an object with 'x' and 'y'");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> pt = (Map<String, Object>) ptObj;

            Object xVal = pt.get("x");
            Object yVal = pt.get("y");
            if (!(xVal instanceof Number) || !(yVal instanceof Number)) {
                return Response.error("Point at index " + i + " must have numeric 'x' and 'y'");
            }
            polygon[i][0] = ((Number) xVal).floatValue();
            polygon[i][1] = ((Number) yVal).floatValue();
        }

        // Validate colors before EDT
        ColorParser.ColorResult floorColorResult = ColorParser.parseNullable(params, "floorColor");
        if (floorColorResult != null && floorColorResult.hasError()) {
            return Response.error(floorColorResult.error);
        }

        ColorParser.ColorResult ceilingColorResult = ColorParser.parseNullable(params, "ceilingColor");
        if (ceilingColorResult != null && ceilingColorResult.hasError()) {
            return Response.error(ceilingColorResult.error);
        }

        final ColorParser.ColorResult finalFloorColor = floorColorResult;
        final ColorParser.ColorResult finalCeilingColor = ceilingColorResult;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Room room = new Room(polygon);

            // Name
            String name = request.getString("name");
            if (name != null) {
                room.setName(name);
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
            if (finalFloorColor != null) {
                room.setFloorColor(finalFloorColor.clear ? null : finalFloorColor.value);
            }
            if (finalCeilingColor != null) {
                room.setCeilingColor(finalCeilingColor.clear ? null : finalCeilingColor.value);
            }

            home.addRoom(room);

            // Build response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", room.getId());
            result.put("name", room.getName());
            result.put("area", round2(room.getArea()));
            result.put("areaVisible", room.isAreaVisible());
            result.put("floorVisible", room.isFloorVisible());
            result.put("ceilingVisible", room.isCeilingVisible());
            result.put("floorColor", colorToHex(room.getFloorColor()));
            result.put("ceilingColor", colorToHex(room.getCeilingColor()));
            result.put("xCenter", round2(room.getXCenter()));
            result.put("yCenter", round2(room.getYCenter()));

            List<Object> pointList = new ArrayList<>();
            for (float[] pt : room.getPoints()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("x", round2(pt[0]));
                p.put("y", round2(pt[1]));
                pointList.add(p);
            }
            result.put("points", pointList);

            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Creates a room (floor/ceiling polygon) from an array of points. "
                + "Rooms define floor and ceiling surfaces, independent of walls. "
                + "Coordinates are in centimeters. Minimum 3 points required. "
                + "Use create_walls for rectangular wall outlines.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> pointSchema = SchemaBuilder.create()
                .requiredNumber("x", "X coordinate in cm")
                .requiredNumber("y", "Y coordinate in cm")
                .build();

        return SchemaBuilder.create()
                .requiredArray("points", SchemaBuilder.arrayDef(
                        "Polygon vertices in cm. Minimum 3 points. "
                                + "Example: [{\"x\":0,\"y\":0},{\"x\":500,\"y\":0},{\"x\":500,\"y\":400},{\"x\":0,\"y\":400}]")
                        .items(pointSchema)
                        .minItems(3)
                        .build())
                .string("name", "Room name (e.g. 'Living Room')")
                .boolWithDefault("floorVisible", "Whether floor is visible", true)
                .boolWithDefault("ceilingVisible", "Whether ceiling is visible", true)
                .nullableString("floorColor",
                        "Floor color as hex '#RRGGBB' (e.g. '#CCBB99'), or null for default")
                .nullableString("ceilingColor",
                        "Ceiling color as hex '#RRGGBB', or null for default")
                .boolWithDefault("areaVisible", "Whether area label is shown", false)
                .build();
    }

}
