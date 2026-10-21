package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Круг {@code save_home} → очистка → {@code load_home}: загрузка — обратная операция сохранения,
 * сцена после неё та же, включая уровни объектов.
 */
class SaveLoadRoundTripLiveIT extends LiveTestBase {

    private static final String GROUND = "E2E ground";
    private static final String FIRST = "E2E first";

    /** Разделы get_state с объектами сцены: по описанию load_home они заменяются содержимым файла. */
    private static final String[] OBJECT_SECTIONS = {
        "levels", "walls", "furniture", "rooms", "labels", "dimensionLines"
    };

    @Test
    void loadHomeBringsBackSceneWithoutLevels() {
        buildFloor();
        Map<String, Object> before = state();
        Home saved = saveAndReadHome("flat");
        assertEquals(4, saved.getWalls().size(), "walls in the saved home");
        assertEquals(2, saved.getFurniture().size(), "furniture in the saved home");
        assertTrue(saved.getLevels().isEmpty(), "the flat scene has no levels");

        assertLoadBringsBack("flat", before, saved);
    }

    @Test
    void loadHomeBringsBackObjectsOnTheirLevels() {
        String ground = str(mcp.call("add_level", "name", GROUND, "elevation", 0), "id");
        buildFloor();
        mcp.call("add_level", "name", FIRST, "elevation", 262, "height", 240);
        mcp.call("create_wall", "xStart", 0, "yStart", 0, "xEnd", 600, "yEnd", 0, "thickness", 15);
        mcp.call("place_furniture", "catalogId", furnitureCatalogId(), "x", 450, "y", 200);
        mcp.call("set_selected_level", "id", ground);

        Map<String, Object> before = state();
        Home saved = saveAndReadHome("two-levels");

        // Сохранённая сцена — та, что строилась: объекты действительно разнесены по уровням
        Map<String, String> objects = describe(saved);
        assertAll("the saved two-level scene",
                () -> assertEquals(2, saved.getLevels().size(), "levels"),
                () -> assertEquals(4, countWithLevel(objects, "wall", GROUND), "ground walls: " + objects),
                () -> assertEquals(1, countWithLevel(objects, "wall", FIRST), "first-floor walls: " + objects),
                () -> assertEquals(2, countWithLevel(objects, "piece", GROUND), "ground furniture: " + objects),
                () -> assertEquals(1, countWithLevel(objects, "piece", FIRST), "first-floor furniture: " + objects),
                () -> assertEquals(1, countWithLevel(objects, "room", GROUND), "ground rooms: " + objects),
                () -> assertEquals(1, countWithLevel(objects, "label", GROUND), "ground labels: " + objects),
                () -> assertEquals(1, countWithLevel(objects, "dimension", GROUND), "ground dimensions: " + objects),
                () -> assertEquals(GROUND, levelName(saved.getSelectedLevel()), "selected level"));

        assertLoadBringsBack("two-levels", before, saved);
    }

    /**
     * «Camera positions, environment settings, and stored cameras are also restored». Сцена без
     * уровней и камера наблюдателя с явными координатами: так Sweet Home 3D сам её не двигает.
     */
    @Test
    void loadHomeRestoresCameraEnvironmentAndStoredCameras() {
        Map<String, Object> pristine = state();
        mcp.call("create_walls", "x", 0, "y", 0, "width", 600, "height", 400, "thickness", 20);
        mcp.call("set_camera", "mode", "observer", "x", 120, "y", 700, "z", 170, "yaw", 200, "pitch", 15, "fov", 60);
        mcp.call("store_camera", "name", "E2E view");
        mcp.call("set_environment", "groundColor", "#336699", "wallsAlpha", 0.25);
        Map<String, Object> before = state();
        try {
            assertEquals("observer", ((Map<?, ?>) before.get("camera")).get("mode"), "camera mode before save");
            assertEquals(1, objects(before, "storedCameras").size(), "stored cameras before save");
            Path file = workDir.resolve("view.sh3d");
            mcp.call("save_home", "filePath", file.toString());

            // Всё перечисленное меняется, закладка удаляется
            resetScene();
            mcp.call("set_camera", "mode", "top");
            mcp.call("set_environment", "groundColor", "#102030", "wallsAlpha", 0.75);

            mcp.call("load_home", "filePath", file.toString());

            Map<String, Object> after = state();
            assertAll("get_state after load_home equals get_state before save_home",
                    sectionChecks(before, after, "camera", "storedCameras", "environment"));
        } finally {
            // Окружение и камера не входят в очистку сцены — вернуть их, чтобы не протекали в другие тесты
            Map<?, ?> environment = (Map<?, ?>) pristine.get("environment");
            mcp.callTool("set_environment", McpLiveClient.args(
                    "groundColor", environment.get("groundColor"), "wallsAlpha", environment.get("wallsAlpha")));
            mcp.callTool("set_camera", McpLiveClient.args("mode", ((Map<?, ?>) pristine.get("camera")).get("mode")));
        }
    }

