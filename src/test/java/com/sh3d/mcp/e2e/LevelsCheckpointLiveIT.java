package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Уровни и контрольная точка: объекты создаются на выбранном уровне, а {@code restore_checkpoint}
 * возвращает сцену такой, какой она была в момент {@code checkpoint}, — объекты на своих уровнях,
 * выбран прежний уровень.
 */
class LevelsCheckpointLiveIT extends LiveTestBase {

    private static final String GROUND = "E2E ground";
    private static final String FIRST = "E2E first";

    /** Координата Y стены и X мебели — по ним объекты узнаются после восстановления. */
    private static final double GROUND_WALL_Y = 0;
    private static final double FIRST_WALL_Y = 100;
    private static final double GROUND_PIECE_X = 100;
    private static final double FIRST_PIECE_X = 300;

    private String groundId;
    private String firstId;

    /**
     * Два уровня, на каждом стена и предмет мебели; в конце выбран нижний уровень.
     * По описанию {@code add_level} новый уровень становится выбранным, и объекты создаются на нём.
     */
    private void buildTwoLevelScene() {
        String furnitureId = str(objects(mcp.call("list_furniture_catalog", "type", "furniture"), "furniture")
                .get(0), "catalogId");

        groundId = str(mcp.call("add_level", "name", GROUND, "elevation", 0), "id");
        mcp.call("create_wall", "xStart", 0, "yStart", GROUND_WALL_Y, "xEnd", 400, "yEnd", GROUND_WALL_Y);
        mcp.call("place_furniture", "catalogId", furnitureId, "x", GROUND_PIECE_X, "y", 250);

        firstId = str(mcp.call("add_level", "name", FIRST, "elevation", 262, "height", 240), "id");
        mcp.call("create_wall", "xStart", 0, "yStart", FIRST_WALL_Y, "xEnd", 400, "yEnd", FIRST_WALL_Y);
        mcp.call("place_furniture", "catalogId", furnitureId, "x", FIRST_PIECE_X, "y", 250);

        mcp.call("set_selected_level", "id", groundId);
    }

    @Test
    void objectsAreCreatedOnTheSelectedLevel() {
        buildTwoLevelScene();
        // Ещё одна стена после возврата на нижний уровень
        mcp.call("create_wall", "xStart", 0, "yStart", 200, "xEnd", 400, "yEnd", 200);

        List<Map<String, Object>> listed = objects(mcp.call("list_levels"), "levels");
        assertEquals(2, listed.size(), "list_levels: " + listed);
        assertEquals(Boolean.TRUE, levelByName(listed, GROUND).get("selected"), "list_levels: ground is selected");
        assertEquals(Boolean.FALSE, levelByName(listed, FIRST).get("selected"), "list_levels: first is not selected");

        Home home = saveAndReadHome("two-levels");
        assertEquals(2, home.getLevels().size(), "levels in the saved home");
        Level ground = modelLevel(home, GROUND);
        Level first = modelLevel(home, FIRST);
        assertEquals(groundId, ground.getId(), "add_level returned the id of the ground level");
        assertEquals(firstId, first.getId(), "add_level returned the id of the first level");
        assertEquals(0.0, ground.getElevation(), MODEL_EPS, "ground elevation");
        assertEquals(262.0, first.getElevation(), MODEL_EPS, "first elevation");
        assertEquals(240.0, first.getHeight(), MODEL_EPS, "first height");
        // Значения по умолчанию из схемы add_level
        assertEquals(250.0, ground.getHeight(), MODEL_EPS, "ground height defaults to 250");
        assertEquals(12.0, first.getFloorThickness(), MODEL_EPS, "floorThickness defaults to 12");

        assertAll(sceneOnItsLevels(home, 3));
        assertEquals(GROUND, levelName(wallAtY(home, 200).getLevel()),
                "a wall created after set_selected_level belongs to the newly selected level");
        assertEquals(GROUND, levelName(home.getSelectedLevel()), "selected level in the saved home");
    }

