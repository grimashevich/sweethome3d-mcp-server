package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.LevelSceneFixture.*;
import static com.sh3d.mcp.command.handler.SceneFacts.assertNoProblems;
import static com.sh3d.mcp.command.handler.TestFixtures.addWall;
import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * restore_checkpoint и уровни: после восстановления сцена обязана совпасть с той, что была
 * в момент контрольной точки. Требования, на которые ссылаются комментарии к тестам:
 * <ul>
 *   <li>R1 — каждый объект на том же уровне, что в момент точки;</li>
 *   <li>R2 — выбран тот же уровень;</li>
 *   <li>R3 — уровни те же: число, порядок, имя, отметка, высота, толщина перекрытия, viewable;</li>
 *   <li>R4 — R1–R3 не зависят от того, какой уровень выбран при восстановлении и где лежат объекты;</li>
 *   <li>R5 — дом без уровней остаётся без уровней, объекты — с {@code level == null};</li>
 *   <li>R6 — идентификаторы объектов и уровней сохраняются;</li>
 *   <li>R7 — правка живой сцены не меняет контрольную точку;</li>
 *   <li>R8 — соединения стен и состав групп мебели сохраняются;</li>
 *   <li>R9 — счётчики в ответе равны содержимому сцены;</li>
 *   <li>G1 — модель меняется только в потоке EDT.</li>
 * </ul>
 *
 * <p>Здесь команда работает с голой моделью. Те же тесты в доме, который ведёт себя как
 * в приложении, — {@link RestoreCheckpointLevelsInApplicationTest}.
 */
class RestoreCheckpointLevelsTest {

