package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Слепок сцены по значениям: уровни, выбранный уровень и все объекты с их уровнями,
 * координатами и идентификаторами. Ссылок на объекты модели не хранит, поэтому
 * остаётся верным, что бы ни происходило со сценой после снятия.
 *
 * <p>Объект опознаётся ключом, который не зависит ни от уровня, ни от идентификатора,
 * ни от координат: у стены это толщина, у размерной линии — отступ, у полилинии —
 * толщина линии, у комнаты и мебели — имя, у надписи — текст. Сцены для таких
 * сравнений строит {@link LevelSceneFixture}: ключи в них уникальны.
 *
 * <p>Уровень сравнивается как сущность — имя и отметка, а не как экземпляр.
 */
final class SceneFacts {

    static final String NO_LEVEL = "<no level>";

    /** Что известно об одном объекте сцены. */
    static final class ObjectFact {
        final String category;
        final String id;
        final String level;
        final String coordinates;

        ObjectFact(String category, String id, String level, String coordinates) {
            this.category = category;
            this.id = id;
            this.level = level;
            this.coordinates = coordinates;
        }
    }

    /** Описания уровней в порядке {@code Home.getLevels()}: имя, отметка, высота, перекрытие, viewable. */
    final List<String> levels = new ArrayList<>();
    /** Сущность уровня → идентификатор. */
    final Map<String, String> levelIds = new TreeMap<>();
    /** Сущность выбранного уровня. */
    final String selectedLevel;
    /** Ключ объекта → факты о нём. */
    final Map<String, ObjectFact> objects = new TreeMap<>();
    /** Ключи, встреченные в сцене больше одного раза. */
    final List<String> duplicates = new ArrayList<>();

