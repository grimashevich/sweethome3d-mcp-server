package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CollectionEvent;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Sash;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Сцены из нескольких уровней для тестов восстановления и загрузки.
 *
 * <p>На уровень кладётся «набор» — по объекту каждого вида: две соединённые стены, комната,
 * мебель, дверь, группа мебели из двух стульев, надпись, размерная линия, полилиния. Наборы
 * различаются номером ({@code slot}): от него зависят координаты и те свойства, по которым
 * {@link SceneFacts} опознаёт объект, так что в одной сцене ключи не повторяются.
 *
 * <p>Объект ставится на уровень явным {@code setLevel} после добавления: {@code Home.addXxx}
 * сам назначает объекту выбранный в этот момент уровень, а сцене нужны объекты и на
 * невыбранных уровнях.
 */
final class LevelSceneFixture {

    static final String GROUND = "Ground";
    static final String FIRST = "First";
    static final String ATTIC = "Attic";

    /** Имя, отметка, толщина перекрытия, высота. Значения у уровней разные — перепутанные поля заметны. */
    private static final Object[][] LEVELS = {
            {GROUND, 0f, 12f, 250f},
            {FIRST, 262f, 14f, 270f},
            {ATTIC, 546f, 16f, 230f},
    };

    private LevelSceneFixture() {
    }

    // ==================== Построение ====================

    /**
     * Дом, который ведёт себя как в работающем приложении: там {@code HomeController} слушает состав
     * уровней и каждый добавленный уровень делает выбранным. В голой модели такого слушателя нет,
     * поэтому команда, которая полагается на выбранный уровень, в приложении и в тесте на голой
     * модели приходит к разным сценам.
     */
    static Home applicationHome() {
        Home home = new Home();
        home.addLevelsListener(ev -> {
            if (ev.getType() == CollectionEvent.Type.ADD) {
                home.setSelectedLevel(ev.getItem());
            }
        });
        return home;
    }

    /**
     * Вариант для проверок камер: настоящий контроллер 3D сдвигает камеру наблюдателя
     * при смене выбранного уровня. Создавать и менять такой дом следует в EDT.
     */
    static Home applicationHomeWith3DController() {
        Home home = applicationHome();
        new HomeController3D(home, new DefaultUserPreferences(), null, null, null);
        return home;
    }

