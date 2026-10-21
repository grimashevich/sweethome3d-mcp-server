package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.addLevel;
import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static com.sh3d.mcp.command.handler.TestFixtures.makeRequest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #9: {@code list_levels} отдаёт стабильный {@code id} модели.
 * <ul>
 *   <li>{@code id} каждого элемента {@code levels} — стабильная строка {@code Level.getId()}, та же, что
 *       в {@code get_state} и {@code add_level};</li>
 *   <li>порядковый номер — отдельное числовое поле {@code index} (0, 1, … в порядке
 *       {@code home.getLevels()}); прочие поля — без изменений;</li>
 *   <li>{@code id} из {@code list_levels} принимают {@code set_selected_level} и {@code delete_level};</li>
 *   <li>описание {@code list_levels} перестаёт говорить «id (index)».</li>
 * </ul>
 */
class ListLevelsStableIdTest {

    /** Поля элемента до I9 — в прежнем порядке. */
    private static final List<String> OLD_ITEM_KEYS = Arrays.asList(
            "id", "name", "elevation", "height", "floorThickness", "viewable", "selected");

    private Home home;
    private HomeAccessor accessor;
    private Level ground;
    private Level first;
    private Level attic;

    @BeforeEach
    void setUp() {
        home = new Home();
        accessor = createAccessor(home);
        ground = addLevel(home, "Ground", 0, 250, 12);
        first = addLevel(home, "First", 262, 240, 14);
        attic = addLevel(home, "Attic", 516, 200, 10);
        home.setSelectedLevel(first);
    }

