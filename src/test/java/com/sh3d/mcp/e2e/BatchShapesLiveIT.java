package com.sh3d.mcp.e2e;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Пакет последовательных операций и пользовательские модели из примитивов, контура и сетки. */
class BatchShapesLiveIT extends LiveEditingTestBase {

    @Test
    void batchCommandsRunsSequentiallyAndContinuesAfterAnIndividualFailure() {
        Map<String, Object> reply = mcp.call("batch_commands", "commands", List.of(
                command("create_wall", McpLiveClient.args("xStart", 0, "yStart", 0, "xEnd", 300, "yEnd", 0)),
                command("get_state", Map.of()),
                command("delete_wall", McpLiveClient.args("id", "wall-no-such-id")),
                command("create_room_polygon", McpLiveClient.args("name", "E2E batched room", "points", List.of(
                        McpLiveClient.args("x", 0, "y", 0), McpLiveClient.args("x", 300, "y", 0),
                        McpLiveClient.args("x", 300, "y", 200)))),
                command("add_label", McpLiveClient.args("text", "E2E last command", "x", 50, "y", 60))));

        List<Map<String, Object>> results = objects(reply, "results");
        assertEquals(5, results.size(), "one individual result per command");
        for (int i = 0; i < results.size(); i++) {
            assertEquals(i, num(results.get(i), "index"), 0.0, "results retain input order");
            assertEquals(i == 2 ? "error" : "ok", results.get(i).get("status"), "result " + i);
        }
        // Уточнение: данные успешного результата принадлежат соответствующей команде пакета.
        Object stateResult = results.get(1).get("data");
        assertTrue(stateResult instanceof Map, "the get_state result must expose its data: " + results.get(1));
        @SuppressWarnings("unchecked")
        Map<String, Object> middleState = (Map<String, Object>) stateResult;
        assertEquals(1, num(middleState, "wallCount"), 0.0, "get_state sees the preceding create_wall");
        assertEquals(0, num(middleState, "roomCount"), 0.0, "get_state does not see later commands");

        Home home = saveAndReadHome("batched");
        assertEquals(1, home.getWalls().size());
        assertEquals(300, home.getWalls().iterator().next().getXEnd(), MODEL_EPS);
        assertEquals(1, home.getRooms().size(), "a later command runs after an error");
        assertEquals("E2E batched room", home.getRooms().get(0).getName());
        assertEquals(1, home.getLabels().size());
        assertEquals("E2E last command", home.getLabels().iterator().next().getText());
    }

    @Test
    void batchCommandsRejectsMoreThanFiftyOperationsBeforeExecutingAny() {
        createRoom(0, 0, "E2E kept");
        List<Map<String, Object>> commands = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            commands.add(command("create_wall",
                    McpLiveClient.args("xStart", 0, "yStart", i, "xEnd", 300, "yEnd", i)));
        }
        assertRejectedWithoutChange("batch_commands", McpLiveClient.args("commands", commands));
    }

    @Test
    void generateBoxPersistsDimensionsPositionRotationAndColor() throws Exception {
        String id = str(mcp.call("generate_shape", "mode", "box", "name", "E2E tabletop", "width", 160,
                "depth", 80, "height", 5, "x", 200, "y", 150, "elevation", 75, "angle", 30,
                "color", 0x336699), "id");
        Home home = saveAndReadHome("box");
        assertEquals(1, home.getFurniture().size());
        HomePieceOfFurniture box = pieceById(home, id);
        assertEquals("E2E tabletop", box.getName());
        assertEquals(160, box.getWidth(), MODEL_EPS);
        assertEquals(80, box.getDepth(), MODEL_EPS);
        assertEquals(5, box.getHeight(), MODEL_EPS);
        assertEquals(200, box.getX(), MODEL_EPS);
        assertEquals(150, box.getY(), MODEL_EPS);
        assertEquals(75, box.getElevation(), MODEL_EPS);
        assertEquals(Math.toRadians(30), box.getAngle(), MODEL_EPS);
        assertEquals(Integer.valueOf(0x336699), box.getColor());
        assertModelContent(box);
    }

    @Test
    void generateExtrudedPolygonPersistsItsDimensionsAndModel() throws Exception {
        String id = str(mcp.call("generate_shape", "mode", "extrude", "name", "E2E counter", "height", 90,
                "polygon", List.of(List.of(0, 0), List.of(200, 0), List.of(200, 40),
                        List.of(60, 40), List.of(60, 120), List.of(0, 120))), "id");
        HomePieceOfFurniture prism = pieceById(saveAndReadHome("extrusion"), id);
        assertEquals("E2E counter", prism.getName());
        assertEquals(200, prism.getWidth(), MODEL_EPS);
        assertEquals(120, prism.getDepth(), MODEL_EPS);
        assertEquals(90, prism.getHeight(), MODEL_EPS);
        assertModelContent(prism);
    }

    @Test
    void generateMeshUsesPlanXYAndHeightZ() throws Exception {
        String id = str(mcp.call("generate_shape", "mode", "mesh", "name", "E2E pyramid", "vertices", List.of(
                List.of(0, 0, 0), List.of(100, 0, 0), List.of(100, 80, 0), List.of(0, 80, 0), List.of(50, 40, 60)),
                "triangles", List.of(List.of(0, 1, 4), List.of(1, 2, 4), List.of(2, 3, 4), List.of(3, 0, 4),
                        List.of(0, 2, 1), List.of(0, 3, 2))), "id");
        HomePieceOfFurniture pyramid = pieceById(saveAndReadHome("mesh"), id);
        assertEquals(100, pyramid.getWidth(), MODEL_EPS);
        assertEquals(80, pyramid.getDepth(), MODEL_EPS);
        assertEquals(60, pyramid.getHeight(), MODEL_EPS);
        assertModelContent(pyramid);
    }

    @Test
    void generateShapeRejectsAnUnknownModeWithoutAddingFurniture() {
        placeResizableFurniture(10, 20);
        assertRejectedWithoutChange("generate_shape", McpLiveClient.args("mode", "not-a-shape"));
    }

    private static Map<String, Object> command(String action, Map<String, Object> params) {
        return McpLiveClient.args("action", action, "params", params);
    }

    private static void assertModelContent(HomePieceOfFurniture piece) throws Exception {
        assertNotNull(piece.getModel(), "the generated model is embedded in the saved home");
        try (InputStream input = piece.getModel().openStream()) {
            assertTrue(input.read() >= 0, "embedded geometry is not empty");
        }
    }
}
