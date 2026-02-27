package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class RestoreCheckpointHandlerTest {

    private CheckpointManager checkpointManager;
    private CheckpointHandler checkpointHandler;
    private RestoreCheckpointHandler restoreHandler;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        checkpointManager = new CheckpointManager();
        checkpointHandler = new CheckpointHandler(checkpointManager);
        restoreHandler = new RestoreCheckpointHandler(checkpointManager);
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Error cases ---

    @Test
    void testRestoreWithoutCheckpoints_error() {
        Response resp = restoreWithoutId();
        assertTrue(resp.isError());
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().toLowerCase().contains("no previous"));
    }

    @Test
    void testRestoreWithOneCheckpointAtCursor0_error() {
        createCheckpoint("only checkpoint");
        assertEquals(0, checkpointManager.getCursor());

        Response resp = restoreWithoutId();
        assertTrue(resp.isError());
        assertNotNull(resp.getMessage());
        // cursor=0, no previous to go to
        assertTrue(resp.getMessage().toLowerCase().contains("no previous"));
    }

    @Test
    void testRestoreWithInvalidId_error() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");

        Response resp = restoreWithId(999);
        assertTrue(resp.isError());
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testRestoreWithCurrentCursorId_error() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        assertEquals(1, checkpointManager.getCursor());

        Response resp = restoreWithId(1);
        assertTrue(resp.isError());
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().contains("already the current"));
    }

    @Test
    void testRestoreWithNegativeId_error() {
        createCheckpoint("cp-0");

        Response resp = restoreWithId(-1);
        assertTrue(resp.isError());
    }

    @Test
    void testRestoreWithInvalidIdFormat_error() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");

        Response resp = restoreHandler.execute(
                new Request("restore_checkpoint", Map.of("id", "not_a_number")), accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Invalid 'id'"));
    }

    // --- Successful restores ---

    @Test
    @SuppressWarnings("unchecked")
    void testRestoreAfterTwoCheckpoints_restoresFirst() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        assertEquals(1, checkpointManager.getCursor());

        Response resp = restoreWithoutId();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("restoredTo"));
        assertEquals("cp-0", data.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRestoreWithSpecificId() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        createCheckpoint("cp-2");
        assertEquals(2, checkpointManager.getCursor());

        Response resp = restoreWithId(0);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("restoredTo"));
        assertEquals("cp-0", data.get("description"));
        assertEquals(3, data.get("depth")); // all 3 snapshots still exist
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRestoreActuallyRestoresWalls() {
        // Scene with one wall -> checkpoint
        addWall(home, 0, 0, 500, 0);
        assertEquals(1, home.getWalls().size());
        createCheckpoint("1 wall");

        // Add second wall -> checkpoint
        addWall(home, 500, 0, 500, 400);
        assertEquals(2, home.getWalls().size());
        createCheckpoint("2 walls");

        // Add third wall
        addWall(home, 500, 400, 0, 400);
        assertEquals(3, home.getWalls().size());

        // Restore to first checkpoint (1 wall)
        Response resp = restoreWithId(0);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, data.get("walls"));
        assertEquals(1, home.getWalls().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRestoreActuallyRestoresRoomsAndFurniture() {
        // Scene with room + furniture
        addRoom(home, new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        home.addPieceOfFurniture(
                new com.eteks.sweethome3d.model.HomePieceOfFurniture(
                        new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                                "Chair", null, null, 50f, 50f, 90f, true, false)));
        createCheckpoint("room+furniture");

        // Add more stuff
        addRoom(home, new float[][]{{600, 0}, {900, 0}, {900, 300}});
        addWall(home, 0, 0, 500, 0);
        assertEquals(2, home.getRooms().size());
        assertEquals(1, home.getWalls().size());
        createCheckpoint("added more");

        // Restore to first checkpoint
        Response resp = restoreWithId(0);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, data.get("rooms"));
        assertEquals(1, data.get("furniture"));
        assertEquals(0, data.get("walls"));

        assertEquals(1, home.getRooms().size());
        assertEquals(1, home.getFurniture().size());
        assertEquals(0, home.getWalls().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRestoredDataIncludesCorrectCounts() {
        // Build a scene with various objects
        addWall(home, 0, 0, 500, 0);
        addWall(home, 500, 0, 500, 400);
        addRoom(home, new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        home.addLabel(new com.eteks.sweethome3d.model.Label("Kitchen", 250, 200));
        home.addDimensionLine(
                new com.eteks.sweethome3d.model.DimensionLine(0, 0, 500, 0, 20));

        createCheckpoint("full scene");

        // Clear everything
        for (Wall w : new java.util.ArrayList<>(home.getWalls())) home.deleteWall(w);
        for (Room r : new java.util.ArrayList<>(home.getRooms())) home.deleteRoom(r);
        assertTrue(home.getWalls().isEmpty());
        createCheckpoint("cleared");

        // Restore full scene
        Response resp = restoreWithId(0);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("restoredTo"));
        assertEquals("full scene", data.get("description"));
        assertEquals(2, data.get("walls"));
        assertEquals(1, data.get("rooms"));
        assertEquals(1, data.get("labels"));
        assertEquals(1, data.get("dimensionLines"));
        assertEquals(0, data.get("furniture"));
        assertEquals(0, data.get("polylines"));
        assertTrue(data.containsKey("depth"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRestoreToEmptyScene() {
        // Checkpoint empty scene
        createCheckpoint("empty");

        // Add walls
        addWall(home, 0, 0, 500, 0);
        addWall(home, 500, 0, 500, 400);
        createCheckpoint("with walls");

        // Restore to empty
        Response resp = restoreWithId(0);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("walls"));
        assertEquals(0, data.get("rooms"));
        assertEquals(0, data.get("furniture"));
        assertTrue(home.getWalls().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRedoByRestoringForwardCheckpoint() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        createCheckpoint("cp-2");

        // Undo to cp-0
        restoreWithId(0);
        assertEquals(0, checkpointManager.getCursor());

        // Redo to cp-2
        Response resp = restoreWithId(2);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, data.get("restoredTo"));
        assertEquals("cp-2", data.get("description"));
    }

    // --- Force mode ---

    @Test
    @SuppressWarnings("unchecked")
    void testForceRestoreCurrentCheckpoint_succeeds() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        assertEquals(1, checkpointManager.getCursor());

        // Without force, restoring to current cursor fails
        Response failResp = restoreWithId(1);
        assertTrue(failResp.isError());

        // With force, restoring to current cursor succeeds
        Response resp = restoreWithIdAndForce(1, true);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, data.get("restoredTo"));
        assertEquals("cp-1", data.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testForceRestoreNonCurrentCheckpoint_succeeds() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        createCheckpoint("cp-2");

        // force=true to a non-current checkpoint should also work
        Response resp = restoreWithIdAndForce(0, true);
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("restoredTo"));
        assertEquals("cp-0", data.get("description"));
    }

    @Test
    void testForceFalseWithCurrentCheckpoint_errors() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");

        // force=false behaves like no force (still errors on current)
        Response resp = restoreWithIdAndForce(1, false);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("already the current"));
    }

    @Test
    void testForceWithOutOfRangeId_errors() {
        createCheckpoint("cp-0");

        Response resp = restoreWithIdAndForce(999, true);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaHasForceParam() {
        Map<String, Object> schema = restoreHandler.getSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("force"));

        Map<String, Object> forceProp = (Map<String, Object>) props.get("force");
        assertEquals("boolean", forceProp.get("type"));
        assertEquals(false, forceProp.get("default"));
    }

    // --- Descriptor ---

    @Test
    void testDescriptionNotEmpty() {
        String desc = restoreHandler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("restore") || desc.contains("Restore"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaHasOptionalIdParam() {
        Map<String, Object> schema = restoreHandler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("id"));

        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(restoreHandler instanceof CommandHandler);
        assertTrue(restoreHandler instanceof CommandDescriptor);
    }

    // --- Helpers ---

    private void createCheckpoint(String description) {
        checkpointHandler.execute(
                new Request("checkpoint", Map.of("description", description)), accessor);
    }

    private Response restoreWithoutId() {
        return restoreHandler.execute(
                new Request("restore_checkpoint", Collections.emptyMap()), accessor);
    }

    private Response restoreWithId(int id) {
        return restoreHandler.execute(
                new Request("restore_checkpoint", Map.of("id", String.valueOf(id))), accessor);
    }

    private Response restoreWithIdAndForce(int id, boolean force) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("id", String.valueOf(id));
        params.put("force", force);
        return restoreHandler.execute(
                new Request("restore_checkpoint", params), accessor);
    }
}
