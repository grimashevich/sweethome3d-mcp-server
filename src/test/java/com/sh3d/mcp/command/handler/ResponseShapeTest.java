package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Состав и порядок полей в ответах команд, чьи ответы собираются общими сборщиками FormatUtil.
 * FormatUtilTest закрепляет сам сборщик; здесь — то, что получает клиент: сборщик плюс поля,
 * которые обработчик дописывает поверх. Эталон — ответы этих команд до выноса сборщиков
 * (версия 1.1.0), а не код сборщиков.
 */
class ResponseShapeTest {

    private static final List<String> LEVEL_ITEM_KEYS = Arrays.asList(
            "id", "name", "elevation", "height", "floorThickness", "viewable", "selected");
    private static final List<String> ENVIRONMENT_KEYS = Arrays.asList(
            "groundColor", "groundTexture", "skyColor", "skyTexture", "lightColor",
            "ceilingLightColor", "wallsAlpha", "drawingMode", "allLevelsVisible");
    private static final List<String> LABEL_KEYS = Arrays.asList(
            "id", "text", "x", "y", "angle", "color", "outlineColor", "elevation", "pitch");

    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        home = new Home();
        accessor = createAccessor(home);
    }

    private Map<String, Object> run(CommandHandler handler, String action, Object... keyValues) {
        Response resp = handler.execute(makeRequest(action, keyValues), accessor);
        assertTrue(resp.isOk(), action + ": " + resp.getMessage());
        return resp.getData();
    }

    private static List<String> keys(Object map) {
        return new ArrayList<>(asMap(map).keySet());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object map) {
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object list) {
        return (List<Object>) list;
    }

    // ---- Levels ----

    @Test
    void addLevelResponse() {
        Map<String, Object> data = run(new AddLevelHandler(), "add_level", "name", "Ground", "elevation", 0.0);

        assertEquals(Arrays.asList("id", "name", "elevation", "height", "floorThickness", "levelCount"),
                keys(data));
        assertEquals(home.getLevels().get(0).getId(), data.get("id"), "add_level reports the stable id");
    }

    @Test
    void listLevelsResponse() {
        addLevel(home, "Ground", 0, 250, 12);
        addLevel(home, "First", 262, 240, 12);

        Map<String, Object> data = run(new ListLevelsHandler(), "list_levels");

        assertEquals(Arrays.asList("levelCount", "levels"), keys(data));
        List<Object> levels = asList(data.get("levels"));
        assertEquals(LEVEL_ITEM_KEYS, keys(levels.get(0)));
        assertEquals(LEVEL_ITEM_KEYS, keys(levels.get(1)));
        // list_levels has always reported the position, as a JSON number
        assertEquals(0, asMap(levels.get(0)).get("id"));
        assertEquals(1, asMap(levels.get(1)).get("id"));
    }

    @Test
    void setSelectedLevelResponse() {
        Level ground = addLevel(home, "Ground", 0, 250, 12);

        Map<String, Object> data = run(new SetSelectedLevelHandler(), "set_selected_level", "id", ground.getId());

        assertEquals(Arrays.asList("id", "name", "elevation", "height", "floorThickness", "viewable"), keys(data));
        assertEquals(ground.getId(), data.get("id"));
    }

    // ---- Labels and environment ----

    @Test
    void addLabelWithoutStyle() {
        Map<String, Object> data = run(new AddLabelHandler(), "add_label", "text", "plain", "x", 100.0, "y", 300.0);

        assertEquals(LABEL_KEYS, keys(data));
    }

    @Test
    void addLabelWithStyle() {
        Map<String, Object> data = run(new AddLabelHandler(), "add_label",
                "text", "Kitchen", "x", 300.0, "y", 200.0, "angle", 30.0, "fontSize", 24.0, "bold", true);

        List<String> expected = new ArrayList<>(LABEL_KEYS);
        expected.add("style");
        assertEquals(expected, keys(data));
        assertEquals(Arrays.asList("fontSize", "bold", "italic", "alignment"), keys(data.get("style")));
    }

    @Test
    void setEnvironmentResponse() {
        Map<String, Object> data = run(new SetEnvironmentHandler(), "set_environment",
                "groundColor", "#A0B0C0", "wallsAlpha", 0.25);

        assertEquals(ENVIRONMENT_KEYS, keys(data));
    }

    // ---- get_state ----

    @Test
    void getStateSections() {
        Map<String, Object> data = run(new GetStateHandler(), "get_state");

        assertEquals(Arrays.asList("wallCount", "walls", "furnitureCount", "furniture", "roomCount", "rooms",
                        "labelCount", "labels", "dimensionLineCount", "dimensionLines", "camera",
                        "storedCameraCount", "storedCameras", "levelCount", "levels", "environment",
                        "boundingBox"),
                keys(data));
        assertEquals(ENVIRONMENT_KEYS, keys(data.get("environment")));
    }

    /**
     * Three levels with different viewable/selected flags; every object sits on Ground while
     * First is selected, so a level taken from the selection instead of the object shows up.
     */
    @Test
    void getStateWithLevelsAndObjects() {
        Level ground = addLevel(home, "Ground", 0, 250, 12);
        Level first = addLevel(home, "First", 262, 240, 15);
        Level attic = addLevel(home, "Attic", 517, 200, 12);
        attic.setViewable(false);

        Wall wall = addWall(home, 0, 0, 600, 0);
        wall.setLevel(ground);
        Label label = new Label("Kitchen", 300, 200);
        home.addLabel(label);
        label.setLevel(ground);
        DimensionLine dim = addDimensionLine(home, 0, 0, 600, 0, 40);
        dim.setLevel(ground);
        HomePieceOfFurniture piece = addFurniture(home, "Table", 100, 100);
        piece.setLevel(ground);
        home.setSelectedLevel(first);

        Map<String, Object> data = run(new GetStateHandler(), "get_state");

        List<Object> levels = asList(data.get("levels"));
        assertEquals(3, levels.size());
        Level[] expected = {ground, first, attic};
        for (int i = 0; i < 3; i++) {
            Map<String, Object> item = asMap(levels.get(i));
            assertEquals(LEVEL_ITEM_KEYS, keys(item), "level " + i);
            assertEquals(expected[i].getId(), item.get("id"), "get_state reports the stable id, not the position");
            assertEquals(expected[i].getName(), item.get("name"));
        }
        assertEquals(Arrays.asList(true, true, false),
                Arrays.asList(asMap(levels.get(0)).get("viewable"), asMap(levels.get(1)).get("viewable"),
                        asMap(levels.get(2)).get("viewable")));
        assertEquals(Arrays.asList(false, true, false),
                Arrays.asList(asMap(levels.get(0)).get("selected"), asMap(levels.get(1)).get("selected"),
                        asMap(levels.get(2)).get("selected")));
        assertEquals(15.0, ((Number) asMap(levels.get(1)).get("floorThickness")).doubleValue(), 0.0);

        Map<String, Object> labelItem = asMap(asList(data.get("labels")).get(0));
        assertEquals(Arrays.asList("id", "text", "x", "y", "angle", "color", "level"), keys(labelItem));
        assertEquals(label.getId(), labelItem.get("id"));
        assertEquals("Ground", labelItem.get("level"));

        Map<String, Object> dimItem = asMap(asList(data.get("dimensionLines")).get(0));
        assertEquals(Arrays.asList("id", "xStart", "yStart", "xEnd", "yEnd", "offset", "length", "level"),
                keys(dimItem));
        assertEquals(dim.getId(), dimItem.get("id"));
        assertEquals("Ground", dimItem.get("level"));

        assertEquals("Ground", asMap(asList(data.get("walls")).get(0)).get("level"));
        assertEquals("Ground", asMap(asList(data.get("furniture")).get(0)).get("level"));
    }
}