    @Test
    void idOfEveryLevelIsTheStableLevelId() {
        List<Map<String, Object>> levels = listLevels();

        assertEquals(3, levels.size());
        Level[] expected = {ground, first, attic};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].getId(), levels.get(i).get("id"),
                    "I9: list_levels must report Level.getId() as 'id', level " + i);
        }
    }

    @Test
    void idMatchesTheIdReportedByGetState() {
        List<Map<String, Object>> listed = listLevels();
        Response state = new GetStateHandler().execute(makeRequest("get_state"), accessor);
        assertTrue(state.isOk(), state.getMessage());
        List<Map<String, Object>> fromState = maps(state.getData().get("levels"));

        assertEquals(fromState.size(), listed.size());
        for (int i = 0; i < listed.size(); i++) {
            assertEquals(fromState.get(i).get("id"), listed.get(i).get("id"),
                    "I9: list_levels and get_state must report the same id for level " + i);
        }
    }

    @Test
    void idMatchesTheIdReportedByAddLevel() {
        Response added = new AddLevelHandler().execute(
                makeRequest("add_level", "name", "Roof", "elevation", 716.0), accessor);
        assertTrue(added.isOk(), added.getMessage());

        List<Map<String, Object>> listed = listLevels();
        Map<String, Object> roof = byName(listed, "Roof");

        assertEquals(added.getData().get("id"), roof.get("id"),
                "I9: the id reported by add_level must be the id list_levels reports for the same level");
    }

    @Test
    void indexIsThePositionInTheLevelList() {
        List<Map<String, Object>> levels = listLevels();

        for (int i = 0; i < levels.size(); i++) {
            Object index = levels.get(i).get("index");
            assertTrue(index instanceof Number, "I9: 'index' must be a JSON number, got " + index);
            assertEquals(i, ((Number) index).intValue(), "I9: 'index' is the position in home.getLevels()");
            assertEquals(((Number) index).doubleValue(), Math.rint(((Number) index).doubleValue()),
                    "I9: 'index' is a whole number");
        }
    }

    @Test
    void indexFollowsHomeLevelsOrderForLevelsAtTheSameElevation() {
        // Уровни с одинаковой отметкой: порядок в home.getLevels() задаёт elevationIndex, а не имя.
        Level mezzanine = addLevel(home, "Mezzanine", 262, 100, 5);

        List<Map<String, Object>> levels = listLevels();
        List<Level> order = home.getLevels();

        assertEquals(order.size(), levels.size());
        for (int i = 0; i < order.size(); i++) {
            assertEquals(order.get(i).getId(), levels.get(i).get("id"), "level " + i);
            assertEquals(i, ((Number) levels.get(i).get("index")).intValue(), "level " + i);
        }
        assertTrue(order.contains(mezzanine));
    }

    @Test
    void itemHasTheOldFieldsPlusIndexOnly() {
        for (Map<String, Object> item : listLevels()) {
            List<String> keys = new ArrayList<>(item.keySet());
            List<String> expected = new ArrayList<>(OLD_ITEM_KEYS);
            expected.add("index");
            // Уточнение: позиция уровня передаётся числовым полем index; его место среди полей не задано.
            // Проверяется состав полей и прежний порядок остальных полей.
            assertEquals(expected.size(), keys.size(), "I9: fields of a list_levels item: " + keys);
            assertTrue(keys.containsAll(expected), "I9: fields of a list_levels item: " + keys);
            List<String> withoutIndex = new ArrayList<>(keys);
            withoutIndex.remove("index");
            assertEquals(OLD_ITEM_KEYS, withoutIndex, "I9: the other fields keep their order");
        }
    }

    @Test
    void otherFieldsAreUnchanged() {
        Map<String, Object> item = listLevels().get(1);

        assertEquals("First", item.get("name"));
        assertEquals(262.0, ((Number) item.get("elevation")).doubleValue(), 0.0);
        assertEquals(240.0, ((Number) item.get("height")).doubleValue(), 0.0);
        assertEquals(14.0, ((Number) item.get("floorThickness")).doubleValue(), 0.0);
        assertEquals(Boolean.TRUE, item.get("viewable"));
        assertEquals(Boolean.TRUE, item.get("selected"));
        assertEquals(Boolean.FALSE, listLevels().get(0).get("selected"));
    }

    @Test
    void levelCountIsUnchanged() {
        Response response = new ListLevelsHandler().execute(makeRequest("list_levels"), accessor);

        assertEquals(Arrays.asList("levelCount", "levels"), new ArrayList<>(response.getData().keySet()));
        assertEquals(3, ((Number) response.getData().get("levelCount")).intValue());
    }

    @Test
    void setSelectedLevelAcceptsTheIdFromListLevels() {
        Object atticId = byName(listLevels(), "Attic").get("id");

        Response response = new SetSelectedLevelHandler().execute(
                makeRequest("set_selected_level", "id", atticId), accessor);

        assertTrue(response.isOk(), "I9: set_selected_level must accept the id from list_levels, got: "
                + response.getMessage());
        assertSame(attic, home.getSelectedLevel());
    }

    @Test
    void deleteLevelAcceptsTheIdFromListLevels() {
        Object groundId = byName(listLevels(), "Ground").get("id");

        Response response = new DeleteLevelHandler().execute(
                makeRequest("delete_level", "id", groundId), accessor);

        assertTrue(response.isOk(), "I9: delete_level must accept the id from list_levels, got: "
                + response.getMessage());
        assertFalse(home.getLevels().contains(ground), "the level named by the id must be the one deleted");
        assertEquals(Arrays.asList(first, attic), home.getLevels());
    }

    @Test
    void descriptionNoLongerCallsTheIdAnIndex() {
        String description = new ListLevelsHandler().getDescription();

        assertFalse(description.toLowerCase(Locale.ROOT).replace(" ", "").contains("id(index)"),
                "I9: the list_levels description must stop saying 'id (index)': " + description);
    }

    // ==================== Helpers ====================

    private List<Map<String, Object>> listLevels() {
        Response response = new ListLevelsHandler().execute(makeRequest("list_levels"), accessor);
        assertTrue(response.isOk(), response.getMessage());
        return maps(response.getData().get("levels"));
    }

    private static Map<String, Object> byName(List<Map<String, Object>> levels, String name) {
        for (Map<String, Object> level : levels) {
            if (name.equals(level.get("name"))) {
                return level;
            }
        }
        return fail("no level '" + name + "' in " + levels);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object list) {
        return (List<Map<String, Object>>) list;
    }
}
