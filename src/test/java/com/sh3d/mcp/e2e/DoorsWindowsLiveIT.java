package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.io.DefaultFurnitureCatalog;
import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Wall;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Двери и окна: изделие каталога, поставленное в стену, становится в модели настоящей дверью
 * ({@link HomeDoorOrWindow}), привязано к стене и вписано в её толщину так, как это делает сам
 * Sweet Home 3D при перетаскивании (CHANGELOG, раздел Fixed).
 * <p>
 * Изделия берутся из ответа {@code list_furniture_catalog}, имена и идентификаторы в тесте не
 * зашиты. Эталон «что было в каталоге» — тот же каталог по умолчанию, прочитанный здесь через API
 * Sweet Home 3D, а не ответ плагина.
 */
class DoorsWindowsLiveIT extends LiveTestBase {

    /** Допуск геометрии посадки в стену, см: Sweet Home 3D добавляет к глубине доли миллиметра. */
    private static final double FIT_EPS = 0.05;

    @Test
    void catalogDoorBecomesRealDoorBoundToWallAndFittedIntoIt() {
        Map<String, Object> item = catalogDoorsAndWindows().get(0);
        String catalogId = str(item, "catalogId");

        Map<String, Object> walls = mcp.call("create_walls",
                "x", 100, "y", 50, "width", 600, "height", 400, "thickness", 20);
        String topWallId = (String) ((List<?>) walls.get("wallIds")).get(0);

        Map<String, Object> placed = mcp.call("place_door_or_window",
                "catalogId", catalogId, "wallId", topWallId, "position", 0.25);
        String pieceId = str(placed, "id");

        // Ответ get_state: изделие числится дверью/окном и несёт счётчик створок
        Map<String, Object> inState = byId(objects(state(), "furniture"), pieceId);
        assertEquals(Boolean.TRUE, inState.get("isDoorOrWindow"), "get_state isDoorOrWindow: " + inState);
        assertEquals(catalogId, inState.get("catalogId"), "get_state catalogId: " + inState);

        // Модель
        Home home = saveAndReadHome("door-in-wall");
        HomePieceOfFurniture piece = pieceById(home, pieceId);
        assertTrue(piece instanceof HomeDoorOrWindow,
                "the saved piece must be a HomeDoorOrWindow, got " + piece.getClass().getSimpleName());
        HomeDoorOrWindow door = (HomeDoorOrWindow) piece;
        Wall wall = wallById(home, topWallId);

        assertTrue(door.isBoundToWall(), "a door placed into a straight wall must be bound to it");
        List<String> problems = wallFitProblems(door, wall, 0.25);
        assertTrue(problems.isEmpty(), "the door is not fitted into the wall: " + problems);

        // Данные каталога о проёме не потеряны
        CatalogDoorOrWindow catalog = catalogDoorOrWindow(catalogId);
        assertEquals(catalog.getSashes().length, door.getSashes().length, "sashes kept from the catalog");
        assertEquals(catalog.getCutOutShape(), door.getCutOutShape(), "cut-out shape kept from the catalog");
        assertEquals(catalog.getWallThickness(), door.getWallThickness(), MODEL_EPS, "wallThickness kept");
        assertEquals(catalog.getWallDistance(), door.getWallDistance(), MODEL_EPS, "wallDistance kept");
        assertEquals(door.getSashes().length, num(inState, "sashes"), 0.0, "get_state sashes count");
        assertEquals(door.getSashes().length, num(placed, "sashes"), 0.0, "place_door_or_window sashes count");
    }

    @Test
    void everyCatalogDoorAndWindowFitsADiagonalWall() {
        // Наклонная стена: посадка проверяется не только вдоль осей
        Map<String, Object> wallResponse = mcp.call("create_wall",
                "xStart", 100, "yStart", 100, "xEnd", 400, "yEnd", 500, "thickness", 24, "height", 300);
        String wallId = str(wallResponse, "id");

        List<Map<String, Object>> items = catalogDoorsAndWindows();
        List<String> pieceIds = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String catalogId = str(item, "catalogId");
            ToolResult result = mcp.callTool("place_door_or_window",
                    McpLiveClient.args("catalogId", catalogId, "wallId", wallId, "position", 0.5));
            if (result.isError()) {
                problems.add(catalogId + ": " + result.text());
                pieceIds.add(null);
            } else {
                pieceIds.add(str(result.json(), "id"));
            }
        }