    private CheckpointManager checkpointManager;
    private CheckpointHandler checkpointHandler;
    private RestoreCheckpointHandler restoreHandler;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        checkpointManager = new CheckpointManager();
        checkpointHandler = new CheckpointHandler(checkpointManager);
        restoreHandler = new RestoreCheckpointHandler(checkpointManager);
        home = newHome();
        accessor = createAccessor(home);
    }

    /** Дом, с которым работает команда. */
    Home newHome() {
        return new Home();
    }

    // ==================== Двухэтажный дом, всё на нижнем уровне ====================

    // R1, R2: стены, надписи и шесть размерных линий на Ground, выбран Ground. После восстановления
    // объекты остаются на Ground, а не съезжают на First, и выбранным остаётся Ground.
    @Test
    void testTwoStoreyHouseIsNotCollapsedOntoFirstWhenCheckpointIsReapplied() {
        SceneFacts expected = everythingOnGroundAtCheckpoint();

        ok(reapply(0));

        assertNoProblems("R1, R2: a two-storey house with everything on Ground must come back unchanged",
                expected.sceneProblems(SceneFacts.of(home)));
    }

    // R1, R2: то же на пути «отмена на шаг назад».
    @Test
    void testTwoStoreyHouseIsNotCollapsedOntoFirstOnUndo() {
        SceneFacts expected = everythingOnGroundAtCheckpoint();
        addWall(home, 0, 600, 400, 600, 35, 250);
        checkpoint("one more wall");

        ok(undo());

        assertNoProblems("R1, R2: a two-storey house with everything on Ground must come back unchanged",
                expected.sceneProblems(SceneFacts.of(home)));
    }

    /** Объекты добавлены так, как это делает приложение, — при выбранном Ground, без явного setLevel. */
    private SceneFacts everythingOnGroundAtCheckpoint() {
        List<Level> levels = addLevels(home, 2);
        home.setSelectedLevel(levels.get(0));
        addWall(home, 0, 0, 400, 0, 31, 250);
        addWall(home, 400, 0, 400, 300, 32, 250);
        addWall(home, 400, 300, 0, 300, 33, 250);
        addWall(home, 0, 300, 0, 0, 34, 250);
        home.addLabel(new Label("Kitchen", 100, 100));
        home.addLabel(new Label("Hall", 300, 200));
        for (int i = 0; i < 6; i++) {
            home.addDimensionLine(new DimensionLine(0, -20 * (i + 1), 400, -20 * (i + 1), 41 + i));
        }
        SceneFacts facts = SceneFacts.of(home);
        checkpoint("two-storey house");
        return facts;
    }

    // ==================== R1: объекты на своих уровнях ====================

    // R1: стены, комнаты, мебель (дверь, группа), надписи, размерные линии и полилинии лежат на обоих
    // уровнях; после восстановления каждый объект на прежнем уровне, число объектов по уровням то же.
    @Test
    void testObjectsStayOnTheirLevels() {
        SceneFacts expected = twoLevelHouseAtCheckpoint();

        ok(reapply(0));

        assertNoProblems("R1: every object must be on the level it had when the checkpoint was taken",
                expected.objectLevelProblems(SceneFacts.of(home)));
    }

    // R1: «объект на уровне» значит на уровне самого дома. Уровень с тем же именем и отметкой, но не из
    // home.getLevels(), — посторонний экземпляр: приложение такой объект ни на одном уровне не покажет.
    @Test
    void testObjectsReferToLevelInstancesOfTheRestoredHome() {
        List<Level> levels = buildFull(home, 3);
        home.setSelectedLevel(levels.get(1));
        checkpoint("three levels");
        home.setSelectedLevel(levels.get(2));

        ok(reapply(0));

        SceneFacts actual = SceneFacts.of(home);
        List<String> problems = SceneFacts.foreignLevelProblems(home);
        actual.objects.forEach((key, fact) -> {
            if (SceneFacts.NO_LEVEL.equals(fact.level)) {
                problems.add(key + " has no level at all");
            }
        });
        assertFalse(actual.objects.isEmpty(), "the restored scene has no objects");
        assertNoProblems("R1, R2: objects and the selected level must refer to levels of the restored home",
                problems);
    }

    // ==================== R2: выбранный уровень ====================

    // R2: точка снята при выбранном нижнем уровне.
    @Test
    void testSelectedLevelIsTheOneSelectedAtTheCheckpoint() {
        SceneFacts expected = twoLevelHouseAtCheckpoint();

        ok(reapply(0));

        assertNoProblems("R2: the selected level must be the one selected when the checkpoint was taken",
                expected.selectedLevelProblems(SceneFacts.of(home)));
    }

    // R2: точка снята при выбранном верхнем уровне, после неё выбор сменился.
    @Test
    void testSelectedUpperLevelIsRestoredAfterSelectionChanged() {
        List<Level> levels = buildFull(home, 2);
        home.setSelectedLevel(levels.get(1));
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("first selected");
        home.setSelectedLevel(levels.get(0));

        ok(reapply(0));

        assertNoProblems("R2: the selected level must be the one selected when the checkpoint was taken",
                expected.selectedLevelProblems(SceneFacts.of(home)));
    }

    // ==================== R3: сами уровни ====================

    // R3: число, порядок, имя, отметка, высота, толщина перекрытия, viewable.
    @Test
    void testLevelsAreRestored() {
        List<Level> levels = buildFull(home, 3);
        home.setSelectedLevel(levels.get(0));
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("three levels");

        ok(reapply(0));

        assertNoProblems("R3: levels must be restored with their order and properties",
                expected.levelProblems(SceneFacts.of(home)));
    }

    // R3: признак viewable.
    @Test
    void testNonViewableLevelStaysNonViewable() {
        List<Level> levels = buildFull(home, 2);
        levels.get(1).setViewable(false);
        home.setSelectedLevel(levels.get(0));
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("first is not viewable");

        ok(reapply(0));

        assertNoProblems("R3: a non-viewable level must stay non-viewable",
                expected.levelProblems(SceneFacts.of(home)));
    }

    // R3: порядок. У уровней с равной отметкой он не следует из отметки — его надо сохранить.
    @Test
    void testLevelsAtEqualElevationKeepTheirOrder() {
        Level lower = new Level("Lower", 0, 12, 250);
        Level splitA = new Level("Split A", 262, 12, 120);
        Level splitB = new Level("Split B", 262, 14, 130);
        home.addLevel(lower);
        home.addLevel(splitA);
        home.addLevel(splitB);
        populate(home, lower, 0);
        populate(home, splitA, 1);
        populate(home, splitB, 2);
        home.setSelectedLevel(splitA);
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("split level");

        ok(reapply(0));

        assertNoProblems("R3: levels at the same elevation must keep their order",
                expected.levelProblems(SceneFacts.of(home)));
    }

    // R3: уровень, удалённый после точки, возвращается.
    @Test
    void testLevelDeletedAfterCheckpointComesBack() {
        List<Level> levels = buildFull(home, 3);
        home.setSelectedLevel(levels.get(0));
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("three levels");
        home.deleteLevel(levels.get(1));
        checkpoint("middle level deleted");

        ok(undo());

        assertNoProblems("R3: a level deleted after the checkpoint must come back",
                expected.levelProblems(SceneFacts.of(home)));
    }

    // R3: уровень, добавленный после точки, после восстановления исчезает.
    @Test
    void testLevelAddedAfterCheckpointIsRemoved() {
        List<Level> levels = buildFull(home, 2);
        home.setSelectedLevel(levels.get(1));
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("two levels");
        Level roof = new Level("Roof", 900, 20, 200);
        home.addLevel(roof);
        populate(home, roof, 7);
        home.setSelectedLevel(roof);
        checkpoint("roof added");

        ok(undo());

        assertNoProblems("R3: a level added after the checkpoint must be gone",
                expected.levelProblems(SceneFacts.of(home)));
    }

    // ==================== R4: независимость от выбранного уровня ====================

    /** Дом для R4: сколько уровней, на каких лежат объекты, какой выбран при точке и какой — при восстановлении. */
    static final class House {
        final int levelCount;
        final int[] populated;
        final int selectedAtCheckpoint;
        final int selectedAtRestore;

        House(int levelCount, int[] populated, int selectedAtCheckpoint, int selectedAtRestore) {
            this.levelCount = levelCount;
            this.populated = populated;
            this.selectedAtCheckpoint = selectedAtCheckpoint;
            this.selectedAtRestore = selectedAtRestore;
        }

        @Override
        public String toString() {
            String[] names = {GROUND, FIRST, ATTIC};
            List<String> populatedNames = new ArrayList<>();
            for (int index : populated) {
                populatedNames.add(names[index]);
            }
            return levelCount + " levels, objects on " + populatedNames
                    + ", checkpoint at " + names[selectedAtCheckpoint]
                    + ", restore at " + names[selectedAtRestore];
        }
    }

    static Stream<House> houses() {
        List<House> houses = new ArrayList<>();
        // Два уровня: объекты на обоих, только на нижнем, только на верхнем — при любом выборе.
        for (int[] populated : new int[][] {{0, 1}, {0}, {1}}) {
            for (int atCheckpoint = 0; atCheckpoint < 2; atCheckpoint++) {
                for (int atRestore = 0; atRestore < 2; atRestore++) {
                    houses.add(new House(2, populated, atCheckpoint, atRestore));
                }
            }
        }
        // Три уровня: объекты на всех — при любом выборе.
        for (int atCheckpoint = 0; atCheckpoint < 3; atCheckpoint++) {
            for (int atRestore = 0; atRestore < 3; atRestore++) {
                houses.add(new House(3, new int[] {0, 1, 2}, atCheckpoint, atRestore));
            }
        }
        // Три уровня: объекты не на всех уровнях, в том числе выбран пустой уровень.
        houses.add(new House(3, new int[] {0}, 2, 1));
        houses.add(new House(3, new int[] {2}, 0, 0));
        houses.add(new House(3, new int[] {0, 2}, 1, 1));
        return houses.stream();
    }

    // R4: R1–R3 при любом сочетании «где объекты — что выбрано при точке — что выбрано при восстановлении».
    @ParameterizedTest(name = "{0}")
    @MethodSource("houses")
    void testLevelsSurviveRestoreWhateverLevelIsSelected(House house) {
        List<Level> levels = build(home, house.levelCount, house.populated);
        home.setSelectedLevel(levels.get(house.selectedAtCheckpoint));
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("point");
        home.setSelectedLevel(levels.get(house.selectedAtRestore));

        ok(reapply(0));

        assertNoProblems("R4 (" + house + "): levels, selected level and levels of objects must match the checkpoint",
                expected.sceneProblems(SceneFacts.of(home)));
    }

    // ==================== R1–R3 на всех путях восстановления ====================

    // R1–R3: отмена на шаг назад (без параметров) после правок на другом уровне.
    @Test
    void testUndoBringsBackTheSceneOfThePreviousCheckpoint() {
        SceneFacts expected = twoLevelHouseAtCheckpoint();
        editAfterCheckpoint();
        checkpoint("edited");

        ok(undo());

        assertNoProblems("R1-R3: undo must bring back the scene of the previous checkpoint",
                expected.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    // R1–R3: переход к точке по номеру через одну.
    @Test
    void testRestoreByIdBringsBackTheSceneOfThatCheckpoint() {
        SceneFacts expected = twoLevelHouseAtCheckpoint();
        editAfterCheckpoint();
        checkpoint("edited");
        home.deleteLevel(level(home, GROUND));
        checkpoint("ground deleted");

        ok(restoreTo(0));

        assertNoProblems("R1-R3: restore by id must bring back the scene of that checkpoint",
                expected.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    // R1–R3: возврат вперёд (redo) к более поздней точке.
    @Test
    void testRedoBringsBackTheSceneOfTheLaterCheckpoint() {
        twoLevelHouseAtCheckpoint();
        editAfterCheckpoint();
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("edited");
        ok(restoreTo(0));

        ok(restoreTo(1));

        assertNoProblems("R1-R3: redo must bring back the scene of the later checkpoint",
                expected.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    /** Правки после точки: новый набор на First, сдвинутая стена на Ground, выбран First. */
    private void editAfterCheckpoint() {
        Level first = level(home, FIRST);
        populate(home, first, 7);
        wall(home, firstWallThickness(0)).move(37, 53);
        home.setSelectedLevel(first);
    }

    // ==================== R5: дом без уровней ====================

    // R5: уровней нет, у объектов level == null — так и остаётся.
    @Test
    void testHomeWithoutLevelsKeepsObjectsWithoutLevel() {
        build(home, 0);
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("no levels");

        ok(reapply(0));

        assertNoProblems("R5: a home without levels must come back without levels, objects with level == null",
                expected.sceneProblems(SceneFacts.of(home)));
    }

    // R5: точка снята без уровней, потом в сцене появились уровни с объектами.
    @Test
    void testCheckpointWithoutLevelsIsRestoredOverSceneThatGotLevels() {
        build(home, 0);
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("no levels");
        List<Level> levels = addLevels(home, 2);
        populate(home, levels.get(0), 1);
        populate(home, levels.get(1), 2);
        home.setSelectedLevel(levels.get(1));
        checkpoint("levels added");

        ok(undo());

        assertNoProblems("R5: a checkpoint taken without levels must come back without levels",
                expected.sceneProblems(SceneFacts.of(home)));
    }

    // ==================== R6: идентификаторы ====================

    // R6: объекты, существовавшие в момент точки, сохраняют getId().
    @Test
    void testObjectsKeepTheirIds() {
        SceneFacts expected = twoLevelHouseAtCheckpoint();

        ok(reapply(0));

        assertNoProblems("R6: objects must keep the ids they had when the checkpoint was taken",
                expected.objectIdProblems(SceneFacts.of(home)));
    }

    // R6: то же в доме без уровней.
    @Test
    void testObjectsKeepTheirIdsInHomeWithoutLevels() {
        build(home, 0);
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("no levels");

        ok(reapply(0));

        assertNoProblems("R6: objects must keep the ids they had when the checkpoint was taken",
                expected.objectIdProblems(SceneFacts.of(home)));
    }

    // R6: уровни сохраняют getId().
    @Test
    void testLevelsKeepTheirIds() {
        SceneFacts expected = twoLevelHouseAtCheckpoint();

        ok(reapply(0));

        assertNoProblems("R6: levels must keep the ids they had when the checkpoint was taken",
                expected.levelIdProblems(SceneFacts.of(home)));
    }

    // ==================== R7: контрольная точка неизменна ====================

    // R7: после восстановления объекты живой сцены сдвинуты — снимок от этого не меняется.
    @ParameterizedTest(name = "{0} levels")
    @ValueSource(ints = {0, 2})
    void testCheckpointIsNotChangedByMovingObjects(int levelCount) {
        assertCheckpointSurvives(levelCount, scene -> {
            wall(scene, firstWallThickness(0)).move(37, 53);
            room(scene, roomName(0)).move(37, 53);
            furniture(scene, tableName(0)).move(37, 53);
            furniture(scene, groupName(0)).move(37, 53);
            label(scene, labelText(0)).move(37, 53);
            dimensionLine(scene, dimensionLineOffset(0)).move(37, 53);
            polyline(scene, polylineThickness(0)).move(37, 53);
        });
    }

    // R7: после восстановления объекты живой сцены удалены.
    @ParameterizedTest(name = "{0} levels")
    @ValueSource(ints = {0, 2})
    void testCheckpointIsNotChangedByDeletingObjects(int levelCount) {
        assertCheckpointSurvives(levelCount, scene -> {
            scene.deleteWall(wall(scene, secondWallThickness(0)));
            scene.deleteRoom(room(scene, roomName(0)));
            scene.deletePieceOfFurniture(furniture(scene, tableName(0)));
            scene.deletePieceOfFurniture(furniture(scene, doorName(0)));
            scene.deletePieceOfFurniture(furniture(scene, groupName(0)));
            scene.deleteLabel(label(scene, labelText(0)));
            scene.deleteDimensionLine(dimensionLine(scene, dimensionLineOffset(0)));
            scene.deletePolyline(polyline(scene, polylineThickness(0)));
        });
    }

    // R7: после восстановления в живую сцену добавлены новые объекты.
    @ParameterizedTest(name = "{0} levels")
    @ValueSource(ints = {0, 2})
    void testCheckpointIsNotChangedByAddingObjects(int levelCount) {
        assertCheckpointSurvives(levelCount,
                scene -> populate(scene, scene.getLevels().isEmpty() ? null : scene.getLevels().get(0), 7));
    }

    // R7: после восстановления из живой сцены удалён уровень.
    @Test
    void testCheckpointIsNotChangedByDeletingALevel() {
        assertCheckpointSurvives(2, scene -> scene.deleteLevel(level(scene, FIRST)));
    }

    // R7: после восстановления уровень живой сцены переименован.
    @Test
    void testCheckpointIsNotChangedByRenamingALevel() {
        assertCheckpointSurvives(2, scene -> level(scene, FIRST).setName("Renamed"));
    }

    // R7: после восстановления у уровней живой сцены изменены отметка, высота, перекрытие, viewable.
    @Test
    void testCheckpointIsNotChangedByChangingLevelProperties() {
        assertCheckpointSurvives(2, scene -> {
            Level first = level(scene, FIRST);
            first.setElevation(400);
            first.setHeight(999);
            first.setFloorThickness(33);
            level(scene, GROUND).setViewable(false);
        });
    }

    /**
     * Точка снята, восстановлена, живая сцена изменена, та же точка восстановлена ещё раз с force —
     * сцена обязана снова совпасть с состоянием точки: уровни, выбранный уровень, уровни и
     * координаты объектов.
     */
    private void assertCheckpointSurvives(int levelCount, Consumer<Home> edit) {
        List<Level> levels = buildFull(home, levelCount);
        if (!levels.isEmpty()) {
            home.setSelectedLevel(levels.get(0));
        }
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("point");
        ok(reapply(0));
        edit.accept(home);

        ok(reapply(0));

        assertNoProblems("R7: an edit of the live scene after a restore must not change the checkpoint",
                expected.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    // R7: уход на другую точку и возврат по id — более поздняя точка не испорчена правками живой сцены.
    @Test
    void testLaterCheckpointSurvivesEditsMadeWhileAtTheEarlierOne() {
        twoLevelHouseAtCheckpoint();
        editAfterCheckpoint();
        SceneFacts expected = SceneFacts.of(home);
        checkpoint("edited");
        ok(restoreTo(0));
        editEverything();

        ok(restoreTo(1));

        assertNoProblems("R7: edits of the live scene must not change another checkpoint",
                expected.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    // R7: 0 → 1 → 0 с правками живой сцены на каждой остановке — ранняя точка не испорчена.
    @Test
    void testEarlierCheckpointSurvivesSwitchingAwayAndBack() {
        SceneFacts expected = twoLevelHouseAtCheckpoint();
        editAfterCheckpoint();
        checkpoint("edited");
        ok(restoreTo(0));
        editEverything();
        ok(restoreTo(1));
        editEverything();

        ok(restoreTo(0));

        assertNoProblems("R7: edits of the live scene must not change the checkpoint the scene came from",
                expected.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    /** Правки живой сцены всех видов из R7: сдвиг, удаление, добавление, переименование и удаление уровня. */
    private void editEverything() {
        wall(home, firstWallThickness(0)).move(11, 17);
        home.deleteLabel(label(home, labelText(0)));
        home.deletePieceOfFurniture(furniture(home, groupName(0)));
        populate(home, level(home, GROUND), 9);
        level(home, GROUND).setName("Renamed");
        home.deleteLevel(level(home, FIRST));
    }

    // ==================== R8: связи ====================

    // R8: соединённые стены остаются соединёнными — и с экземплярами из восстановленной сцены.
    @ParameterizedTest(name = "{0} levels")
    @ValueSource(ints = {0, 2})
    void testConnectedWallsStayConnectedToWallsOfTheRestoredScene(int levelCount) {
        buildFull(home, levelCount);
        checkpoint("point");

        ok(reapply(0));

        assertNoProblems("R8: walls connected at the checkpoint must be connected to walls of the restored scene",
                wallConnectionProblems(Math.max(levelCount, 1)));
    }

    // R8: то же, когда одна и та же точка восстановлена дважды подряд.
    @ParameterizedTest(name = "{0} levels")
    @ValueSource(ints = {0, 2})
    void testConnectedWallsStayConnectedWhenCheckpointIsRestoredTwice(int levelCount) {
        buildFull(home, levelCount);
        checkpoint("point");
        ok(reapply(0));

        ok(reapply(0));

        assertNoProblems("R8: walls connected at the checkpoint must be connected to walls of the restored scene",
                wallConnectionProblems(Math.max(levelCount, 1)));
    }

    // R7, R8: соединённая стена удалена из живой сцены (модель при этом разрывает связь у соседа) —
    // в точке связь цела, после повторного восстановления стены снова соединены.
    @ParameterizedTest(name = "{0} levels")
    @ValueSource(ints = {0, 2})
    void testConnectedWallsStayConnectedAfterOneOfThemWasDeleted(int levelCount) {
        buildFull(home, levelCount);
        checkpoint("point");
        ok(reapply(0));
        home.deleteWall(wall(home, secondWallThickness(0)));

        ok(reapply(0));

        assertNoProblems("R7, R8: deleting a wall of the live scene must not disconnect walls of the checkpoint",
                wallConnectionProblems(Math.max(levelCount, 1)));
    }

    /** В каждом из {@code slots} наборов первая стена концом соединена со второй, вторая началом — с первой. */
    private List<String> wallConnectionProblems(int slots) {
        List<String> problems = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            Wall first = wall(home, firstWallThickness(slot));
            Wall second = wall(home, secondWallThickness(slot));
            checkConnection(problems, first, "wallAtEnd", first.getWallAtEnd(), second);
            checkConnection(problems, second, "wallAtStart", second.getWallAtStart(), first);
        }
        // Ни одна стена сцены не должна ссылаться на стену вне сцены.
        for (Wall wall : home.getWalls()) {
            for (Wall neighbour : new Wall[] {wall.getWallAtStart(), wall.getWallAtEnd()}) {
                if (neighbour != null && !containsInstance(home.getWalls(), neighbour)) {
                    problems.add(SceneFacts.wallKey(wall) + " is connected to " + SceneFacts.wallKey(neighbour)
                            + " (id " + neighbour.getId() + ") that is not a wall of the scene");
                }
            }
        }
        return problems;
    }

    private static void checkConnection(List<String> problems, Wall wall, String side, Wall actual, Wall expected) {
        if (actual == null) {
            problems.add(SceneFacts.wallKey(wall) + ": " + side + " expected " + SceneFacts.wallKey(expected)
                    + " but the wall is not connected");
        } else if (actual != expected) {
            problems.add(SceneFacts.wallKey(wall) + ": " + side + " expected the scene's "
                    + SceneFacts.wallKey(expected) + " but is another instance, " + SceneFacts.wallKey(actual));
        }
    }

    private static boolean containsInstance(Iterable<?> items, Object item) {
        for (Object candidate : items) {
            if (candidate == item) {
                return true;
            }
        }
        return false;
    }

    // R8: члены группы мебели остаются в группе.
    @ParameterizedTest(name = "{0} levels")
    @ValueSource(ints = {0, 2})
    void testFurnitureGroupKeepsItsMembers(int levelCount) {
        buildFull(home, levelCount);
        checkpoint("point");

        ok(reapply(0));

        for (int slot = 0; slot < Math.max(levelCount, 1); slot++) {
            HomePieceOfFurniture group = furniture(home, groupName(slot));
            assertTrue(group instanceof HomeFurnitureGroup,
                    "R8: '" + groupName(slot) + "' must still be a furniture group, was " + group.getClass());
            List<String> members = new ArrayList<>();
            for (HomePieceOfFurniture member : ((HomeFurnitureGroup) group).getFurniture()) {
                members.add(member.getName());
            }
            assertEquals(List.of(chairName(slot, 'a'), chairName(slot, 'b')), members,
                    "R8: members of '" + groupName(slot) + "' must stay in the group");
            for (HomePieceOfFurniture piece : home.getFurniture()) {
                assertFalse(members.contains(piece.getName()),
                        "R8: '" + piece.getName() + "' must stay inside '" + groupName(slot)
                                + "', not become top-level furniture");
            }
        }
    }

    // ==================== R9: счётчики в ответе ====================

    // R9: levels, walls, rooms, furniture, labels, dimensionLines, polylines — фактическое содержимое сцены.
    @Test
    void testResponseCountersMatchTheRestoredScene() {
        twoLevelHouseAtCheckpoint();
        clear(home);
        checkpoint("cleared");

        Response response = ok(undo());

        Map<String, Integer> scene = new LinkedHashMap<>();
        scene.put("levels", home.getLevels().size());
        scene.put("walls", home.getWalls().size());
        scene.put("rooms", home.getRooms().size());
        scene.put("furniture", home.getFurniture().size());
        scene.put("labels", home.getLabels().size());
        scene.put("dimensionLines", home.getDimensionLines().size());
        scene.put("polylines", home.getPolylines().size());
        List<String> problems = new ArrayList<>();
        scene.forEach((counter, inScene) -> {
            Object reported = response.getData().get(counter);
            if (!(reported instanceof Number) || ((Number) reported).intValue() != inScene) {
                problems.add("'" + counter + "': response says " + reported + ", the scene has " + inScene);
            }
        });
        assertNoProblems("R9: counters in the response must equal the contents of the restored scene", problems);
    }

    // ==================== G1: поток ====================

    // G1: restore_checkpoint меняет Home только в потоке EDT.
    @Test
    void testSceneIsChangedOnTheEventDispatchThread() {
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test itself must not run on the EDT");
        twoLevelHouseAtCheckpoint();
        editAfterCheckpoint();
        List<Boolean> onEdt = Collections.synchronizedList(new ArrayList<>());
        recordThreadOfEveryChange(home, onEdt);

        ok(reapply(0));

        assertFalse(onEdt.isEmpty(), "restore_checkpoint was expected to change the model");
        assertFalse(onEdt.contains(Boolean.FALSE), "G1: the model must only be changed inside runOnEDT");
    }

    // ==================== Helpers ====================

    /** Дом из двух уровней с набором объектов на каждом, выбран Ground; точка 0 снята. */
    private SceneFacts twoLevelHouseAtCheckpoint() {
        List<Level> levels = buildFull(home, 2);
        home.setSelectedLevel(levels.get(0));
        SceneFacts facts = SceneFacts.of(home);
        assertTrue(facts.duplicates.isEmpty(), "fixture objects must be told apart: " + facts.duplicates);
        checkpoint("two levels");
        return facts;
    }

    private void checkpoint(String description) {
        Response response = checkpointHandler.execute(makeRequest("checkpoint", "description", description), accessor);
        assertTrue(response.isOk(), "checkpoint failed: " + response.getMessage());
    }

    /** Отмена на шаг назад: restore_checkpoint без параметров. */
    private Response undo() {
        return restoreHandler.execute(makeRequest("restore_checkpoint"), accessor);
    }

    /** Переход к точке по номеру. */
    private Response restoreTo(int id) {
        return restoreHandler.execute(makeRequest("restore_checkpoint", "id", id), accessor);
    }

    /** Повторное применение точки: тот же номер и force. */
    private Response reapply(int id) {
        return restoreHandler.execute(makeRequest("restore_checkpoint", "id", id, "force", true), accessor);
    }

    private static Response ok(Response response) {
        assertTrue(response.isOk(), "restore_checkpoint failed: " + response.getMessage());
        return response;
    }
}
