package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Стены: четыре стены прямоугольником доходят до модели с заданными координатами, толщиной и высотой.
 */
class WallsLiveIT extends LiveTestBase {

    private static final double X = 100;
    private static final double Y = 50;
    private static final double WIDTH = 600;
    private static final double DEPTH = 400;
    private static final double THICKNESS = 20;
    private static final double WALL_HEIGHT = 270;

    /** Углы прямоугольника по часовой стрелке от левого верхнего (ось Y направлена вниз). */
    private static final double[][] CORNERS = {
        {X, Y}, {X + WIDTH, Y}, {X + WIDTH, Y + DEPTH}, {X, Y + DEPTH}
    };

    @Test
    void fourSingleWallsAreStoredWithGivenGeometry() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double[] from = CORNERS[i];
            double[] to = CORNERS[(i + 1) % 4];
            Map<String, Object> created = mcp.call("create_wall",
                    "xStart", from[0], "yStart", from[1], "xEnd", to[0], "yEnd", to[1],
                    "thickness", THICKNESS, "height", WALL_HEIGHT);
            ids.add(str(created, "id"));
        }
        assertEquals(4, new HashSet<>(ids).size(), "every wall must get its own id: " + ids);

        // Ответ get_state
        Map<String, Object> state = state();
        assertEquals(4.0, num(state, "wallCount"), 0.0, "wallCount");
        List<Map<String, Object>> walls = objects(state, "walls");
        assertEquals(4, walls.size(), "walls in get_state");
        for (int i = 0; i < 4; i++) {
            Map<String, Object> wall = byId(walls, ids.get(i));
            double[] from = CORNERS[i];
            double[] to = CORNERS[(i + 1) % 4];
            String what = "get_state wall " + i;
            assertEquals(from[0], num(wall, "xStart"), RESPONSE_EPS, what + " xStart");
            assertEquals(from[1], num(wall, "yStart"), RESPONSE_EPS, what + " yStart");
            assertEquals(to[0], num(wall, "xEnd"), RESPONSE_EPS, what + " xEnd");
            assertEquals(to[1], num(wall, "yEnd"), RESPONSE_EPS, what + " yEnd");
            assertEquals(THICKNESS, num(wall, "thickness"), RESPONSE_EPS, what + " thickness");
            assertEquals(WALL_HEIGHT, num(wall, "height"), RESPONSE_EPS, what + " height");
        }

        // Модель в сохранённом файле
        Home home = saveAndReadHome("four-walls");
        assertEquals(4, home.getWalls().size(), "walls in the saved home");
        for (int i = 0; i < 4; i++) {
            Wall wall = wallById(home, ids.get(i));
            double[] from = CORNERS[i];
            double[] to = CORNERS[(i + 1) % 4];
            String what = "saved wall " + i;
            assertEquals(from[0], wall.getXStart(), MODEL_EPS, what + " xStart");
            assertEquals(from[1], wall.getYStart(), MODEL_EPS, what + " yStart");
            assertEquals(to[0], wall.getXEnd(), MODEL_EPS, what + " xEnd");
            assertEquals(to[1], wall.getYEnd(), MODEL_EPS, what + " yEnd");
            assertEquals(THICKNESS, wall.getThickness(), MODEL_EPS, what + " thickness");
            assertNotNull(wall.getHeight(), what + " height must be set");
            assertEquals(WALL_HEIGHT, wall.getHeight(), MODEL_EPS, what + " height");
        }
    }

    @Test
    void createWallsBuildsConnectedRectangle() {
        Map<String, Object> created = mcp.call("create_walls",
                "x", X, "y", Y, "width", WIDTH, "height", DEPTH,
                "thickness", THICKNESS, "wallHeight", WALL_HEIGHT);

        // По описанию команды: wallIds — массив в порядке top, right, bottom, left
        Object wallIds = created.get("wallIds");
        assertTrue(wallIds instanceof List, "create_walls must return a wallIds array: " + created);
        List<String> ids = new ArrayList<>();
        for (Object id : (List<?>) wallIds) {
            ids.add((String) id);
        }
        assertEquals(4, ids.size(), "wallIds: " + ids);
        assertEquals(4, new HashSet<>(ids).size(), "wallIds must be distinct: " + ids);

        assertEquals(4.0, num(state(), "wallCount"), 0.0, "wallCount in get_state");

        Home home = saveAndReadHome("create-walls");
        assertEquals(4, home.getWalls().size(), "walls in the saved home");

        String[] sides = {"top", "right", "bottom", "left"};
        Set<Wall> rectangle = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            Wall wall = wallById(home, ids.get(i));
            rectangle.add(wall);
            double[] a = CORNERS[i];
            double[] b = CORNERS[(i + 1) % 4];
            // Направление стены описанием не задано — концы сравниваются как неупорядоченная пара
            boolean forward = near(wall.getXStart(), wall.getYStart(), a) && near(wall.getXEnd(), wall.getYEnd(), b);
            boolean backward = near(wall.getXStart(), wall.getYStart(), b) && near(wall.getXEnd(), wall.getYEnd(), a);
            assertTrue(forward || backward, sides[i] + " wall must join (" + a[0] + "," + a[1] + ") and ("
                    + b[0] + "," + b[1] + "), got (" + wall.getXStart() + "," + wall.getYStart() + ")->("
                    + wall.getXEnd() + "," + wall.getYEnd() + ")");
            assertEquals(THICKNESS, wall.getThickness(), MODEL_EPS, sides[i] + " wall thickness");
            assertNotNull(wall.getHeight(), sides[i] + " wall height must be set");
            assertEquals(WALL_HEIGHT, wall.getHeight(), MODEL_EPS, sides[i] + " wall height");
        }

        // «4 connected walls»: у каждой стены на обоих концах сосед из той же четвёрки, и он касается этого конца
        for (int i = 0; i < 4; i++) {
            Wall wall = wallById(home, ids.get(i));
            Wall atStart = wall.getWallAtStart();
            Wall atEnd = wall.getWallAtEnd();
            assertNotNull(atStart, sides[i] + " wall is not connected at its start");
            assertNotNull(atEnd, sides[i] + " wall is not connected at its end");
            assertTrue(rectangle.contains(atStart) && atStart != wall, sides[i] + " wall: neighbour at start");
            assertTrue(rectangle.contains(atEnd) && atEnd != wall, sides[i] + " wall: neighbour at end");
            assertTrue(touches(atStart, wall.getXStart(), wall.getYStart()),
                    sides[i] + " wall: the neighbour at start does not touch the start point");
            assertTrue(touches(atEnd, wall.getXEnd(), wall.getYEnd()),
                    sides[i] + " wall: the neighbour at end does not touch the end point");
        }
    }

    private static boolean near(float x, float y, double[] point) {
        return Math.abs(x - point[0]) <= MODEL_EPS && Math.abs(y - point[1]) <= MODEL_EPS;
    }

    private static boolean touches(Wall wall, float x, float y) {
        return near(wall.getXStart(), wall.getYStart(), new double[] {x, y})
                || near(wall.getXEnd(), wall.getYEnd(), new double[] {x, y});
    }
}