    @Test
    void restoreCheckpointKeepsObjectsOnTheirLevelsAndTheSelectedLevel() {
        buildTwoLevelScene();
        String description = uniqueDescription("two levels");
        mcp.call("checkpoint", "description", description);

        // Правка после контрольной точки: другой уровень выбран, на нём новая стена и мебель
        mcp.call("set_selected_level", "id", firstId);
        mcp.call("create_wall", "xStart", 0, "yStart", 300, "xEnd", 400, "yEnd", 300);
        String furnitureId = str(objects(mcp.call("list_furniture_catalog", "type", "furniture"), "furniture")
                .get(0), "catalogId");
        mcp.call("place_furniture", "catalogId", furnitureId, "x", 500, "y", 250);
        assertEquals(3.0, num(state(), "wallCount"), 0.0, "the edit after the checkpoint took effect");

        // force: по описанию — заново применить точку, даже если она текущая, отбросив правки после неё
        mcp.call("restore_checkpoint", "id", checkpointId(description), "force", true);
        assertEquals(description, currentCheckpoint().get("description"), "the restored checkpoint is the current one");

        Map<String, Object> state = state();
        Home home = saveAndReadHome("restored");
        List<Executable> checks = new ArrayList<>();

        // Ответ get_state
        checks.add(() -> assertEquals(2.0, num(state, "wallCount"), 0.0, "get_state wallCount after restore"));
        checks.add(() -> assertEquals(2.0, num(state, "furnitureCount"), 0.0,
                "get_state furnitureCount after restore"));
        checks.add(() -> assertEquals(2.0, num(state, "levelCount"), 0.0, "get_state levelCount after restore"));
        checks.add(() -> assertEquals(GROUND, stateObjectAt(state, "walls", "yStart", GROUND_WALL_Y).get("level"),
                "get_state: level of the ground wall"));
        checks.add(() -> assertEquals(FIRST, stateObjectAt(state, "walls", "yStart", FIRST_WALL_Y).get("level"),
                "get_state: level of the first-floor wall"));
        checks.add(() -> assertEquals(GROUND, stateObjectAt(state, "furniture", "x", GROUND_PIECE_X).get("level"),
                "get_state: level of the ground furniture"));
        checks.add(() -> assertEquals(FIRST, stateObjectAt(state, "furniture", "x", FIRST_PIECE_X).get("level"),
                "get_state: level of the first-floor furniture"));
        checks.add(() -> assertEquals(Boolean.TRUE, levelByName(objects(state, "levels"), GROUND).get("selected"),
                "get_state: the level selected at the checkpoint is selected again"));
        checks.add(() -> assertEquals(Boolean.FALSE, levelByName(objects(state, "levels"), FIRST).get("selected"),
                "get_state: the level selected by the discarded edit is not selected"));

        // Модель
        checks.add(() -> assertEquals(2, home.getLevels().size(), "levels in the saved home"));
        checks.addAll(sceneOnItsLevels(home, 2));
        checks.add(() -> assertEquals(GROUND, levelName(home.getSelectedLevel()), "selected level in the saved home"));

        assertAll("scene after restore_checkpoint", checks);
    }

    /**
     * Сцена без уровней: контрольная точка возвращает ровно то, что было, а вызов без {@code id}
     * отступает на одну точку назад.
     */
    @Test
    void restoreCheckpointBringsBackFlatScene() {
        String oneWall = uniqueDescription("one wall");
        String twoWalls = uniqueDescription("two walls");
        mcp.call("create_wall", "xStart", 0, "yStart", 0, "xEnd", 400, "yEnd", 0, "thickness", 12);
        mcp.call("checkpoint", "description", oneWall);
        mcp.call("create_wall", "xStart", 0, "yStart", 100, "xEnd", 400, "yEnd", 100, "thickness", 12);
        mcp.call("checkpoint", "description", twoWalls);
        assertEquals(twoWalls, currentCheckpoint().get("description"), "a new checkpoint becomes the current one");

        // Точка уже текущая: без force — ошибка, как сказано в описании
        ToolResult alreadyCurrent = mcp.callTool("restore_checkpoint",
                McpLiveClient.args("id", checkpointId(twoWalls)));
        assertTrue(alreadyCurrent.isError(), "restoring the current checkpoint without force must fail: "
                + alreadyCurrent);

        // Правки после второй точки отбрасываются принудительным восстановлением
        String wallToDelete = str(objects(state(), "walls").get(0), "id");
        mcp.call("delete_wall", "id", wallToDelete);
        mcp.call("create_wall", "xStart", 0, "yStart", 300, "xEnd", 400, "yEnd", 300);
        mcp.call("restore_checkpoint", "id", checkpointId(twoWalls), "force", true);
        Home restoredTwoWalls = saveAndReadHome("restored-two-walls");
        assertEquals(2, restoredTwoWalls.getWalls().size(), "walls after restoring the second checkpoint");
        assertEquals(12.0, wallAtY(restoredTwoWalls, 0).getThickness(), MODEL_EPS, "the deleted wall is back");
        assertEquals(12.0, wallAtY(restoredTwoWalls, 100).getThickness(), MODEL_EPS, "the second wall is kept");

        // Без id — на одну точку назад, к сцене с одной стеной
        mcp.call("restore_checkpoint");
        assertEquals(oneWall, currentCheckpoint().get("description"), "one step back from the second checkpoint");
        Home restoredOneWall = saveAndReadHome("restored-one-wall");
        assertEquals(1, restoredOneWall.getWalls().size(), "walls after stepping back");
        assertEquals(0.0, restoredOneWall.getWalls().iterator().next().getYStart(), MODEL_EPS, "the first wall");
    }

