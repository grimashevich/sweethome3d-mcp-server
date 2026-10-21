package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.sh3d.mcp.command.handler.LevelSceneFixture.*;
import static com.sh3d.mcp.command.handler.SceneFacts.assertNoProblems;
import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * load_home и уровни: круг save_home → правка сцены → load_home того же файла обязан вернуть
 * сцену, записанную в файл. Требования, на которые ссылаются комментарии к тестам:
 * <ul>
 *   <li>L1 — каждый объект на том уровне, на котором записан; выбран уровень, выбранный в файле;
 *       уровни те же: число, порядок, имя, отметка, высота, толщина перекрытия, viewable;</li>
 *   <li>L2 — файл без уровней, загруженный в сцену с уровнями, оставляет сцену без уровней;</li>
 *   <li>L3 — файл с уровнями, загруженный в сцену без уровней, приносит уровни и объекты на них;</li>
 *   <li>L4 — идентификаторы объектов и уровней равны записанным в файл;</li>
 *   <li>G1 — модель меняется только в потоке EDT.</li>
 * </ul>
 *
 * <p>Здесь команда работает с голой моделью. Те же тесты в доме, который ведёт себя как
 * в приложении, — {@link LoadHomeLevelsInApplicationTest}.
 */
class LoadHomeLevelsTest {

    @TempDir
    Path tempDir;

    private SaveHomeHandler saveHandler;
    private LoadHomeHandler loadHandler;
    private Home home;

    @BeforeEach
    void setUp() {
        saveHandler = new SaveHomeHandler();
        loadHandler = new LoadHomeHandler();
        home = newHome();
    }

    /** Дом, с которым работает команда. */
    Home newHome() {
        return new Home();
    }

    // ==================== L1: объекты, выбранный уровень, уровни ====================

    // L1: каждый объект на том уровне, на котором записан в файл; число объектов по уровням то же.
    @Test
    void testObjectsStayOnTheirLevels() {
        SceneFacts saved = twoLevelHouse();
        Path file = save(home);

        load(home, file);

        assertNoProblems("L1: every object must be on the level it has in the file",
                saved.objectLevelProblems(SceneFacts.of(home)));
    }

    // L1: «объект на уровне» значит на уровне самого дома, а не на постороннем экземпляре с тем же именем.
    @Test
    void testObjectsReferToLevelInstancesOfTheLoadedHome() {
        List<Level> levels = buildFull(home, 3);
        home.setSelectedLevel(levels.get(1));
        Path file = save(home);
        home.setSelectedLevel(levels.get(2));

        load(home, file);

        SceneFacts actual = SceneFacts.of(home);
        List<String> problems = SceneFacts.foreignLevelProblems(home);
        actual.objects.forEach((key, fact) -> {
            if (SceneFacts.NO_LEVEL.equals(fact.level)) {
                problems.add(key + " has no level at all");
            }
        });
        assertFalse(actual.objects.isEmpty(), "the loaded scene has no objects");
        assertNoProblems("L1: objects and the selected level must refer to levels of the loaded home", problems);
    }

    // L1: выбранный уровень — как в файле (записан нижний).
    @Test
    void testSelectedLevelIsTheOneSavedInTheFile() {
        SceneFacts saved = twoLevelHouse();
        Path file = save(home);

        load(home, file);

        assertNoProblems("L1: the selected level must be the one saved in the file",
                saved.selectedLevelProblems(SceneFacts.of(home)));
    }

    // L1: выбранный уровень — как в файле (записан верхний), к моменту загрузки выбор сменился.
    @Test
    void testSelectedUpperLevelIsLoadedAfterSelectionChanged() {
        List<Level> levels = buildFull(home, 2);
        home.setSelectedLevel(levels.get(1));
        SceneFacts saved = SceneFacts.of(home);
        Path file = save(home);
        home.setSelectedLevel(levels.get(0));

        load(home, file);

        assertNoProblems("L1: the selected level must be the one saved in the file",
                saved.selectedLevelProblems(SceneFacts.of(home)));
    }

    // L1: уровни — число, порядок, имя, отметка, высота, толщина перекрытия, viewable.
    @Test
    void testLevelsAreLoaded() {
        List<Level> levels = buildFull(home, 3);
        levels.get(2).setViewable(false);
        home.setSelectedLevel(levels.get(0));
        SceneFacts saved = SceneFacts.of(home);
        Path file = save(home);

        load(home, file);

        assertNoProblems("L1: levels must be loaded with their order and properties",
                saved.levelProblems(SceneFacts.of(home)));
    }

    // L1: порядок уровней с равной отметкой не следует из отметки — он обязан сохраниться.
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
        SceneFacts saved = SceneFacts.of(home);
        Path file = save(home);

