package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.viewcontroller.ExportableView;
import com.eteks.sweethome3d.viewcontroller.PlanController;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.SwingUtilities;
import javax.swing.undo.UndoableEditSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Выбранный уровень и объекты без уровня.
 * <ul>
 *   <li>X1 — {@code export_plan_image} не меняет ни выбранный уровень, ни выделение: после команды они те
 *       же, что до неё (сейчас уровень меняется, когда на выбранном уровне нет объектов, а на других
 *       есть);</li>
 *   <li>X2 — {@code add_level} в доме, где уже есть объекты, но нет уровней, не оставляет объектов с уровнем
 *       {@code null}. В SH3D 7.5 «Add level» в таком доме создаёт два уровня — нижний
 *       для прежних объектов и новый. В {@code add_level}: (а) запрошенный уровень с {@code elevation} 0 — прежние объекты
 *       переносятся на него, уровень один; (б) иначе — сначала создаётся нижний уровень так же, как его
 *       создаёт {@code PlanController.addLevel} SH3D 7.5 (имя, отметка 0, толщина пола и высота — как там),
 *       прежние объекты переносятся на него, затем добавляется запрошенный. Ответ — о запрошенном уровне,
 *       как сейчас; {@code levelCount} учитывает оба. В доме без объектов — как сейчас (один уровень).</li>
 * </ul>
 *
 * <p>Эталон нижнего уровня — сам {@code PlanController.addLevel()} SH3D 7.5 на такой же сцене. По байткоду
 * ({@code PlanController.addLevel(boolean)}): при пустом {@code home.getLevels()} —
 * {@code createLevel(preferences.getLocalizedString(PlanController.class, "levelName", 0), 0,
 * preferences.getNewFloorThickness(), preferences.getNewWallHeight())}, затем
 * {@code moveHomeItemsToLevel(level)}: мебель, стены, комнаты, полилинии, размерные линии, надписи.
 * Настройки здесь — настоящие настройки SH3D с изменёнными высотой стен (262) и толщиной пола (15), чтобы
 * значения «как там» отличались от значений по умолчанию.
 */
class SelectedLevelAndLevellessObjectsTest {

    private static UserPreferences preferences;

    @BeforeAll
    static void loadPreferences() {
        preferences = Sh3dPreferences.create();
        preferences.setNewWallHeight(262);
        preferences.setNewFloorThickness(15);
    }

    // ==================== X1: export_plan_image ====================

    @Test
    void exportPlanImageKeepsAnEmptySelectedLevelWhenObjectsAreOnAnotherOne() throws Exception {
        Home home = new Home();
        Level ground = addLevel(home, "Ground", 0);
        Level first = addLevel(home, "First", 262);
        addWall(home, first, 0, 0, 400, 0);
        addWall(home, first, 400, 0, 400, 300);
        home.setSelectedLevel(ground);
        home.setSelectedItems(Collections.<Selectable>emptyList());

        exportPlanImage(home);

        assertSame(ground, home.getSelectedLevel(), "X1: export_plan_image must not change the selected level");
        assertEquals(Collections.emptyList(), home.getSelectedItems(), "X1: export_plan_image must not change "
                + "the selection");
    }

    @Test
    void exportPlanImageKeepsAnEmptyUpperSelectedLevel() throws Exception {
        Home home = new Home();
        Level ground = addLevel(home, "Ground", 0);
        Level first = addLevel(home, "First", 262);
        addWall(home, ground, 0, 0, 400, 0);
        home.setSelectedLevel(first);
        home.setSelectedItems(Collections.<Selectable>emptyList());

        exportPlanImage(home);

        assertSame(first, home.getSelectedLevel(), "X1: export_plan_image must not change the selected level");
        assertEquals(Collections.emptyList(), home.getSelectedItems());
    }

    @Test
    void exportPlanImageKeepsTheSelectedLevelAndTheSelection() throws Exception {
        Home home = new Home();
        Level ground = addLevel(home, "Ground", 0);
        Level first = addLevel(home, "First", 262);
        Wall lower = addWall(home, ground, 0, 0, 400, 0);
        Wall upper = addWall(home, first, 0, 0, 400, 0);
        home.setSelectedLevel(first);
        home.setSelectedItems(Collections.<Selectable>singletonList(upper));

        exportPlanImage(home);

        assertSame(first, home.getSelectedLevel());
        assertEquals(Collections.singletonList(upper), home.getSelectedItems());
        assertNotNull(lower);
    }