    @Test
    void savePathGetsExtensionAndLoadOfMissingFileChangesNothing() {
        mcp.call("create_wall", "xStart", 0, "yStart", 0, "xEnd", 300, "yEnd", 0);

        // «The .sh3d extension is added automatically if missing»
        Path withoutExtension = workDir.resolve("no-extension");
        Map<String, Object> saved = mcp.call("save_home", "filePath", withoutExtension.toString());
        Path expected = workDir.resolve("no-extension.sh3d");
        assertEquals(expected.toString(), saved.get("filePath"), "save_home must add the .sh3d extension");
        assertTrue(Files.isRegularFile(expected), "saved file must exist: " + expected);
        assertFalse(Files.exists(withoutExtension), "no file without the extension must be written");
        assertEquals(1, readHome(expected).getWalls().size(), "walls in the saved home");

        // «The file must exist and be readable»
        ToolResult missing = mcp.callTool("load_home",
                McpLiveClient.args("filePath", workDir.resolve("missing.sh3d").toString()));
        assertTrue(missing.isError(), "loading a missing file must fail: " + missing);
        assertEquals(1.0, num(state(), "wallCount"), 0.0, "a failed load must leave the scene as it was");
    }

    /**
     * Один этаж со всеми видами объектов на текущем уровне: комната из четырёх стен с дверью, мебель,
     * помещение, подпись, размерная линия.
     */
    private void buildFloor() {
        String doorId = str(objects(mcp.call("list_furniture_catalog", "type", "doorOrWindow"), "furniture")
                .get(0), "catalogId");
        Map<String, Object> walls = mcp.call("create_walls",
                "x", 0, "y", 0, "width", 600, "height", 400, "thickness", 20);
        String topWall = (String) ((List<?>) walls.get("wallIds")).get(0);
        mcp.call("place_door_or_window", "catalogId", doorId, "wallId", topWall, "position", 0.3);
        mcp.call("place_furniture", "catalogId", furnitureCatalogId(), "x", 150, "y", 200, "angle", 90);
        List<Map<String, Object>> corners = new ArrayList<>();
        corners.add(McpLiveClient.args("x", 10, "y", 10));
        corners.add(McpLiveClient.args("x", 590, "y", 10));
        corners.add(McpLiveClient.args("x", 590, "y", 390));
        corners.add(McpLiveClient.args("x", 10, "y", 390));
        mcp.call("create_room_polygon", "points", corners, "name", "E2E room");
        mcp.call("add_label", "text", "E2E label", "x", 300, "y", 200);
        mcp.call("add_dimension_line", "xStart", 0, "yStart", 0, "xEnd", 600, "yEnd", 0, "offset", -40);
    }

    private String furnitureCatalogId() {
        return str(objects(mcp.call("list_furniture_catalog", "type", "furniture"), "furniture").get(0), "catalogId");
    }