        Home home = saveAndReadHome("all-doors-and-windows");
        Wall wall = wallById(home, wallId);
        for (int i = 0; i < items.size(); i++) {
            if (pieceIds.get(i) == null) {
                continue;
            }
            String catalogId = str(items.get(i), "catalogId");
            HomePieceOfFurniture piece = pieceById(home, pieceIds.get(i));
            if (!(piece instanceof HomeDoorOrWindow)) {
                problems.add(catalogId + ": saved as " + piece.getClass().getSimpleName());
                continue;
            }
            HomeDoorOrWindow door = (HomeDoorOrWindow) piece;
            if (!door.isBoundToWall()) {
                problems.add(catalogId + ": not bound to the wall");
            }
            for (String problem : wallFitProblems(door, wall, 0.5)) {
                problems.add(catalogId + ": " + problem);
            }
            CatalogDoorOrWindow catalog = catalogDoorOrWindow(catalogId);
            if (catalog.getSashes().length != door.getSashes().length) {
                problems.add(catalogId + ": " + door.getSashes().length + " sashes, the catalog has "
                        + catalog.getSashes().length);
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " problem(s) among " + items.size()
                + " catalog doors and windows:\n  " + String.join("\n  ", problems));
    }

    /**
     * CHANGELOG: на круглой стене изделие ставится на саму дугу, {@code position} отмеряется вдоль
     * дуги, изделие повёрнуто по касательной (раньше оно вставало на хорду, в стороне от стены).
     */
    @Test
    void doorOnRoundWallSitsOnTheArcAlongTheTangent() {
        String catalogId = str(catalogDoorsAndWindows().get(0), "catalogId");
        String wallId = str(mcp.call("create_wall",
                "xStart", 0, "yStart", 0, "xEnd", 600, "yEnd", 0, "thickness", 20), "id");
        mcp.call("modify_wall", "id", wallId, "arcExtent", 90);
        double position = 0.25;

        Map<String, Object> placed = mcp.call("place_door_or_window",
                "catalogId", catalogId, "wallId", wallId, "position", position);

        Home home = saveAndReadHome("door-in-round-wall");
        Wall wall = wallById(home, wallId);
        assertNotNull(wall.getArcExtent(), "modify_wall arcExtent must make the wall round");
        double extent = Math.abs(wall.getArcExtent());
        assertEquals(Math.toRadians(90), extent, 1e-4, "arcExtent is given in degrees");
        HomePieceOfFurniture piece = pieceById(home, str(placed, "id"));
        assertTrue(piece instanceof HomeDoorOrWindow, "saved as " + piece.getClass().getSimpleName());
        HomeDoorOrWindow door = (HomeDoorOrWindow) piece;

        // Окружность дуги — из самой модели Sweet Home 3D
        double cx = wall.getXArcCircleCenter();
        double cy = wall.getYArcCircleCenter();
        double radius = Math.hypot(wall.getXStart() - cx, wall.getYStart() - cy);
        // Середина рамы: от центра изделия вдоль его оси глубины
        double depth = door.getDepth();
        double toFrameCentre = (door.getWallDistance() + door.getWallThickness() / 2) * depth - depth / 2;
        double frameX = door.getX() - Math.sin(door.getAngle()) * toFrameCentre;
        double frameY = door.getY() + Math.cos(door.getAngle()) * toFrameCentre;

        double fromArc = Math.hypot(frameX - cx, frameY - cy) - radius;
        double fromStart = angleBetween(wall.getXStart() - cx, wall.getYStart() - cy, door.getX() - cx, door.getY() - cy);
        double toEnd = angleBetween(door.getX() - cx, door.getY() - cy, wall.getXEnd() - cx, wall.getYEnd() - cy);
        // Касательная: ось ширины изделия перпендикулярна радиусу, проведённому в его центр
        double radial = Math.hypot(door.getX() - cx, door.getY() - cy);
        double alongRadius = (Math.cos(door.getAngle()) * (door.getX() - cx)
                + Math.sin(door.getAngle()) * (door.getY() - cy)) / radial;

        assertAll("door on a round wall",
                // Как при перетаскивании в самом Sweet Home 3D: середина рамы — на осевой дуге стены
                () -> assertEquals(0.0, fromArc, FIT_EPS, "distance from the frame centre to the wall arc, cm"),
                () -> assertEquals(Math.toDegrees(position * extent), Math.toDegrees(fromStart), 0.2,
                        "degrees along the arc from the wall start to the piece"),
                () -> assertEquals(Math.toDegrees((1 - position) * extent), Math.toDegrees(toEnd), 0.2,
                        "degrees along the arc from the piece to the wall end"),
                () -> assertEquals(0.0, alongRadius, 1e-3, "the piece must be turned along the tangent"));
    }

