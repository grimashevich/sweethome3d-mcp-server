package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Размерные линии: {@code add_dimension_line}, {@code modify_dimension_line},
 * {@code delete_dimension_line} (CHANGELOG, раздел Added) доходят до модели.
 */
class DimensionLinesLiveIT extends LiveTestBase {

    @Test
    void addedLineIsStoredWithGivenGeometry() {
        Map<String, Object> added = mcp.call("add_dimension_line",
                "xStart", 100, "yStart", 50, "xEnd", 400, "yEnd", 450, "offset", -40);
        String id = str(added, "id");
        // 300 по X и 400 по Y — длина 500
        assertEquals(500.0, num(added, "length"), RESPONSE_EPS, "length in the response");
        assertEquals(-40.0, num(added, "offset"), RESPONSE_EPS, "offset in the response");

        Map<String, Object> inState = byId(objects(state(), "dimensionLines"), id);
        assertEquals(100.0, num(inState, "xStart"), RESPONSE_EPS, "get_state xStart");
        assertEquals(50.0, num(inState, "yStart"), RESPONSE_EPS, "get_state yStart");
        assertEquals(400.0, num(inState, "xEnd"), RESPONSE_EPS, "get_state xEnd");
        assertEquals(450.0, num(inState, "yEnd"), RESPONSE_EPS, "get_state yEnd");
        assertEquals(-40.0, num(inState, "offset"), RESPONSE_EPS, "get_state offset");
        assertEquals(500.0, num(inState, "length"), RESPONSE_EPS, "get_state length");

        Home home = saveAndReadHome("dimension-added");
        assertEquals(1, home.getDimensionLines().size(), "dimension lines in the saved home");
        DimensionLine line = lineById(home, id);
        assertLine(line, 100, 50, 400, 450, -40);
        assertEquals(500.0, line.getLength(), MODEL_EPS, "length in the saved home");
    }

    @Test
    void offsetDefaultsTo25() {
        Map<String, Object> added = mcp.call("add_dimension_line",
                "xStart", 0, "yStart", 0, "xEnd", 200, "yEnd", 0);

        assertEquals(25.0, num(added, "offset"), RESPONSE_EPS, "default offset in the response");
        Home home = saveAndReadHome("dimension-default-offset");
        assertEquals(25.0, lineById(home, str(added, "id")).getOffset(), MODEL_EPS, "default offset in the saved home");
    }

    /**
     * Описание команды: положительное смещение — по правую руку при движении от начала к концу,
     * то есть ниже линии «слева направо» и левее линии «сверху вниз» (ось Y плана направлена вниз).
     * Проверяется по тому, где линию рисует сам Sweet Home 3D.
     */
    @Test
    void positiveOffsetIsOnTheRightHandSide() {
        String leftToRight = str(mcp.call("add_dimension_line",
                "xStart", 0, "yStart", 0, "xEnd", 600, "yEnd", 0, "offset", 40), "id");
        String topToBottom = str(mcp.call("add_dimension_line",
                "xStart", 0, "yStart", 0, "xEnd", 0, "yEnd", 400, "offset", 40), "id");

        Home home = saveAndReadHome("dimension-offset-side");
        // getPoints(): [1] и [2] — концы самой размерной линии, уже со смещением
        float[][] horizontal = lineById(home, leftToRight).getPoints();
        assertEquals(40.0, horizontal[1][1], MODEL_EPS, "left-to-right line, offset +40: drawn 40 cm below (y)");
        assertEquals(0.0, horizontal[1][0], MODEL_EPS, "left-to-right line: no shift along x");
        float[][] vertical = lineById(home, topToBottom).getPoints();
        assertEquals(-40.0, vertical[1][0], MODEL_EPS, "top-to-bottom line, offset +40: drawn 40 cm to the left (x)");
        assertEquals(0.0, vertical[1][1], MODEL_EPS, "top-to-bottom line: no shift along y");
    }

    @Test
    void modifyChangesOnlyGivenPropertiesAndRecalculatesLength() {
        String id = str(mcp.call("add_dimension_line",
                "xStart", 0, "yStart", 0, "xEnd", 300, "yEnd", 0, "offset", 30), "id");

        // Только смещение: измеряемый отрезок не меняется
        mcp.call("modify_dimension_line", "id", id, "offset", -60);
        Home afterOffset = saveAndReadHome("dimension-modified-offset");
        assertLine(lineById(afterOffset, id), 0, 0, 300, 0, -60);
        assertEquals(300.0, lineById(afterOffset, id).getLength(), MODEL_EPS, "length after an offset-only change");

        // Только конец: смещение остаётся, длина пересчитана (300 по X, 400 по Y — 500)
        Map<String, Object> moved = mcp.call("modify_dimension_line", "id", id, "xEnd", 300, "yEnd", 400);
        if (moved.containsKey("length")) {
            // Состав ответа описанием не задан; но если длина в нём есть — она обязана быть новой
            assertEquals(500.0, num(moved, "length"), RESPONSE_EPS, "recalculated length in the response");
        }
        Home afterMove = saveAndReadHome("dimension-modified-end");
        assertLine(lineById(afterMove, id), 0, 0, 300, 400, -60);
        assertEquals(500.0, lineById(afterMove, id).getLength(), MODEL_EPS, "recalculated length in the saved home");

        Map<String, Object> inState = byId(objects(state(), "dimensionLines"), id);
        assertEquals(500.0, num(inState, "length"), RESPONSE_EPS, "recalculated length in get_state");
        assertEquals(1, afterMove.getDimensionLines().size(), "modify must not add lines");
    }

