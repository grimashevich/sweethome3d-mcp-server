package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.Home;
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

class CheckpointHandlerTest {

    private CheckpointManager checkpointManager;
    private CheckpointHandler handler;
    private ListCheckpointsHandler listHandler;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        checkpointManager = new CheckpointManager();
        handler = new CheckpointHandler(checkpointManager);
        listHandler = new ListCheckpointsHandler(checkpointManager);
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Functional ---

    @Test
    @SuppressWarnings("unchecked")
    void testCheckpointWithoutDescription_autoGeneratesDescription() {
        addWall(home, 0, 0, 500, 0);
        addWall(home, 500, 0, 500, 400);

        Response resp = execute(Collections.emptyMap());
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        String description = (String) data.get("description");
        assertNotNull(description);
        assertTrue(description.contains("2 walls"), "Expected '2 walls' in: " + description);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckpointWithCustomDescription() {
        Response resp = execute(Map.of("description", "Before adding bedroom furniture"));
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("Before adding bedroom furniture", data.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckpointOnEmptyScene_emptySceneDescription() {
        Response resp = execute(Collections.emptyMap());
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("empty scene", data.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckpointReturnsCorrectIdDepthMaxDepth() {
        Response resp = execute(Map.of("description", "first"));
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("id"));
        assertEquals(1, data.get("depth"));
        assertEquals(CheckpointManager.DEFAULT_MAX_DEPTH, data.get("maxDepth"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMultipleCheckpointsIncrementId() {
        Response resp1 = execute(Map.of("description", "first"));
        Response resp2 = execute(Map.of("description", "second"));
        Response resp3 = execute(Map.of("description", "third"));

        assertFalse(resp1.isError());
        assertFalse(resp2.isError());
        assertFalse(resp3.isError());

        Map<String, Object> data1 = (Map<String, Object>) resp1.getData();
        Map<String, Object> data2 = (Map<String, Object>) resp2.getData();
        Map<String, Object> data3 = (Map<String, Object>) resp3.getData();

        assertEquals(0, data1.get("id"));
        assertEquals(1, data2.get("id"));
        assertEquals(2, data3.get("id"));

        assertEquals(1, data1.get("depth"));
        assertEquals(2, data2.get("depth"));
        assertEquals(3, data3.get("depth"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckpointAfterRestoreTruncatesForwardHistory() {
        // Create 3 checkpoints
        execute(Map.of("description", "cp-0"));
        execute(Map.of("description", "cp-1"));
        execute(Map.of("description", "cp-2"));

        assertEquals(3, checkpointManager.size());

        // Restore to cp-0
        checkpointManager.restore(0);
        assertEquals(0, checkpointManager.getCursor());

        // Create new checkpoint -- should truncate cp-1 and cp-2
        Response resp = execute(Map.of("description", "cp-new"));
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, data.get("depth")); // cp-0 + cp-new

        // Verify via list: only cp-0 and cp-new
        Response listResp = listHandler.execute(
                new Request("list_checkpoints", Collections.emptyMap()), accessor);
        Map<String, Object> listData = (Map<String, Object>) listResp.getData();
        List<Map<String, Object>> checkpoints =
                (List<Map<String, Object>>) listData.get("checkpoints");
        assertEquals(2, checkpoints.size());
        assertEquals("cp-0", checkpoints.get(0).get("description"));
        assertEquals("cp-new", checkpoints.get(1).get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAutoDescriptionIncludesWallsFurnitureRooms() {
        addWall(home, 0, 0, 500, 0);
        addRoom(home, new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        home.addPieceOfFurniture(
                new com.eteks.sweethome3d.model.HomePieceOfFurniture(
                        new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                                "Chair", null, null, 50f, 50f, 90f, true, false)));

        Response resp = execute(Collections.emptyMap());
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        String description = (String) data.get("description");
        assertTrue(description.contains("1 walls"), "Expected '1 walls' in: " + description);
        assertTrue(description.contains("1 furniture"), "Expected '1 furniture' in: " + description);
        assertTrue(description.contains("1 rooms"), "Expected '1 rooms' in: " + description);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckpointWithMaxDepthEvictsOldest() {
        CheckpointManager smallManager = new CheckpointManager(2);
        CheckpointHandler smallHandler = new CheckpointHandler(smallManager);

        // Fill to capacity
        smallHandler.execute(new Request("checkpoint", Map.of("description", "first")), accessor);
        smallHandler.execute(new Request("checkpoint", Map.of("description", "second")), accessor);
        assertEquals(2, smallManager.size());

        // One more should evict the oldest
        Response resp = smallHandler.execute(
                new Request("checkpoint", Map.of("description", "third")), accessor);
        assertFalse(resp.isError());
        assertEquals(2, smallManager.size());

        // Verify oldest was evicted
        List<CheckpointManager.SnapshotInfo> snapshots = smallManager.list();
        assertEquals("second", snapshots.get(0).getDescription());
        assertEquals("third", snapshots.get(1).getDescription());
    }

    // --- Descriptor ---

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("snapshot") || desc.contains("checkpoint"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaHasOptionalDescriptionParam() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("description"));

        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandHandler);
        assertTrue(handler instanceof CommandDescriptor);
    }

    // --- Helper ---

    private Response execute(Map<String, Object> params) {
        return handler.execute(new Request("checkpoint", params), accessor);
    }
}