    /** Угол между двумя векторами, радианы, от 0 до π. */
    private static double angleBetween(double ax, double ay, double bx, double by) {
        return Math.acos(Math.max(-1, Math.min(1, (ax * bx + ay * by) / (Math.hypot(ax, ay) * Math.hypot(bx, by)))));
    }

    @Test
    void elevationIsStoredForWindows() {
        String catalogId = str(catalogDoorsAndWindows().get(0), "catalogId");
        String wallId = str(mcp.call("create_wall",
                "xStart", 0, "yStart", 0, "xEnd", 500, "yEnd", 0, "thickness", 20), "id");

        Map<String, Object> placed = mcp.call("place_door_or_window",
                "catalogId", catalogId, "wallId", wallId, "elevation", 90);

        assertEquals(90.0, num(placed, "elevation"), RESPONSE_EPS, "elevation in the response");
        Home home = saveAndReadHome("window-elevation");
        HomePieceOfFurniture piece = pieceById(home, str(placed, "id"));
        assertTrue(piece instanceof HomeDoorOrWindow, "saved as " + piece.getClass().getSimpleName());
        assertEquals(90.0, piece.getElevation(), MODEL_EPS, "elevation in the saved home");
        // position по умолчанию 0.5 — середина стены
        List<String> problems = wallFitProblems((HomeDoorOrWindow) piece, wallById(home, wallId), 0.5);
        assertTrue(problems.isEmpty(), "with the default position the piece must sit at the wall centre: " + problems);
    }