    @Test
    void deleteRemovesOnlyThatLine() {
        String first = str(mcp.call("add_dimension_line",
                "xStart", 0, "yStart", 0, "xEnd", 300, "yEnd", 0), "id");
        String second = str(mcp.call("add_dimension_line",
                "xStart", 0, "yStart", 100, "xEnd", 300, "yEnd", 100), "id");

        mcp.call("delete_dimension_line", "id", first);

        List<Map<String, Object>> lines = objects(state(), "dimensionLines");
        assertEquals(1, lines.size(), "dimension lines left in get_state: " + lines);
        assertEquals(second, lines.get(0).get("id"), "the other line must stay");

        Home home = saveAndReadHome("dimension-deleted");
        assertEquals(1, home.getDimensionLines().size(), "dimension lines left in the saved home");
        assertLine(lineById(home, second), 0, 100, 300, 100, 25);

        // Повторное удаление того же id — ошибка, а не тихий успех
        ToolResult again = mcp.callTool("delete_dimension_line", McpLiveClient.args("id", first));
        assertTrue(again.isError(), "deleting an already deleted line must fail: " + again);
    }

    @Test
    void responseLevelMatchesGetStateAndModel() {
        mcp.call("add_level", "name", "E2E storey", "elevation", 0);

        Map<String, Object> added = mcp.call("add_dimension_line",
                "xStart", 0, "yStart", 0, "xEnd", 300, "yEnd", 0);

        assertTrue(added.containsKey("level"), "add_dimension_line must report 'level': " + added);
        Map<String, Object> inState = byId(objects(state(), "dimensionLines"), str(added, "id"));
        assertEquals(inState.get("level"), added.get("level"),
                "add_dimension_line must report the same level as get_state");

        Home home = saveAndReadHome("dimension-level");
        assertEquals("E2E storey", levelName(lineById(home, str(added, "id")).getLevel()),
                "a line added while a level is selected belongs to that level");
    }

    @Test
    void nonFiniteAndOverflowingValuesAreRejectedBeforeAnythingChanges() {
        // 1e40 не помещается в float и стал бы бесконечностью
        ToolResult infinite = mcp.callTool("add_dimension_line",
                McpLiveClient.args("xStart", 0, "yStart", 0, "xEnd", 1e40, "yEnd", 0));
        assertTrue(infinite.isError(), "a coordinate that overflows to infinity must be rejected: " + infinite);

        // Каждая координата конечна, но длина отрезка уже не помещается в float
        ToolResult tooLong = mcp.callTool("add_dimension_line",
                McpLiveClient.args("xStart", -3e38, "yStart", 0, "xEnd", 3e38, "yEnd", 0));
        assertTrue(tooLong.isError(), "a line whose length overflows must be rejected: " + tooLong);

        ToolResult badOffset = mcp.callTool("add_dimension_line",
                McpLiveClient.args("xStart", 0, "yStart", 0, "xEnd", 100, "yEnd", 0, "offset", 1e40));
        assertTrue(badOffset.isError(), "an offset that overflows to infinity must be rejected: " + badOffset);

        assertTrue(objects(state(), "dimensionLines").isEmpty(), "rejected lines must not be stored");

        // То же для изменения: отказ до любых правок
        String id = str(mcp.call("add_dimension_line",
                "xStart", 0, "yStart", 0, "xEnd", 300, "yEnd", 0, "offset", 30), "id");
        ToolResult badModify = mcp.callTool("modify_dimension_line",
                McpLiveClient.args("id", id, "xStart", 50, "xEnd", 1e40));
        assertTrue(badModify.isError(), "modify with a non-finite coordinate must be rejected: " + badModify);

        Home home = saveAndReadHome("dimension-rejected");
        assertEquals(1, home.getDimensionLines().size(), "only the valid line is stored");
        assertLine(lineById(home, id), 0, 0, 300, 0, 30);
    }

    @Test
    void unknownIdIsAnError() {
        ToolResult modify = mcp.callTool("modify_dimension_line",
                McpLiveClient.args("id", "dimensionLine-no-such-id", "offset", 10));
        assertTrue(modify.isError(), "modify of an unknown id must fail: " + modify);

        ToolResult delete = mcp.callTool("delete_dimension_line",
                McpLiveClient.args("id", "dimensionLine-no-such-id"));
        assertTrue(delete.isError(), "delete of an unknown id must fail: " + delete);
    }

    private static DimensionLine lineById(Home home, String id) {
        for (DimensionLine line : home.getDimensionLines()) {
            if (id.equals(line.getId())) {
                return line;
            }
        }
        throw new AssertionError("The saved home has no dimension line with id " + id);
    }

    private static void assertLine(DimensionLine line, double xStart, double yStart, double xEnd, double yEnd,
                                   double offset) {
        assertEquals(xStart, line.getXStart(), MODEL_EPS, "xStart in the saved home");
        assertEquals(yStart, line.getYStart(), MODEL_EPS, "yStart in the saved home");
        assertEquals(xEnd, line.getXEnd(), MODEL_EPS, "xEnd in the saved home");
        assertEquals(yEnd, line.getYEnd(), MODEL_EPS, "yEnd in the saved home");
        assertEquals(offset, line.getOffset(), MODEL_EPS, "offset in the saved home");
    }
}