    /** «Maximum 32 checkpoints are kept»: лента не растёт дальше и остаётся рабочей после вытеснения. */
    @Test
    void timelineKeepsAtMost32CheckpointsAndStaysUsable() {
        mcp.call("create_wall", "xStart", 0, "yStart", 0, "xEnd", 400, "yEnd", 0);
        String prefix = uniqueDescription("fill");
        for (int i = 1; i <= 34; i++) {
            mcp.call("checkpoint", "description", prefix + " #" + i);
        }

        List<Map<String, Object>> checkpoints = objects(mcp.call("list_checkpoints"), "checkpoints");
        assertEquals(32, checkpoints.size(), "checkpoints kept");
        // Вытесняются старые: последние 32 из 34 на месте и идут по порядку
        for (int i = 0; i < 32; i++) {
            assertEquals(prefix + " #" + (i + 3), checkpoints.get(i).get("description"), "checkpoint " + i);
        }

        mcp.call("create_wall", "xStart", 0, "yStart", 100, "xEnd", 400, "yEnd", 100);
        mcp.call("restore_checkpoint", "id", checkpointId(prefix + " #34"), "force", true);
        assertEquals(1.0, num(state(), "wallCount"), 0.0, "walls after restoring the newest checkpoint");
    }

    /**
     * {@code set_selected_level} и {@code delete_level}: «Use list_levels or get_state to find level IDs».
     * Идентификатор уровня из {@code list_levels} обязан приниматься обеими командами.
     */
    @Test
    void levelIdFromListLevelsIsAcceptedByLevelCommands() {
        mcp.call("add_level", "name", GROUND, "elevation", 0);
        mcp.call("add_level", "name", FIRST, "elevation", 262);

        List<Map<String, Object>> listed = objects(mcp.call("list_levels"), "levels");
        List<Map<String, Object>> inState = objects(state(), "levels");
        Object groundIdFromList = levelByName(listed, GROUND).get("id");
        Object firstIdFromList = levelByName(listed, FIRST).get("id");
        assertNotNull(groundIdFromList, "list_levels must report an id for every level");

        ToolResult select = mcp.callTool("set_selected_level", McpLiveClient.args("id", groundIdFromList));
        ToolResult delete = mcp.callTool("delete_level", McpLiveClient.args("id", firstIdFromList));

        Home saved = saveAndReadHome("level-ids-from-list");
        List<Map<String, Object>> left = objects(mcp.call("list_levels"), "levels");
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertFalse(select.isError(), "set_selected_level rejected id " + Json.write(groundIdFromList)
                + " taken from list_levels: " + select));
        checks.add(() -> assertFalse(delete.isError(), "delete_level rejected id " + Json.write(firstIdFromList)
                + " taken from list_levels: " + delete));
        // Issue #9: id — стабильный id модели, index — место в списке.
        for (int i = 0; i < listed.size(); i++) {
            Map<String, Object> level = listed.get(i);
            int index = i;
            checks.add(() -> assertEquals(index, num(level, "index"), 0.0,
                    "list_levels must expose a numeric index separately from id"));
            checks.add(() -> assertEquals(levelByName(inState, str(level, "name")).get("id"), level.get("id"),
                    "list_levels id must equal get_state id for " + level.get("name")));
        }
        checks.add(() -> assertEquals(1, left.size(), "levels left after delete_level: " + left));
        checks.add(() -> assertEquals(Boolean.TRUE, levelByName(left, GROUND).get("selected"),
                "the ground level is selected"));
        checks.add(() -> assertEquals(1, saved.getLevels().size(), "saved model after delete_level"));
        checks.add(() -> assertEquals(GROUND, levelName(saved.getSelectedLevel()), "saved selected level"));
        checks.add(() -> assertTrue(saved.getLevels().stream().noneMatch(l -> FIRST.equals(l.getName())),
                "deleted first level must be absent from saved model"));
        assertAll("level id taken from list_levels", checks);
    }

    // === Помощники ===

    /** Описание контрольной точки, неповторимое в пределах жизни приложения: лента точек общая для всех тестов. */
    private static String uniqueDescription(String what) {
        return "e2e " + what + " " + UUID.randomUUID();
    }

    /**
     * Идентификатор точки по её описанию — из {@code list_checkpoints}, как велит описание
     * {@code restore_checkpoint}. Стабильность однажды выданного id проверяет CheckpointIdentityLiveIT.
     */
    private Object checkpointId(String description) {
        List<Map<String, Object>> checkpoints = objects(mcp.call("list_checkpoints"), "checkpoints");
        for (Map<String, Object> checkpoint : checkpoints) {
            if (description.equals(checkpoint.get("description"))) {
                assertNotNull(checkpoint.get("id"), "list_checkpoints must report an id: " + checkpoint);
                return checkpoint.get("id");
            }
        }
        throw new AssertionError("No checkpoint '" + description + "' in " + checkpoints);
    }

    /** Точка, помеченная {@code current} в {@code list_checkpoints}. */
    private Map<String, Object> currentCheckpoint() {
        List<Map<String, Object>> checkpoints = objects(mcp.call("list_checkpoints"), "checkpoints");
        Map<String, Object> current = null;
        for (Map<String, Object> checkpoint : checkpoints) {
            if (Boolean.TRUE.equals(checkpoint.get("current"))) {
                assertTrue(current == null, "more than one checkpoint is marked current: " + checkpoints);
                current = checkpoint;
            }
        }
        assertNotNull(current, "no checkpoint is marked current: " + checkpoints);
        return current;
    }

    /** Проверки модели: стены и мебель исходной сцены стоят на своих уровнях; всего стен {@code wallCount}. */
    private static List<Executable> sceneOnItsLevels(Home home, int wallCount) {
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertEquals(wallCount, home.getWalls().size(), "walls in the saved home"));
        checks.add(() -> assertEquals(2, home.getFurniture().size(), "furniture in the saved home"));
        checks.add(() -> assertEquals(GROUND, levelName(wallAtY(home, GROUND_WALL_Y).getLevel()),
                "saved home: level of the ground wall"));
        checks.add(() -> assertEquals(FIRST, levelName(wallAtY(home, FIRST_WALL_Y).getLevel()),
                "saved home: level of the first-floor wall"));
        checks.add(() -> assertEquals(GROUND, levelName(pieceAtX(home, GROUND_PIECE_X).getLevel()),
                "saved home: level of the ground furniture"));
        checks.add(() -> assertEquals(FIRST, levelName(pieceAtX(home, FIRST_PIECE_X).getLevel()),
                "saved home: level of the first-floor furniture"));
        return checks;
    }

    private static Map<String, Object> levelByName(List<Map<String, Object>> levels, String name) {
        for (Map<String, Object> level : levels) {
            if (name.equals(level.get("name"))) {
                return level;
            }
        }
        throw new AssertionError("No level named '" + name + "' in " + levels);
    }

    private static Level modelLevel(Home home, String name) {
        for (Level level : home.getLevels()) {
            if (name.equals(level.getName())) {
                return level;
            }
        }
        throw new AssertionError("The saved home has no level named '" + name + "'");
    }

    private static Wall wallAtY(Home home, double y) {
        for (Wall wall : home.getWalls()) {
            if (Math.abs(wall.getYStart() - y) <= MODEL_EPS) {
                return wall;
            }
        }
        throw new AssertionError("The saved home has no wall starting at y=" + y);
    }

    private static HomePieceOfFurniture pieceAtX(Home home, double x) {
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            if (Math.abs(piece.getX() - x) <= MODEL_EPS) {
                return piece;
            }
        }
        throw new AssertionError("The saved home has no furniture at x=" + x);
    }

    /** Объект из массива {@code kind} ответа get_state, у которого поле {@code field} равно {@code value}. */
    private static Map<String, Object> stateObjectAt(Map<String, Object> state, String kind, String field,
                                                     double value) {
        for (Map<String, Object> object : objects(state, kind)) {
            if (Math.abs(num(object, field) - value) <= RESPONSE_EPS) {
                return object;
            }
        }
        throw new AssertionError("get_state has no object in '" + kind + "' with " + field + "=" + value);
    }
}
