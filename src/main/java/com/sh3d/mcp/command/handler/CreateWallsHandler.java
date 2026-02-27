package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Обработчик команды "create_walls".
 * Создаёт 4 стены прямоугольной комнаты и соединяет их.
 *
 * <pre>
 * Параметры:
 *   x, y       — координаты верхнего левого угла (float, см)
 *   width      — ширина (float, см, > 0)
 *   height     — высота (float, см, > 0)
 *   thickness  — толщина стен (float, default 10.0)
 *   wallHeight — высота стен (float, default from Home or 250.0)
 *
 * Логика (в EDT):
 *   1. Рассчитать 4 угла: A(x,y) B(x+w,y) C(x+w,y+h) D(x,y+h)
 *   2. Создать 4 Wall: AB, BC, CD, DA
 *   3. Соединить: w1↔w2↔w3↔w4↔w1
 *   4. home.addWall(w1..w4)
 * </pre>
 */
public class CreateWallsHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        float x = request.getFloat("x");
        float y = request.getFloat("y");
        float width = request.getFloat("width");
        float height = request.getFloat("height");
        float thickness = request.getFloat("thickness", 10.0f);

        if (width <= 0) {
            return Response.error("Parameter 'width' must be positive, got " + width);
        }
        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive, got " + height);
        }
        if (thickness <= 0) {
            return Response.error("Parameter 'thickness' must be positive, got " + thickness);
        }

        float wallHeight = request.getFloat("wallHeight", 0f);

        Wall[] created = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            // Высота: параметр > Home default > 250 см
            float h = wallHeight > 0 ? wallHeight
                    : home.getWallHeight() > 0 ? home.getWallHeight()
                    : 250f;

            // A(x,y) → B(x+w,y) → C(x+w,y+h) → D(x,y+h)
            Wall w1 = new Wall(x, y, x + width, y, thickness);
            Wall w2 = new Wall(x + width, y, x + width, y + height, thickness);
            Wall w3 = new Wall(x + width, y + height, x, y + height, thickness);
            Wall w4 = new Wall(x, y + height, x, y, thickness);

            w1.setHeight(h);
            w2.setHeight(h);
            w3.setHeight(h);
            w4.setHeight(h);

            // Замкнутый контур: w1↔w2↔w3↔w4↔w1
            w1.setWallAtEnd(w2);
            w2.setWallAtStart(w1);
            w2.setWallAtEnd(w3);
            w3.setWallAtStart(w2);
            w3.setWallAtEnd(w4);
            w4.setWallAtStart(w3);
            w4.setWallAtEnd(w1);
            w1.setWallAtStart(w4);

            home.addWall(w1);
            home.addWall(w2);
            home.addWall(w3);
            home.addWall(w4);

            return new Wall[]{w1, w2, w3, w4};
        });

        List<String> wallIds = new ArrayList<>(4);
        for (Wall w : created) {
            wallIds.add(w.getId());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wallsCreated", 4);
        data.put("wallIds", wallIds);
        data.put("message", "Room " + (int) width + "x" + (int) height + " created");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Creates a rectangular room by adding 4 connected walls in Sweet Home 3D. "
                + "Coordinates and dimensions are in centimeters (e.g., 500 = 5 meters). "
                + "The coordinate system has X pointing right and Y pointing down. "
                + "Returns wallIds array (top, right, bottom, left) for use with "
                + "connect_walls, modify_wall, place_door_or_window, delete_wall.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredNumber("x", "X coordinate of top-left corner in cm")
                .requiredNumber("y", "Y coordinate of top-left corner in cm")
                .requiredNumber("width", "Room width in cm (e.g., 500 = 5m)")
                .requiredNumber("height", "Room height/depth in cm (e.g., 400 = 4m)")
                .numberWithDefault("thickness", "Wall thickness in cm", 10)
                .numberWithDefault("wallHeight", "Wall height in cm (0 = use default)", 0)
                .build();
    }

}