    /** Добавляет в дом первые {@code count} уровней (до трёх) и возвращает их снизу вверх. */
    static List<Level> addLevels(Home home, int count) {
        List<Level> levels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Level level = new Level((String) LEVELS[i][0], (Float) LEVELS[i][1],
                    (Float) LEVELS[i][2], (Float) LEVELS[i][3]);
            home.addLevel(level);
            levels.add(level);
        }
        return levels;
    }

    /**
     * Дом из {@code levelCount} уровней с наборами объектов на уровнях {@code populated}
     * (номер набора равен номеру уровня). При {@code levelCount == 0} — один набор без уровня.
     */
    static List<Level> build(Home home, int levelCount, int... populated) {
        List<Level> levels = addLevels(home, levelCount);
        if (levelCount == 0) {
            populate(home, null, 0);
        }
        for (int index : populated) {
            populate(home, levels.get(index), index);
        }
        return levels;
    }

    /** Дом, в котором набор объектов лежит на каждом уровне. */
    static List<Level> buildFull(Home home, int levelCount) {
        int[] all = new int[levelCount];
        for (int i = 0; i < levelCount; i++) {
            all[i] = i;
        }
        return build(home, levelCount, all);
    }

    /** Кладёт на уровень (или в дом без уровней, если {@code level == null}) набор номер {@code slot}. */
    static void populate(Home home, Level level, int slot) {
        float dx = slot * 1000f;

        Wall first = new Wall(dx, 0, dx + 500, 0, firstWallThickness(slot), 250);
        Wall second = new Wall(dx + 500, 0, dx + 500, 400, secondWallThickness(slot), 250);
        first.setWallAtEnd(second);
        second.setWallAtStart(first);
        home.addWall(first);
        home.addWall(second);
        first.setLevel(level);
        second.setLevel(level);

        Room room = new Room(new float[][] {{dx, 0}, {dx + 500, 0}, {dx + 500, 400}, {dx, 400}});
        room.setName(roomName(slot));
        home.addRoom(room);
        room.setLevel(level);

        HomePieceOfFurniture table = piece(tableName(slot), dx + 100, 100);
        home.addPieceOfFurniture(table);
        table.setLevel(level);

        HomeDoorOrWindow door = new HomeDoorOrWindow(new CatalogDoorOrWindow("test#door", doorName(slot),
                null, null, null, 90f, 20f, 210f, 0f, false, 1f, 0f, new Sash[0], null, null, true, null, null));
        door.setX(dx + 250);
        door.setY(0);
        home.addPieceOfFurniture(door);
        door.setLevel(level);

        HomeFurnitureGroup group = new HomeFurnitureGroup(Arrays.asList(
                piece(chairName(slot, 'a'), dx + 300, 300),
                piece(chairName(slot, 'b'), dx + 400, 300)), groupName(slot));
        home.addPieceOfFurniture(group);
        group.setLevel(level);

        Label label = new Label(labelText(slot), dx + 50, 50);
        home.addLabel(label);
        label.setLevel(level);

        DimensionLine line = new DimensionLine(dx, -50, dx + 500, -50, dimensionLineOffset(slot));
        home.addDimensionLine(line);
        line.setLevel(level);

        Polyline polyline = new Polyline(new float[][] {{dx, 450}, {dx + 200, 450}, {dx + 200, 500}});
        polyline.setThickness(polylineThickness(slot));
        home.addPolyline(polyline);
        polyline.setLevel(level);
    }

    private static HomePieceOfFurniture piece(String name, float x, float y) {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(name, null, null, 50f, 50f, 50f, true, false));
        piece.setX(x);
        piece.setY(y);
        return piece;
    }

    /** Удаляет из дома всё: уровни (вместе с объектами на них) и объекты без уровня. */
    static void clear(Home home) {
        for (Level level : new ArrayList<>(home.getLevels())) {
            home.deleteLevel(level);
        }
        for (Wall wall : new ArrayList<>(home.getWalls())) {
            home.deleteWall(wall);
        }
        for (Room room : new ArrayList<>(home.getRooms())) {
            home.deleteRoom(room);
        }
        for (HomePieceOfFurniture piece : new ArrayList<>(home.getFurniture())) {
            home.deletePieceOfFurniture(piece);
        }
        for (Label label : new ArrayList<>(home.getLabels())) {
            home.deleteLabel(label);
        }
        for (DimensionLine line : new ArrayList<>(home.getDimensionLines())) {
            home.deleteDimensionLine(line);
        }
        for (Polyline polyline : new ArrayList<>(home.getPolylines())) {
            home.deletePolyline(polyline);
        }
    }

    /**
     * Записывает в {@code onEdt}, в каком потоке модель сообщила о каждом изменении:
     * состава объектов любого вида, состава уровней и выбранного уровня.
     */
    static void recordThreadOfEveryChange(Home home, List<Boolean> onEdt) {
        home.addWallsListener(ev -> onEdt.add(SwingUtilities.isEventDispatchThread()));
        home.addRoomsListener(ev -> onEdt.add(SwingUtilities.isEventDispatchThread()));
        home.addFurnitureListener(ev -> onEdt.add(SwingUtilities.isEventDispatchThread()));
        home.addLabelsListener(ev -> onEdt.add(SwingUtilities.isEventDispatchThread()));
        home.addDimensionLinesListener(ev -> onEdt.add(SwingUtilities.isEventDispatchThread()));
        home.addPolylinesListener(ev -> onEdt.add(SwingUtilities.isEventDispatchThread()));
        home.addLevelsListener(ev -> onEdt.add(SwingUtilities.isEventDispatchThread()));
        home.addPropertyChangeListener(Home.Property.SELECTED_LEVEL,
                ev -> onEdt.add(SwingUtilities.isEventDispatchThread()));
    }

    // ==================== Свойства-ключи наборов ====================

    static float firstWallThickness(int slot) {
        return 11 + slot * 10;
    }

    static float secondWallThickness(int slot) {
        return 12 + slot * 10;
    }

    static String roomName(int slot) {
        return "room-" + slot;
    }

    static String tableName(int slot) {
        return "table-" + slot;
    }

    static String doorName(int slot) {
        return "door-" + slot;
    }

    static String groupName(int slot) {
        return "group-" + slot;
    }

    static String chairName(int slot, char which) {
        return "chair-" + slot + "-" + which;
    }

    static String labelText(int slot) {
        return "label-" + slot;
    }

    static float dimensionLineOffset(int slot) {
        return 21 + slot;
    }

    static float polylineThickness(int slot) {
        return 3 + slot;
    }

    // ==================== Поиск в живой сцене ====================

    static Level level(Home home, String name) {
        for (Level level : home.getLevels()) {
            if (name.equals(level.getName())) {
                return level;
            }
        }
        return fail("level '" + name + "' not found in the scene, levels: " + SceneFacts.of(home).levels);
    }

    static Wall wall(Home home, float thickness) {
        for (Wall wall : home.getWalls()) {
            if (wall.getThickness() == thickness) {
                return wall;
            }
        }
        return fail("wall(thickness=" + thickness + ") not found in the scene");
    }

    static Room room(Home home, String name) {
        for (Room room : home.getRooms()) {
            if (name.equals(room.getName())) {
                return room;
            }
        }
        return fail("room '" + name + "' not found in the scene");
    }

    /** Мебель верхнего уровня (не внутри группы). */
    static HomePieceOfFurniture furniture(Home home, String name) {
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            if (name.equals(piece.getName())) {
                return piece;
            }
        }
        return fail("furniture '" + name + "' not found among top-level furniture of the scene");
    }

    static Label label(Home home, String text) {
        for (Label label : home.getLabels()) {
            if (text.equals(label.getText())) {
                return label;
            }
        }
        return fail("label '" + text + "' not found in the scene");
    }

    static DimensionLine dimensionLine(Home home, float offset) {
        for (DimensionLine line : home.getDimensionLines()) {
            if (line.getOffset() == offset) {
                return line;
            }
        }
        return fail("dimension line(offset=" + offset + ") not found in the scene");
    }

    static Polyline polyline(Home home, float thickness) {
        for (Polyline polyline : home.getPolylines()) {
            if (polyline.getThickness() == thickness) {
                return polyline;
            }
        }
        return fail("polyline(thickness=" + thickness + ") not found in the scene");
    }
}