    private SceneFacts(Home home) {
        for (Level level : home.getLevels()) {
            levels.add(levelKey(level)
                    + " height=" + level.getHeight()
                    + " floorThickness=" + level.getFloorThickness()
                    + " viewable=" + level.isViewable());
            levelIds.put(levelKey(level), level.getId());
        }
        selectedLevel = levelKey(home.getSelectedLevel());

        for (Wall wall : home.getWalls()) {
            put("walls", wallKey(wall), wall.getId(), wall.getLevel(),
                    point(wall.getXStart(), wall.getYStart()) + "->" + point(wall.getXEnd(), wall.getYEnd()));
        }
        for (Room room : home.getRooms()) {
            put("rooms", roomKey(room), room.getId(), room.getLevel(), Arrays.deepToString(room.getPoints()));
        }
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            put("furniture", furnitureKey(piece), piece.getId(), piece.getLevel(), point(piece.getX(), piece.getY()));
            if (piece instanceof HomeFurnitureGroup) {
                putMembers((HomeFurnitureGroup) piece);
            }
        }
        for (Label label : home.getLabels()) {
            put("labels", labelKey(label), label.getId(), label.getLevel(), point(label.getX(), label.getY()));
        }
        for (DimensionLine line : home.getDimensionLines()) {
            put("dimension lines", dimensionLineKey(line), line.getId(), line.getLevel(),
                    point(line.getXStart(), line.getYStart()) + "->" + point(line.getXEnd(), line.getYEnd()));
        }
        for (Polyline polyline : home.getPolylines()) {
            put("polylines", polylineKey(polyline), polyline.getId(), polyline.getLevel(),
                    Arrays.deepToString(polyline.getPoints()));
        }
    }

    static SceneFacts of(Home home) {
        return new SceneFacts(home);
    }

    private void putMembers(HomeFurnitureGroup group) {
        for (HomePieceOfFurniture member : group.getFurniture()) {
            put("group members", memberKey(group, member), member.getId(), member.getLevel(),
                    point(member.getX(), member.getY()));
            if (member instanceof HomeFurnitureGroup) {
                putMembers((HomeFurnitureGroup) member);
            }
        }
    }

    private void put(String category, String key, String id, Level level, String coordinates) {
        if (objects.put(key, new ObjectFact(category, id, levelKey(level), coordinates)) != null) {
            duplicates.add(key);
        }
    }

    // ==================== Ключи ====================

    static String levelKey(Level level) {
        return level == null ? NO_LEVEL : level.getName() + "@" + level.getElevation();
    }

    static String wallKey(Wall wall) {
        return "wall(thickness=" + wall.getThickness() + ")";
    }

    static String roomKey(Room room) {
        return "room '" + room.getName() + "'";
    }

    static String furnitureKey(HomePieceOfFurniture piece) {
        String kind = piece instanceof HomeFurnitureGroup ? "furniture group"
                : piece instanceof HomeDoorOrWindow ? "door/window" : "furniture";
        return kind + " '" + piece.getName() + "'";
    }

    static String memberKey(HomeFurnitureGroup group, HomePieceOfFurniture member) {
        return furnitureKey(member) + " in group '" + group.getName() + "'";
    }

    static String labelKey(Label label) {
        return "label '" + label.getText() + "'";
    }

    static String dimensionLineKey(DimensionLine line) {
        return "dimension line(offset=" + line.getOffset() + ")";
    }

    static String polylineKey(Polyline polyline) {
        return "polyline(thickness=" + polyline.getThickness() + ")";
    }

    private static String point(float x, float y) {
        return "(" + x + "," + y + ")";
    }

    // ==================== Сравнения: this — ожидание, actual — сцена после команды ====================

    /** Уровни: число, порядок, имя, отметка, высота, толщина перекрытия, viewable. */
    List<String> levelProblems(SceneFacts actual) {
        List<String> problems = new ArrayList<>();
        if (!levels.equals(actual.levels)) {
            problems.add("levels: expected " + levels + " but was " + actual.levels);
        }
        return problems;
    }

    /** Выбранный уровень. */
    List<String> selectedLevelProblems(SceneFacts actual) {
        List<String> problems = new ArrayList<>();
        if (!selectedLevel.equals(actual.selectedLevel)) {
            problems.add("selected level: expected " + selectedLevel + " but was " + actual.selectedLevel);
        }
        return problems;
    }

    /** Каждый объект на своём уровне, и число объектов каждого вида на каждом уровне то же. */
    List<String> objectLevelProblems(SceneFacts actual) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, ObjectFact> entry : objects.entrySet()) {
            ObjectFact found = actual.objects.get(entry.getKey());
            String expectedLevel = entry.getValue().level;
            if (found == null) {
                problems.add(entry.getKey() + " (expected on level " + expectedLevel + ") is missing from the scene");
            } else if (!expectedLevel.equals(found.level)) {
                problems.add(entry.getKey() + " expected on level " + expectedLevel + " but is on " + found.level);
            }
        }
        for (Map.Entry<String, ObjectFact> entry : actual.objects.entrySet()) {
            if (!objects.containsKey(entry.getKey())) {
                problems.add(entry.getKey() + " on level " + entry.getValue().level
                        + " is not part of the expected scene");
            }
        }
        for (String key : actual.duplicates) {
            problems.add(key + " is present in the scene more than once");
        }

        Map<String, Integer> expectedCounts = countsPerLevel();
        Map<String, Integer> actualCounts = actual.countsPerLevel();
        Map<String, Integer> allCounts = new TreeMap<>(expectedCounts);
        actualCounts.forEach((where, count) -> allCounts.putIfAbsent(where, 0));
        List<String> differences = new ArrayList<>();
        for (String where : allCounts.keySet()) {
            int expectedCount = expectedCounts.getOrDefault(where, 0);
            int actualCount = actualCounts.getOrDefault(where, 0);
            if (expectedCount != actualCount) {
                differences.add(where + ": " + expectedCount + " -> " + actualCount);
            }
        }
        if (!differences.isEmpty()) {
            problems.add("number of objects per level (expected -> found): " + String.join("; ", differences));
        }
        return problems;
    }

    /** Число объектов по видам и уровням, например «walls on Ground@0.0» → 2. */
    private Map<String, Integer> countsPerLevel() {
        Map<String, Integer> counts = new TreeMap<>();
        for (ObjectFact fact : objects.values()) {
            counts.merge(fact.category + " on " + fact.level, 1, Integer::sum);
        }
        for (String key : duplicates) {
            ObjectFact fact = objects.get(key);
            counts.merge(fact.category + " on " + fact.level, 1, Integer::sum);
        }
        return counts;
    }

    /** Координаты объектов. Пропавшие объекты сюда не входят — о них сообщает {@link #objectLevelProblems}. */
    List<String> coordinateProblems(SceneFacts actual) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, ObjectFact> entry : objects.entrySet()) {
            ObjectFact found = actual.objects.get(entry.getKey());
            if (found != null && !entry.getValue().coordinates.equals(found.coordinates)) {
                problems.add(entry.getKey() + " expected at " + entry.getValue().coordinates
                        + " but is at " + found.coordinates);
            }
        }
        return problems;
    }

    /** Идентификаторы объектов. */
    List<String> objectIdProblems(SceneFacts actual) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, ObjectFact> entry : objects.entrySet()) {
            ObjectFact found = actual.objects.get(entry.getKey());
            if (found == null) {
                problems.add(entry.getKey() + " is missing from the scene, its id " + entry.getValue().id
                        + " no longer resolves");
            } else if (!entry.getValue().id.equals(found.id)) {
                problems.add(entry.getKey() + " expected to keep id " + entry.getValue().id
                        + " but has id " + found.id);
            }
        }
        return problems;
    }

    /** Идентификаторы уровней. */
    List<String> levelIdProblems(SceneFacts actual) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : levelIds.entrySet()) {
            String found = actual.levelIds.get(entry.getKey());
            if (found == null) {
                problems.add("level " + entry.getKey() + " is missing from the scene, its id "
                        + entry.getValue() + " no longer resolves");
            } else if (!entry.getValue().equals(found)) {
                problems.add("level " + entry.getKey() + " expected to keep id " + entry.getValue()
                        + " but has id " + found);
            }
        }
        return problems;
    }

    /** Уровни, выбранный уровень и уровни объектов разом; каждая строка помечена тем, что именно нарушено. */
    List<String> sceneProblems(SceneFacts actual) {
        List<String> problems = new ArrayList<>();
        levelProblems(actual).forEach(p -> problems.add("[levels] " + p));
        selectedLevelProblems(actual).forEach(p -> problems.add("[selected level] " + p));
        objectLevelProblems(actual).forEach(p -> problems.add("[object levels] " + p));
        return problems;
    }

    /** То же, что {@link #sceneProblems}, плюс координаты объектов. */
    List<String> sceneAndCoordinateProblems(SceneFacts actual) {
        List<String> problems = sceneProblems(actual);
        coordinateProblems(actual).forEach(p -> problems.add("[coordinates] " + p));
        return problems;
    }

    // ==================== Проверки по живой сцене ====================

    /**
     * Уровень объекта и выбранный уровень должны быть экземплярами из {@code home.getLevels()}:
     * Sweet Home 3D решает, на каком уровне показать объект, сравнением ссылок, так что уровень
     * с тем же именем и отметкой, но посторонний для дома, оставляет объект невидимым.
     */
    static List<String> foreignLevelProblems(Home home) {
        List<String> problems = new ArrayList<>();
        checkOwnLevel(problems, home, "selected level", home.getSelectedLevel());
        for (Wall wall : home.getWalls()) {
            checkOwnLevel(problems, home, wallKey(wall), wall.getLevel());
        }
        for (Room room : home.getRooms()) {
            checkOwnLevel(problems, home, roomKey(room), room.getLevel());
        }
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            checkOwnLevel(problems, home, furnitureKey(piece), piece.getLevel());
            if (piece instanceof HomeFurnitureGroup) {
                HomeFurnitureGroup group = (HomeFurnitureGroup) piece;
                for (HomePieceOfFurniture member : group.getAllFurniture()) {
                    checkOwnLevel(problems, home, memberKey(group, member), member.getLevel());
                }
            }
        }
        for (Label label : home.getLabels()) {
            checkOwnLevel(problems, home, labelKey(label), label.getLevel());
        }
        for (DimensionLine line : home.getDimensionLines()) {
            checkOwnLevel(problems, home, dimensionLineKey(line), line.getLevel());
        }
        for (Polyline polyline : home.getPolylines()) {
            checkOwnLevel(problems, home, polylineKey(polyline), polyline.getLevel());
        }
        return problems;
    }

    private static void checkOwnLevel(List<String> problems, Home home, String what, Level level) {
        if (level == null) {
            return;
        }
        for (Level own : home.getLevels()) {
            if (own == level) {
                return;
            }
        }
        problems.add(what + " refers to level " + levelKey(level)
                + " that is not one of the level instances of the home");
    }

    /** Падает, перечисляя все расхождения, а не только первое. */
    static void assertNoProblems(String what, List<String> problems) {
        assertTrue(problems.isEmpty(),
                () -> what + " — " + problems.size() + " problem(s):\n  - " + String.join("\n  - ", problems));
    }
}