        load(home, file);

        assertNoProblems("L1: levels at the same elevation must keep their order",
                saved.levelProblems(SceneFacts.of(home)));
    }

    /** Что записано в файл: сколько уровней, на каких лежат объекты, какой выбран. */
    static final class SavedHouse {
        final int levelCount;
        final int[] populated;
        final int selected;

        SavedHouse(int levelCount, int[] populated, int selected) {
            this.levelCount = levelCount;
            this.populated = populated;
            this.selected = selected;
        }

        @Override
        public String toString() {
            String[] names = {GROUND, FIRST, ATTIC};
            List<String> populatedNames = new ArrayList<>();
            for (int index : populated) {
                populatedNames.add(names[index]);
            }
            return "file: " + levelCount + " levels, objects on " + populatedNames + ", selected " + names[selected];
        }
    }

    /** Что происходит со сценой между сохранением и загрузкой. */
    enum SceneAtLoad {
        UNTOUCHED("scene untouched"),
        ANOTHER_LEVEL_SELECTED("another level selected"),
        EDITED("scene edited"),
        CLEARED("scene cleared"),
        OTHER_LEVELS("scene has other levels");

        private final String title;

        SceneAtLoad(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    static Stream<Arguments> roundTrips() {
        SavedHouse[] houses = {
                new SavedHouse(2, new int[] {0, 1}, 0),
                new SavedHouse(2, new int[] {0, 1}, 1),
                new SavedHouse(2, new int[] {0}, 0),
                new SavedHouse(2, new int[] {1}, 0),
                new SavedHouse(3, new int[] {0, 1, 2}, 1),
                new SavedHouse(3, new int[] {0, 2}, 2),
        };
        List<Arguments> all = new ArrayList<>();
        for (SavedHouse house : houses) {
            for (SceneAtLoad scene : SceneAtLoad.values()) {
                all.add(Arguments.of(house, scene));
            }
        }
        return all.stream();
    }

    // L1: дом из 2 и 3 уровней, объекты на разных уровнях; к моменту загрузки сцена не тронута,
    // выбран другой уровень, сцена правлена, очищена или в ней вообще другие уровни.
    @ParameterizedTest(name = "{0}; {1}")
    @MethodSource("roundTrips")
    void testSaveThenLoadBringsBackTheSavedScene(SavedHouse house, SceneAtLoad sceneAtLoad) {
        List<Level> levels = build(home, house.levelCount, house.populated);
        home.setSelectedLevel(levels.get(house.selected));
        SceneFacts saved = SceneFacts.of(home);
        Path file = save(home);
        change(home, sceneAtLoad, levels.get((house.selected + 1) % house.levelCount));

        load(home, file);

        assertNoProblems("L1 (" + house + "; " + sceneAtLoad
                        + "): levels, selected level and levels of objects must match the file",
                saved.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    private static void change(Home scene, SceneAtLoad sceneAtLoad, Level anotherLevel) {
        switch (sceneAtLoad) {
            case UNTOUCHED:
                break;
            case ANOTHER_LEVEL_SELECTED:
                scene.setSelectedLevel(anotherLevel);
                break;
            case EDITED:
                List<Level> current = scene.getLevels();
                scene.deleteLevel(current.get(current.size() - 1));
                Level lowest = scene.getLevels().get(0);
                lowest.setName("Renamed");
                lowest.setElevation(50);
                populate(scene, lowest, 7);
                scene.setSelectedLevel(lowest);
                break;
            case CLEARED:
                clear(scene);
                break;
            case OTHER_LEVELS:
                clear(scene);
                Level basement = new Level("Basement", -300, 20, 280);
                Level roof = new Level("Roof", 900, 18, 200);
                scene.addLevel(basement);
                scene.addLevel(roof);
                populate(scene, basement, 8);
                populate(scene, roof, 9);
                scene.setSelectedLevel(roof);
                break;
            default:
                fail("unknown scene change: " + sceneAtLoad);
        }
    }

    // ==================== L2: файл без уровней → сцена с уровнями ====================

    // L2: после загрузки уровней нет, у объектов level == null.
    @Test
    void testFileWithoutLevelsLoadedIntoSceneWithLevels() {
        Home source = newHome();
        build(source, 0);
        SceneFacts saved = SceneFacts.of(source);
        Path file = save(source);
        List<Level> levels = buildFull(home, 2);
        home.setSelectedLevel(levels.get(1));

        load(home, file);

        assertNoProblems("L2: a file without levels must leave the scene without levels, objects with level == null",
                saved.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    // L2: то же, когда уровни появились в той же сцене после сохранения.
    @Test
    void testFileWithoutLevelsLoadedBackAfterSceneGotLevels() {
        build(home, 0);
        SceneFacts saved = SceneFacts.of(home);
        Path file = save(home);
        List<Level> levels = addLevels(home, 2);
        populate(home, levels.get(0), 1);
        populate(home, levels.get(1), 2);
        home.setSelectedLevel(levels.get(1));

        load(home, file);

        assertNoProblems("L2: a file without levels must leave the scene without levels, objects with level == null",
                saved.sceneAndCoordinateProblems(SceneFacts.of(home)));
    }

    // ==================== L3: файл с уровнями → сцена без уровней ====================

    // L3: загрузка в пустой дом.
    @Test
    void testFileWithLevelsLoadedIntoEmptyHome() {
        SceneFacts saved = twoLevelHouse();
        Path file = save(home);
        Home target = newHome();

        load(target, file);

        assertNoProblems("L3: a file with levels loaded into an empty home must bring its levels and objects on them",
                saved.sceneAndCoordinateProblems(SceneFacts.of(target)));
    }

    // L3: загрузка в дом, где уровней нет, а объекты есть.
    @Test
    void testFileWithLevelsLoadedIntoSceneWithoutLevels() {
        List<Level> levels = buildFull(home, 3);
        home.setSelectedLevel(levels.get(2));
        SceneFacts saved = SceneFacts.of(home);
        Path file = save(home);
        Home target = newHome();
        populate(target, null, 7);

        load(target, file);

        assertNoProblems("L3: a file with levels loaded into a scene without levels must bring its levels "
                        + "and objects on them",
                saved.sceneAndCoordinateProblems(SceneFacts.of(target)));
    }

    // ==================== L4: идентификаторы ====================

    // L4: идентификаторы объектов после загрузки — те, что записаны в файл.
    @Test
    void testObjectsKeepTheIdsSavedInTheFile() {
        SceneFacts saved = twoLevelHouse();
        Path file = save(home);
        Home target = newHome();

        load(target, file);

        assertNoProblems("L4: objects must have the ids saved in the file",
                saved.objectIdProblems(SceneFacts.of(target)));
    }

    // L4: то же для файла без уровней.
    @Test
    void testObjectsKeepTheIdsSavedInTheFileWithoutLevels() {
        build(home, 0);
        SceneFacts saved = SceneFacts.of(home);
        Path file = save(home);
        Home target = newHome();

        load(target, file);

        assertNoProblems("L4: objects must have the ids saved in the file",
                saved.objectIdProblems(SceneFacts.of(target)));
    }

    // L4: идентификаторы уровней после загрузки — те, что записаны в файл.
    @Test
    void testLevelsKeepTheIdsSavedInTheFile() {
        SceneFacts saved = twoLevelHouse();
        Path file = save(home);
        Home target = newHome();

        load(target, file);

        assertNoProblems("L4: levels must have the ids saved in the file",
                saved.levelIdProblems(SceneFacts.of(target)));
    }

    // ==================== G1: поток ====================

    // G1: load_home меняет Home только в потоке EDT.
    @Test
    void testSceneIsChangedOnTheEventDispatchThread() {
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test itself must not run on the EDT");
        twoLevelHouse();
        Path file = save(home);
        populate(home, level(home, FIRST), 7);
        List<Boolean> onEdt = Collections.synchronizedList(new ArrayList<>());
        recordThreadOfEveryChange(home, onEdt);

        load(home, file);

        assertFalse(onEdt.isEmpty(), "load_home was expected to change the model");
        assertFalse(onEdt.contains(Boolean.FALSE), "G1: the model must only be changed inside runOnEDT");
    }

    // ==================== Helpers ====================

    /** Дом из двух уровней с набором объектов на каждом, выбран Ground. */
    private SceneFacts twoLevelHouse() {
        List<Level> levels = buildFull(home, 2);
        home.setSelectedLevel(levels.get(0));
        SceneFacts facts = SceneFacts.of(home);
        assertTrue(facts.duplicates.isEmpty(), "fixture objects must be told apart: " + facts.duplicates);
        return facts;
    }

    /** Сохраняет дом командой save_home во временный каталог теста. */
    private Path save(Home source) {
        Path file = tempDir.resolve("house.sh3d");
        Response response = saveHandler.execute(
                makeRequest("save_home", "filePath", file.toString()), createAccessor(source));
        assertTrue(response.isOk(), "save_home failed: " + response.getMessage());
        assertTrue(Files.isRegularFile(file), "save_home reported success but wrote no file");
        return file;
    }

    private void load(Home target, Path file) {
        Response response = loadHandler.execute(
                makeRequest("load_home", "filePath", file.toString()), createAccessor(target));
        assertTrue(response.isOk(), "load_home failed: " + response.getMessage());
    }
}
