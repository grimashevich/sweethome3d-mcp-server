package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Polyline;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ClearSceneHandlerTest {

    private ClearSceneHandler handler;
    private CheckpointManager checkpointManager;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        checkpointManager = new CheckpointManager();
        handler = new ClearSceneHandler(checkpointManager);
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Functional ---

    @Test
    @SuppressWarnings("unchecked")
    void testClearEmptyScene() {
        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("deletedWalls"));
        assertEquals(0, data.get("deletedFurniture"));
        assertEquals(0, data.get("deletedRooms"));
        assertEquals(0, data.get("deletedPolylines"));
        assertEquals(0, data.get("deletedLabels"));
        assertEquals(0, data.get("deletedDimensionLines"));
        assertEquals(0, data.get("totalDeleted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearWalls() {
        addWall(home, 0, 0, 500, 0);
        addWall(home, 500, 0, 500, 400);
        assertEquals(2, home.getWalls().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, data.get("deletedWalls"));
        assertEquals(2, data.get("totalDeleted"));
        assertTrue(home.getWalls().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearFurniture() {
        addFurniture(home, "Test Chair", 0, 0);
        assertEquals(1, home.getFurniture().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, data.get("deletedFurniture"));
        assertTrue(home.getFurniture().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearRooms() {
        addRoom(home, new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        addRoom(home, new float[][]{{600, 0}, {900, 0}, {900, 300}});
        assertEquals(2, home.getRooms().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, data.get("deletedRooms"));
        assertTrue(home.getRooms().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearLabels() {
        home.addLabel(new Label("Label 1", 100, 100));
        home.addLabel(new Label("Label 2", 200, 200));
        home.addLabel(new Label("Label 3", 300, 300));
        assertEquals(3, home.getLabels().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(3, data.get("deletedLabels"));
        assertTrue(home.getLabels().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearDimensionLines() {
        home.addDimensionLine(new DimensionLine(0, 0, 500, 0, 20));
        assertEquals(1, home.getDimensionLines().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, data.get("deletedDimensionLines"));
        assertTrue(home.getDimensionLines().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearPolylines() {
        home.addPolyline(new Polyline(new float[][]{{0, 0}, {100, 100}, {200, 0}}));
        home.addPolyline(new Polyline(new float[][]{{50, 50}, {150, 150}}));
        assertEquals(2, home.getPolylines().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, data.get("deletedPolylines"));
        assertTrue(home.getPolylines().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearMixedScene() {
        // Add various objects
        addWall(home, 0, 0, 500, 0);
        addWall(home, 500, 0, 500, 400);
        addWall(home, 500, 400, 0, 400);
        addRoom(home, new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        home.addPolyline(new Polyline(new float[][]{{0, 0}, {500, 400}}));
        home.addLabel(new Label("Kitchen", 250, 200));
        home.addDimensionLine(new DimensionLine(0, 0, 500, 0, 20));

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(3, data.get("deletedWalls"));
        assertEquals(0, data.get("deletedFurniture"));
        assertEquals(1, data.get("deletedRooms"));
        assertEquals(1, data.get("deletedPolylines"));
        assertEquals(1, data.get("deletedLabels"));
        assertEquals(1, data.get("deletedDimensionLines"));
        assertEquals(7, data.get("totalDeleted"));

        // Verify everything is empty
        assertTrue(home.getWalls().isEmpty());
        assertTrue(home.getFurniture().isEmpty());
        assertTrue(home.getRooms().isEmpty());
        assertTrue(home.getPolylines().isEmpty());
        assertTrue(home.getLabels().isEmpty());
        assertTrue(home.getDimensionLines().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearTwiceSecondIsNoop() {
        addWall(home, 0, 0, 500, 0);
        execute();

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("totalDeleted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testResponseContainsAllFields() {
        Response resp = execute();
        Map<String, Object> data = (Map<String, Object>) resp.getData();

        assertTrue(data.containsKey("deletedWalls"));
        assertTrue(data.containsKey("deletedFurniture"));
        assertTrue(data.containsKey("deletedRooms"));
        assertTrue(data.containsKey("deletedPolylines"));
        assertTrue(data.containsKey("deletedLabels"));
        assertTrue(data.containsKey("deletedDimensionLines"));
        assertTrue(data.containsKey("totalDeleted"));
        assertEquals(7, data.size());
    }

    // --- Auto-checkpoint ---

    @Test
    void testAutoCheckpointCreatedBeforeClear() {
        addWall(home, 0, 0, 500, 0);
        assertEquals(0, checkpointManager.size());

        execute();

        assertEquals(1, checkpointManager.size());
        assertEquals("Auto: before clear_scene",
                checkpointManager.list().get(0).getDescription());
    }

    // --- Descriptor ---

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("removes") || desc.contains("Removes") || desc.contains("clear") || desc.contains("Clear"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoParams() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props);
        assertTrue(props.isEmpty());

        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }

    @Test
    void testToolNameNull() {
        assertNull(handler.getToolName());
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandHandler);
        assertTrue(handler instanceof CommandDescriptor);
    }

    // --- Helper ---

    private Response execute() {
        return handler.execute(new Request("clear_scene", Collections.emptyMap()), accessor);
    }
}
