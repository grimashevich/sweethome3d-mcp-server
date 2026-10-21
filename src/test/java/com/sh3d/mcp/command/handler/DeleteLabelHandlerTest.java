package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.TextStyle;
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

class DeleteLabelHandlerTest {

    private DeleteLabelHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteLabelHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testDeleteSingleLabel() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = handler.execute(makeIdRequest("delete_label", label.getId()), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(0, home.getLabels().size());
    }

    @Test
    void testResponseContainsDeletedInfo() {
        Label label = addLabel(home, "Kitchen", 100, 200);
        label.setAngle((float) Math.toRadians(90));
        label.setColor(0xFF0000);
        label.setStyle(new TextStyle(24f, true, false));

        Response resp = handler.execute(makeIdRequest("delete_label", label.getId()), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        Map<String, Object> data = resp.getData();
        assertEquals(label.getId(), data.get("id"));
        assertEquals("Kitchen", data.get("text"));
        assertEquals(100f, ((Number) data.get("x")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("y")).floatValue(), 0.01f);
        assertEquals(90f, ((Number) data.get("angle")).floatValue(), 0.01f);
        assertEquals("#FF0000", data.get("color"));
        @SuppressWarnings("unchecked")
        Map<String, Object> style = (Map<String, Object>) data.get("style");
        assertEquals(24f, ((Number) style.get("fontSize")).floatValue(), 0f);
        assertEquals(true, style.get("bold"));
        assertTrue(((String) data.get("message")).contains("deleted"));
        assertTrue(((String) data.get("message")).contains(label.getId()));
    }

    @Test
    void testDeletesOnlyTargetFromMultiple() {
        addLabel(home, "A", 0, 0);
        Label target = addLabel(home, "B", 10, 10);
        addLabel(home, "C", 20, 20);

        Response resp = handler.execute(makeIdRequest("delete_label", target.getId()), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(2, home.getLabels().size());
        for (Label remaining : new ArrayList<>(home.getLabels())) {
            assertNotEquals(target.getId(), remaining.getId());
        }
        assertEquals("B", resp.getData().get("text"));
    }

    @Test
    void testIdNotFound() {
        addLabel(home, "Kitchen", 100, 200);

        Response resp = handler.execute(makeIdRequest("delete_label", "nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getLabels().size());
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeIdRequest("delete_label", "any-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testMissingId() {
        Response resp = handler.execute(new Request("delete_label", Collections.emptyMap()), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("id"));
    }

    @Test
    void testSecondDeleteOfSameIdIsNotFound() {
        Label label = addLabel(home, "Kitchen", 100, 200);
        addLabel(home, "Hall", 300, 200);

        assertTrue(handler.execute(makeIdRequest("delete_label", label.getId()), accessor).isOk());
        Response again = handler.execute(makeIdRequest("delete_label", label.getId()), accessor);

        assertTrue(again.isError());
        assertTrue(again.getMessage().contains("not found"));
        assertEquals(1, home.getLabels().size(), "the other label must survive both calls");
    }

    @Test
    void testIdOfAnotherObjectTypeIsNotFound() {
        Wall wall = addWall(home, 0, 0, 500, 0);
        DimensionLine dim = addDimensionLine(home, 0, 0, 500, 0, 25);
        addLabel(home, "Kitchen", 100, 200);

        for (String id : Arrays.asList(wall.getId(), dim.getId())) {
            Response resp = handler.execute(makeIdRequest("delete_label", id), accessor);

            assertTrue(resp.isError());
            assertTrue(resp.getMessage().contains("not found"));
        }
        assertEquals(1, home.getWalls().size(), "a wall id must not delete the wall");
        assertEquals(1, home.getDimensionLines().size(), "a dimension line id must not delete the line");
        assertEquals(1, home.getLabels().size());
    }

    @Test
    void testDoesNotTouchOtherObjectTypes() {
        addWall(home, 0, 0, 500, 0);
        addDimensionLine(home, 0, 0, 500, 0, 25);
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = handler.execute(makeIdRequest("delete_label", label.getId()), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(0, home.getLabels().size());
        assertEquals(1, home.getWalls().size());
        assertEquals(1, home.getDimensionLines().size());
    }

    @Test
    void testDeletesLabelOnAnotherLevelAndReportsItsLevel() {
        Level ground = addLevel(home, "Ground", 0, 250, 12);
        Level first = addLevel(home, "First", 262, 240, 12);
        home.setSelectedLevel(first);
        Label upstairs = addLabel(home, "Bedroom", 100, 200);
        home.setSelectedLevel(ground);
        Label downstairs = addLabel(home, "Kitchen", 100, 200);

        Response resp = handler.execute(makeIdRequest("delete_label", upstairs.getId()), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals("First", resp.getData().get("level"));
        assertEquals(1, home.getLabels().size());
        assertSame(downstairs, home.getLabels().iterator().next());
        assertSame(ground, home.getSelectedLevel(), "deleting must not change the selected level");
    }

    @Test
    void testLevelIsNullInHomeWithoutLevels() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = handler.execute(makeIdRequest("delete_label", label.getId()), accessor);

        assertTrue(resp.getData().containsKey("level"));
        assertNull(resp.getData().get("level"));
    }

    @Test
    void testResponseKeysInOrder() {
        Label label = addLabel(home, "Kitchen", 100, 200);

        Response resp = handler.execute(makeIdRequest("delete_label", label.getId()), accessor);

        assertEquals(Arrays.asList("id", "text", "x", "y", "angle", "color", "outlineColor", "elevation",
                        "pitch", "level", "message"),
                new ArrayList<>(resp.getData().keySet()));
    }

    @Test
    void testResponseKeysInOrderWithStyle() {
        Label label = addLabel(home, "Kitchen", 100, 200);
        label.setStyle(new TextStyle(24f));

        Response resp = handler.execute(makeIdRequest("delete_label", label.getId()), accessor);

        assertEquals(Arrays.asList("id", "text", "x", "y", "angle", "color", "outlineColor", "elevation",
                        "pitch", "style", "level", "message"),
                new ArrayList<>(resp.getData().keySet()));
    }

    @Test
    void testLabelIsDeletedOnTheEventDispatchThread() {
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test itself must not run on the EDT");
        Label label = addLabel(home, "Kitchen", 100, 200);
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        home.addLabelsListener(ev -> onEdt.set(SwingUtilities.isEventDispatchThread()));

        assertTrue(handler.execute(makeIdRequest("delete_label", label.getId()), accessor).isOk());

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
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }
}