    @Test
    void doorIsFoundByNameAmongDoorsAndWindowsOnly() {
        String name = str(catalogDoorsAndWindows().get(0), "name");
        String wallId = str(mcp.call("create_wall",
                "xStart", 0, "yStart", 0, "xEnd", 500, "yEnd", 0, "thickness", 20), "id");

        Map<String, Object> placed = mcp.call("place_door_or_window", "name", name, "wallId", wallId);

        Home home = saveAndReadHome("door-by-name");
        HomePieceOfFurniture piece = pieceById(home, str(placed, "id"));
        assertTrue(piece instanceof HomeDoorOrWindow,
                "a name search of place_door_or_window must yield a door or window, got "
                        + piece.getClass().getSimpleName() + " '" + piece.getName() + "'");
        assertTrue(piece.getName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)),
                "searched for '" + name + "', placed '" + piece.getName() + "'");
    }

    @Test
    void placeFurnitureAlsoCreatesRealDoorForCatalogDoor() {
        String catalogId = str(catalogDoorsAndWindows().get(0), "catalogId");

        Map<String, Object> placed = mcp.call("place_furniture", "catalogId", catalogId, "x", 300, "y", 200);

        Home home = saveAndReadHome("door-as-furniture");
        HomePieceOfFurniture piece = pieceById(home, str(placed, "id"));
        assertTrue(piece instanceof HomeDoorOrWindow,
                "place_furniture with a catalog door must create a HomeDoorOrWindow, got "
                        + piece.getClass().getSimpleName());
        assertEquals(300.0, piece.getX(), MODEL_EPS, "x");
        assertEquals(200.0, piece.getY(), MODEL_EPS, "y");
        assertEquals(catalogDoorOrWindow(catalogId).getSashes().length,
                ((HomeDoorOrWindow) piece).getSashes().length, "sashes kept from the catalog");
    }

    @Test
    void unknownWallIsRejectedAndNothingIsPlaced() {
        String catalogId = str(catalogDoorsAndWindows().get(0), "catalogId");

        ToolResult result = mcp.callTool("place_door_or_window",
                McpLiveClient.args("catalogId", catalogId, "wallId", "wall-no-such-id"));

        assertTrue(result.isError(), "placing into an unknown wall must fail: " + result);
        assertTrue(objects(state(), "furniture").isEmpty(), "nothing must be placed after the error");
    }

    // === Помощники ===

    /** Двери и окна каталога, как их отдаёт сама команда каталога. */
    private List<Map<String, Object>> catalogDoorsAndWindows() {
        List<Map<String, Object>> items =
                objects(mcp.call("list_furniture_catalog", "type", "doorOrWindow"), "furniture");
        assertFalse(items.isEmpty(), "the catalog must offer at least one door or window");
        for (Map<String, Object> item : items) {
            assertEquals(Boolean.TRUE, item.get("isDoorOrWindow"),
                    "type=doorOrWindow must list only doors and windows: " + item);
        }
        return items;
    }

    /**
     * Чем посадка изделия в прямую стену расходится с обещанной; пустой список — посадка верна.
     * <ul>
     *   <li>изделие повёрнуто вдоль стены;</li>
     *   <li>центр изделия лежит на доле {@code position} длины стены от её начала;</li>
     *   <li>у растягиваемого изделия рама ровно в толщину стены и совпадает с телом стены;</li>
     *   <li>у нерастягиваемого глубина не меняется, а рама лежит внутри тела стены (или стена внутри
     *       рамы, если рама толще).</li>
     * </ul>
     * Рама в модели Sweet Home 3D — полоса по глубине изделия: начинается в {@code wallDistance}
     * долях глубины от задней стороны и занимает {@code wallThickness} долей глубины.
     * «Растягиваемое» — как в самом Sweet Home 3D при перетаскивании в стену: изделие можно менять
     * в размерах и его ширина и глубина меняются независимо (не так, например, у модели открытой двери).
     */
    private static List<String> wallFitProblems(HomeDoorOrWindow piece, Wall wall, double position) {
        List<String> problems = new ArrayList<>();
        double length = Math.hypot(wall.getXEnd() - wall.getXStart(), wall.getYEnd() - wall.getYStart());
        double ux = (wall.getXEnd() - wall.getXStart()) / length;
        double uy = (wall.getYEnd() - wall.getYStart()) / length;
        double nx = -uy;
        double ny = ux;

        // Оси изделия на плане: ширина и глубина (от задней стороны к передней)
        double widthX = Math.cos(piece.getAngle());
        double widthY = Math.sin(piece.getAngle());
        double depthX = -widthY;
        double depthY = widthX;

        double cross = widthX * uy - widthY * ux;
        if (Math.abs(cross) > 1e-3) {
            problems.add(String.format(Locale.ROOT, "not aligned with the wall (angle %.2f deg)",
                    Math.toDegrees(piece.getAngle())));
            return problems;
        }

        double along = (piece.getX() - wall.getXStart()) * ux + (piece.getY() - wall.getYStart()) * uy;
        if (Math.abs(along - position * length) > FIT_EPS) {
            problems.add(String.format(Locale.ROOT, "centre is %.2f cm from the wall start, expected %.2f",
                    along, position * length));
        }

        double depth = piece.getDepth();
        double frame = piece.getWallThickness() * depth;
        double frameBack = -depth / 2 + piece.getWallDistance() * depth;
        double backOffset = offsetFromWallAxis(piece, wall, depthX, depthY, frameBack, nx, ny);
        double frontOffset = offsetFromWallAxis(piece, wall, depthX, depthY, frameBack + frame, nx, ny);
        double low = Math.min(backOffset, frontOffset);
        double high = Math.max(backOffset, frontOffset);
        double half = wall.getThickness() / 2.0;
        String spans = String.format(Locale.ROOT,
                "frame spans %.2f..%.2f cm across the wall axis, the wall spans %.2f..%.2f", low, high, -half, half);

        boolean stretchable = piece.isResizable() && piece.isWidthDepthDeformable()
                && piece.getModelTransformations() == null;
        if (stretchable) {
            if (Math.abs(frame - wall.getThickness()) > FIT_EPS) {
                problems.add(String.format(Locale.ROOT, "frame is %.2f cm thick, the wall is %.2f",
                        frame, wall.getThickness()));
            }
            if (Math.abs(low + half) > FIT_EPS || Math.abs(high - half) > FIT_EPS) {
                problems.add(spans);
            }
        } else {
            boolean frameInsideWall = low >= -half - FIT_EPS && high <= half + FIT_EPS;
            boolean wallInsideFrame = low <= -half + FIT_EPS && high >= half - FIT_EPS;
            if (!frameInsideWall && !wallInsideFrame) {
                problems.add(spans + " (not stretchable)");
            }
        }
        return problems;
    }

    /** Расстояние со знаком от оси стены до точки изделия, лежащей на его оси глубины в {@code t} см от центра. */
    private static double offsetFromWallAxis(HomeDoorOrWindow piece, Wall wall, double depthX, double depthY,
                                             double t, double nx, double ny) {
        double px = piece.getX() + depthX * t;
        double py = piece.getY() + depthY * t;
        return (px - wall.getXStart()) * nx + (py - wall.getYStart()) * ny;
    }

    private static CatalogDoorOrWindow catalogDoorOrWindow(String catalogId) {
        for (FurnitureCategory category : new DefaultFurnitureCatalog().getCategories()) {
            for (CatalogPieceOfFurniture piece : category.getFurniture()) {
                if (catalogId.equals(piece.getId())) {
                    assertTrue(piece instanceof CatalogDoorOrWindow,
                            catalogId + " is not a door or window in the Sweet Home 3D catalog");
                    return (CatalogDoorOrWindow) piece;
                }
            }
        }
        throw new AssertionError("The default Sweet Home 3D catalog has no item " + catalogId);
    }
}