    /**
     * Вторая половина круга: сцену заменяет посторонняя, затем файл загружается. После загрузки и
     * get_state, и повторно сохранённая модель обязаны совпасть с тем, что было до сохранения.
     *
     * @param name   имя файла (без расширения), в который сцена уже сохранена
     * @param before ответ get_state перед сохранением
     * @param saved  сцена, прочитанная из сохранённого файла
     */
    private void assertLoadBringsBack(String name, Map<String, Object> before, Home saved) {
        // Сцена, непохожая на сохранённую: без уровней, с чужой стеной
        resetScene();
        mcp.call("create_wall", "xStart", -500, "yStart", -500, "xEnd", -100, "yEnd", -500);

        Map<String, Object> loaded = mcp.call("load_home", "filePath", workDir.resolve(name + ".sh3d").toString());
        Map<String, Object> after = state();
        Home reloaded = saveAndReadHome(name + "-again");

        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertEquals(saved.getLevels().size(), num(loaded, "levels"), 0.0, "load_home: levels"));
        checks.add(() -> assertEquals(saved.getWalls().size(), num(loaded, "walls"), 0.0, "load_home: walls"));
        checks.add(() -> assertEquals(saved.getFurniture().size(), num(loaded, "furniture"), 0.0,
                "load_home: furniture"));
        checks.add(() -> assertEquals(saved.getRooms().size(), num(loaded, "rooms"), 0.0, "load_home: rooms"));
        checks.add(() -> assertEquals(saved.getLabels().size(), num(loaded, "labels"), 0.0, "load_home: labels"));
        checks.add(() -> assertEquals(saved.getDimensionLines().size(), num(loaded, "dimensionLines"), 0.0,
                "load_home: dimensionLines"));
        checks.addAll(sectionChecks(before, after, OBJECT_SECTIONS));
        checks.add(() -> assertNoDifferences("the scene saved again after load_home differs from the first save",
                differences("home", describe(saved), describe(reloaded))));
        assertAll("scene after load_home", checks);
    }

    /** Сравнение разделов двух ответов get_state; каждый раздел — отдельная проверка. */
    private static List<Executable> sectionChecks(Map<String, Object> before, Map<String, Object> after,
                                                  String... sections) {
        List<Executable> checks = new ArrayList<>();
        for (String section : sections) {
            checks.add(() -> assertNoDifferences("get_state '" + section + "' changed after load_home",
                    differences(section, before.get(section), after.get(section))));
        }
        return checks;
    }

    /**
     * Сцена как отсортированная таблица «объект → свойства и уровень». Ключ — вид объекта и его id:
     * идентификаторы хранятся в файле и обязаны пережить загрузку.
     */
    private static Map<String, String> describe(Home home) {
        Map<String, String> objects = new TreeMap<>();
        for (Level level : home.getLevels()) {
            objects.put("level " + level.getId(), String.format(Locale.ROOT, "%s elevation %.2f height %.2f",
                    level.getName(), level.getElevation(), level.getHeight()));
        }
        for (Wall wall : home.getWalls()) {
            objects.put("wall " + wall.getId(), String.format(Locale.ROOT,
                    "(%.2f,%.2f)->(%.2f,%.2f) thickness %.2f connected %s/%s level=%s",
                    wall.getXStart(), wall.getYStart(), wall.getXEnd(), wall.getYEnd(), wall.getThickness(),
                    wall.getWallAtStart() != null, wall.getWallAtEnd() != null, levelName(wall.getLevel())));
        }
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            objects.put("piece " + piece.getId(), String.format(Locale.ROOT,
                    "%s %s at (%.2f,%.2f) angle %.4f size %.2fx%.2fx%.2f boundToWall %s level=%s",
                    piece.getClass().getSimpleName(), piece.getCatalogId(), piece.getX(), piece.getY(),
                    piece.getAngle(), piece.getWidth(), piece.getDepth(), piece.getHeight(),
                    piece instanceof HomeDoorOrWindow && ((HomeDoorOrWindow) piece).isBoundToWall(),
                    levelName(piece.getLevel())));
        }
        for (Room room : home.getRooms()) {
            objects.put("room " + room.getId(), String.format(Locale.ROOT, "%s points %d area %.2f level=%s",
                    room.getName(), room.getPointCount(), room.getArea(), levelName(room.getLevel())));
        }
        for (Label label : home.getLabels()) {
            objects.put("label " + label.getId(), String.format(Locale.ROOT, "%s at (%.2f,%.2f) level=%s",
                    label.getText(), label.getX(), label.getY(), levelName(label.getLevel())));
        }
        for (DimensionLine line : home.getDimensionLines()) {
            objects.put("dimension " + line.getId(), String.format(Locale.ROOT,
                    "(%.2f,%.2f)->(%.2f,%.2f) offset %.2f level=%s", line.getXStart(), line.getYStart(),
                    line.getXEnd(), line.getYEnd(), line.getOffset(), levelName(line.getLevel())));
        }
        return objects;
    }

    private static int countWithLevel(Map<String, String> objects, String kind, String level) {
        int count = 0;
        for (Map.Entry<String, String> entry : objects.entrySet()) {
            if (entry.getKey().startsWith(kind + " ") && entry.getValue().endsWith("level=" + level)) {
                count++;
            }
        }
        return count;
    }
}
