package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DeleteDimensionLineHandlerTest {

    private DeleteDimensionLineHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteDimensionLineHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testDeleteSingleDimensionLine() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getDimensionLines().size());
    }

    @Test
    void testResponseContainsDeletedInfo() {
        DimensionLine dim = addDimensionLine(home, 100, 200, 600, 200, 30);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(100f, ((Number) data.get("xStart")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yStart")).floatValue(), 0.01f);
        assertEquals(600f, ((Number) data.get("xEnd")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yEnd")).floatValue(), 0.01f);
        assertEquals(30f, ((Number) data.get("offset")).floatValue(), 0.01f);
        assertEquals(500f, ((Number) data.get("length")).floatValue(), 0.01f);
        assertTrue(((String) data.get("message")).contains("deleted"));
    }

    @Test
    void testDeletesOnlyTargetFromMultiple() {
        addDimensionLine(home, 0, 0, 500, 0, 25);
        DimensionLine target = addDimensionLine(home, 0, 0, 0, 400, 25);
        addDimensionLine(home, 0, 400, 500, 400, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", target.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, home.getDimensionLines().size());
        for (DimensionLine remaining : new ArrayList<>(home.getDimensionLines())) {
            assertNotEquals(target.getId(), remaining.getId());
        }
    }

    @Test
    void testIdNotFound() {
        addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", "nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getDimensionLines().size());
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeIdRequest("delete_dimension_line", "any-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testMissingId() {
        Response resp = handler.execute(
                new Request("delete_dimension_line", Collections.emptyMap()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("id"));
    }

    @Test
    void testDoesNotTouchOtherObjectTypes() {
        addWall(home, 0, 0, 500, 0);
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getDimensionLines().size());
        assertEquals(1, home.getWalls().size(), "walls must be left untouched");
    }

    @Test
    void testResponseIdentifiesTheDeletedLine() {
        DimensionLine dim = addDimensionLine(home, 100, 200, 600, 200, 30);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(dim.getId(), resp.getData().get("id"));
        assertTrue(resp.getData().containsKey("level"));
        assertNull(resp.getData().get("level"), "no levels in this home");
    }

    @Test
    void testSecondDeleteOfSameIdIsNotFound() {
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);
        addDimensionLine(home, 0, 400, 500, 400, 25);

        assertTrue(handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor).isOk());
        Response again = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertTrue(again.isError());
        assertTrue(again.getMessage().contains("not found"));
        assertEquals(1, home.getDimensionLines().size(), "the other line must survive both calls");
    }

    @Test
    void testIdOfAnotherObjectTypeIsNotFound() {
        Wall wall = addWall(home, 0, 0, 500, 0);
        addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", wall.getId()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getWalls().size(), "a wall id must not delete the wall");
        assertEquals(1, home.getDimensionLines().size());
    }

    @Test
    void testDeletesLineOnAnotherLevelAndReportsItsLevel() {
        Level ground = addLevel(home, "Ground", 0, 250, 12);
        Level first = addLevel(home, "First", 262, 240, 12);
        home.setSelectedLevel(first);
        DimensionLine upstairs = addDimensionLine(home, 0, 0, 500, 0, 25);
        home.setSelectedLevel(ground);
        DimensionLine downstairs = addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", upstairs.getId()), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals("First", resp.getData().get("level"));
        assertEquals(1, home.getDimensionLines().size());
        assertSame(downstairs, home.getDimensionLines().iterator().next());
    }

    @Test
    void testResponseDescribesTheTargetAmongSeveralLines() {
        addDimensionLine(home, 1, 2, 3, 4, 5);
        DimensionLine target = addDimensionLine(home, 10, 20, 510, 20, 25);
        addDimensionLine(home, 6, 7, 8, 9, 10);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", target.getId()), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        Map<String, Object> data = resp.getData();
        assertEquals(target.getId(), data.get("id"));
        assertEquals(10f, ((Number) data.get("xStart")).floatValue(), 0.01f);
        assertEquals(20f, ((Number) data.get("yStart")).floatValue(), 0.01f);
        assertEquals(510f, ((Number) data.get("xEnd")).floatValue(), 0.01f);
        assertEquals(20f, ((Number) data.get("yEnd")).floatValue(), 0.01f);
        assertEquals(25f, ((Number) data.get("offset")).floatValue(), 0.01f);
        assertEquals(500f, ((Number) data.get("length")).floatValue(), 0.01f);
        assertTrue(((String) data.get("message")).contains(target.getId()));
    }

    @Test
    void testResponseKeysInOrder() {
        DimensionLine dim = addDimensionLine(home, 10, 20, 510, 40, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor);

        assertEquals(
                Arrays.asList("id", "xStart", "yStart", "xEnd", "yEnd", "offset", "length", "level", "message"),
                new ArrayList<>(resp.getData().keySet()));
    }

    @Test
    void testIdOfALabelIsNotFound() {
        Label label = new Label("Kitchen", 100, 200);
        home.addLabel(label);
        addDimensionLine(home, 0, 0, 500, 0, 25);

        Response resp = handler.execute(makeIdRequest("delete_dimension_line", label.getId()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getLabels().size(), "a label id must not delete the label");
        assertEquals(1, home.getDimensionLines().size());
    }

    @Test
    void testLineIsDeletedOnTheEventDispatchThread() {
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test itself must not run on the EDT");
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        home.addDimensionLinesListener(ev -> onEdt.set(SwingUtilities.isEventDispatchThread()));

        assertTrue(handler.execute(makeIdRequest("delete_dimension_line", dim.getId()), accessor).isOk());

        assertEquals(Boolean.TRUE, onEdt.get(), "the model must only be changed inside runOnEDT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaTypes() {
        Map<String, Object> props = (Map<String, Object>) handler.getSchema().get("properties");

        assertEquals("string", ((Map<String, Object>) props.get("id")).get("type"));
        assertEquals(1, props.size());
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }
}