    @Test
    void exportPlanImageKeepsTheSelectionInAHomeWithoutLevels() throws Exception {
        Home home = new Home();
        Wall wall = addWall(home, null, 0, 0, 400, 0);
        addWall(home, null, 400, 0, 400, 300);
        home.setSelectedItems(Collections.<Selectable>singletonList(wall));

        exportPlanImage(home);

        assertNull(home.getSelectedLevel());
        assertEquals(Collections.singletonList(wall), home.getSelectedItems());
    }

    // ==================== X2: add_level в доме с объектами без уровней ====================

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"wall", "room", "furniture", "label", "dimension line", "polyline", "everything"})
    void addingAnUpperLevelPutsTheExistingObjectsOnALowerLevelCreatedAsSweetHome3DDoes(String kind)
            throws Exception {
        Level expectedLower = lowerLevelCreatedBySweetHome3D(kind);
        Home home = new Home();
        List<Object> objects = addObjects(home, kind);

        Response response = addLevel(home, "First", 300.0);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(2, home.getLevels().size(), "X2(b): a lower level for the existing objects plus the "
                + "requested one, levels: " + names(home.getLevels()));
        Level lower = home.getLevels().get(0);
        Level requested = home.getLevels().get(1);
        assertAll("X2(b): the lower level is created as PlanController.addLevel creates it",
                () -> assertEquals(expectedLower.getName(), lower.getName(), "name"),
                () -> assertEquals(0f, lower.getElevation(), 0.0f, "elevation"),
                () -> assertEquals(expectedLower.getFloorThickness(), lower.getFloorThickness(), 0.0f,
                        "floor thickness"),
                () -> assertEquals(expectedLower.getHeight(), lower.getHeight(), 0.0f, "height"));
        for (Object object : objects) {
            assertSame(lower, levelOf(object), "X2: " + object.getClass().getSimpleName()
                    + " created before any level must move to the lower level");
        }
        assertEquals("First", requested.getName());
        assertEquals(300f, requested.getElevation(), 0.0f);
        assertEquals(requested.getId(), response.getData().get("id"), "X2: the answer is about the requested level");
        assertEquals("First", response.getData().get("name"));
        assertEquals(2, ((Number) response.getData().get("levelCount")).intValue(),
                "X2: levelCount counts both levels");
        assertSame(requested, home.getSelectedLevel(), "the new level becomes the selected one, as now");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"wall", "label", "everything"})
    void addingALevelAtElevationZeroPutsTheExistingObjectsOnIt(String kind) {
        Home home = new Home();
        List<Object> objects = addObjects(home, kind);

        Response response = addLevel(home, "Ground", 0.0);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(1, home.getLevels().size(), "X2(a): one level, levels: " + names(home.getLevels()));
        Level ground = home.getLevels().get(0);
        assertEquals("Ground", ground.getName());
        for (Object object : objects) {
            assertSame(ground, levelOf(object), "X2(a): " + object.getClass().getSimpleName()
                    + " created before any level must move to the requested level");
        }
        assertEquals(1, ((Number) response.getData().get("levelCount")).intValue());
    }

    @Test
    void addingALevelToAnEmptyHomeCreatesOneLevel() {
        Home home = new Home();

        Response response = addLevel(home, "First", 300.0);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(1, home.getLevels().size(), "X2: in a home without objects — one level, as now");
        assertEquals("First", home.getLevels().get(0).getName());
        assertEquals(1, ((Number) response.getData().get("levelCount")).intValue());
    }

    @Test
    void addingALevelToAHomeWithLevelsAddsOnlyThatLevel() {
        Home home = new Home();
        Level ground = addLevel(home, "Ground", 0);
        Wall wall = addWall(home, ground, 0, 0, 400, 0);

        Response response = addLevel(home, "First", 262.0);

        assertTrue(response.isOk(), response.getMessage());
        assertEquals(2, home.getLevels().size());
        assertSame(ground, wall.getLevel());
    }

    // ==================== Helpers ====================

    private static Level addLevel(Home home, String name, float elevation) {
        Level level = new Level(name, elevation, 12, 250);
        home.addLevel(level);
        return level;
    }

    private static Wall addWall(Home home, Level level, float xStart, float yStart, float xEnd, float yEnd) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10, 250);
        home.addWall(wall);
        wall.setLevel(level);
        return wall;
    }

    private static Response addLevel(Home home, String name, double elevation) {
        return new AddLevelHandler().execute(makeRequest("add_level", "name", name, "elevation", elevation),
                new HomeAccessor(home, preferences));
    }

    /** Объекты одного вида (или всех видов) без уровня. */
    private static List<Object> addObjects(Home home, String kind) {
        List<Object> objects = new ArrayList<>();
        boolean all = kind.equals("everything");
        if (all || kind.equals("wall")) {
            Wall wall = new Wall(0, 0, 400, 0, 10, 250);
            home.addWall(wall);
            objects.add(wall);
        }
        if (all || kind.equals("room")) {
            Room room = new Room(new float[][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}});
            home.addRoom(room);
            objects.add(room);
        }
        if (all || kind.equals("furniture")) {
            HomePieceOfFurniture piece = new HomePieceOfFurniture(
                    new CatalogPieceOfFurniture("Table", null, null, 100f, 60f, 75f, true, false));
            home.addPieceOfFurniture(piece);
            objects.add(piece);
        }
        if (all || kind.equals("label")) {
            Label label = new Label("Hall", 100, 100);
            home.addLabel(label);
            objects.add(label);
        }
        if (all || kind.equals("dimension line")) {
            DimensionLine line = new DimensionLine(0, -50, 400, -50, 20);
            home.addDimensionLine(line);
            objects.add(line);
        }
        if (all || kind.equals("polyline")) {
            Polyline polyline = new Polyline(new float[][] {{0, 400}, {200, 400}});
            home.addPolyline(polyline);
            objects.add(polyline);
        }
        for (Object object : objects) {
            assertNull(levelOf(object), "fixture: objects are created before any level");
        }
        return objects;
    }

    private static Level levelOf(Object object) {
        if (object instanceof Wall) {
            return ((Wall) object).getLevel();
        }
        if (object instanceof Room) {
            return ((Room) object).getLevel();
        }
        if (object instanceof HomePieceOfFurniture) {
            return ((HomePieceOfFurniture) object).getLevel();
        }
        if (object instanceof Label) {
            return ((Label) object).getLevel();
        }
        if (object instanceof DimensionLine) {
            return ((DimensionLine) object).getLevel();
        }
        return ((Polyline) object).getLevel();
    }

    /** Нижний уровень, который создаёт «Add level» самого SH3D в таком же доме. */
    private static Level lowerLevelCreatedBySweetHome3D(String kind) throws Exception {
        Home home = new Home();
        List<Object> objects = addObjects(home, kind);
        SwingUtilities.invokeAndWait(() -> new PlanController(home, preferences, new SwingViewFactory(), null,
                new UndoableEditSupport()).addLevel());
        Level lower = home.getLevels().get(0);
        for (Object object : objects) {
            assertSame(lower, levelOf(object), "oracle: SH3D moves the objects to the lower level");
        }
        return lower;
    }

    private void exportPlanImage(Home home) throws Exception {
        AtomicReference<ExportableView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set((ExportableView) new PlanController(home, preferences,
                new SwingViewFactory(), null, new UndoableEditSupport()).getView()));
        Response response = new ExportPlanImageHandler(view.get()).execute(
                makeRequest("export_plan_image", "width", 200), new HomeAccessor(home, preferences));
        assertTrue(response.isOk(), "export_plan_image failed: " + response.getMessage());
    }

    private static List<String> names(List<Level> levels) {
        List<String> names = new ArrayList<>();
        for (Level level : levels) {
            names.add(level.getName() + "@" + level.getElevation());
        }
        return names;
    }
}
