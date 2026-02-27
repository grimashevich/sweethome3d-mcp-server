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

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class ListCheckpointsHandlerTest {

    private CheckpointManager checkpointManager;
    private CheckpointHandler checkpointHandler;
    private RestoreCheckpointHandler restoreHandler;
    private ListCheckpointsHandler listHandler;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        checkpointManager = new CheckpointManager(10);
        checkpointHandler = new CheckpointHandler(checkpointManager);
        restoreHandler = new RestoreCheckpointHandler(checkpointManager);
        listHandler = new ListCheckpointsHandler(checkpointManager);
        home = new Home();
        accessor = createAccessor(home);
    }

    // --- Functional ---

    @Test
    @SuppressWarnings("unchecked")
    void testListWithNoCheckpoints_emptyList() {
        Response resp = executeList();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("count"));
        assertEquals(-1, data.get("cursor"));
        assertEquals(10, data.get("maxDepth"));

        List<Map<String, Object>> checkpoints =
                (List<Map<String, Object>>) data.get("checkpoints");
        assertNotNull(checkpoints);
        assertTrue(checkpoints.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListAfterOneCheckpoint() {
        createCheckpoint("initial state");

        Response resp = executeList();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, data.get("count"));
        assertEquals(0, data.get("cursor"));

        List<Map<String, Object>> checkpoints =
                (List<Map<String, Object>>) data.get("checkpoints");
        assertEquals(1, checkpoints.size());

        Map<String, Object> cp = checkpoints.get(0);
        assertEquals(0, cp.get("id"));
        assertEquals("initial state", cp.get("description"));
        assertTrue((boolean) cp.get("current"));
        assertNotNull(cp.get("timestamp"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListAfterMultipleCheckpoints_allWithCorrectCurrentMarker() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        createCheckpoint("cp-2");

        Response resp = executeList();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(3, data.get("count"));
        assertEquals(2, data.get("cursor"));

        List<Map<String, Object>> checkpoints =
                (List<Map<String, Object>>) data.get("checkpoints");
        assertEquals(3, checkpoints.size());

        // Only the last one (cursor=2) should be marked as current
        assertFalse((boolean) checkpoints.get(0).get("current"));
        assertFalse((boolean) checkpoints.get(1).get("current"));
        assertTrue((boolean) checkpoints.get(2).get("current"));

        // Verify descriptions
        assertEquals("cp-0", checkpoints.get(0).get("description"));
        assertEquals("cp-1", checkpoints.get(1).get("description"));
        assertEquals("cp-2", checkpoints.get(2).get("description"));

        // Verify IDs
        assertEquals(0, checkpoints.get(0).get("id"));
        assertEquals(1, checkpoints.get(1).get("id"));
        assertEquals(2, checkpoints.get(2).get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListAfterRestore_showsUpdatedCursorPosition() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        createCheckpoint("cp-2");

        // Restore to cp-1
        restoreHandler.execute(
                new Request("restore_checkpoint", Map.of("id", "1")), accessor);

        Response resp = executeList();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(3, data.get("count")); // all 3 still exist
        assertEquals(1, data.get("cursor")); // cursor moved to 1

        List<Map<String, Object>> checkpoints =
                (List<Map<String, Object>>) data.get("checkpoints");
        assertEquals(3, checkpoints.size());

        // Only cp-1 should be current now
        assertFalse((boolean) checkpoints.get(0).get("current"));
        assertTrue((boolean) checkpoints.get(1).get("current"));
        assertFalse((boolean) checkpoints.get(2).get("current"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListReturnsCountCursorMaxDepth() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");

        Response resp = executeList();
        Map<String, Object> data = (Map<String, Object>) resp.getData();

        assertTrue(data.containsKey("count"));
        assertTrue(data.containsKey("cursor"));
        assertTrue(data.containsKey("maxDepth"));
        assertTrue(data.containsKey("checkpoints"));
        assertEquals(4, data.size());

        assertEquals(2, data.get("count"));
        assertEquals(1, data.get("cursor"));
        assertEquals(10, data.get("maxDepth"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListTimestampsAreMonotonicallyIncreasing() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        createCheckpoint("cp-2");

        Response resp = executeList();
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        List<Map<String, Object>> checkpoints =
                (List<Map<String, Object>>) data.get("checkpoints");

        long prev = 0;
        for (Map<String, Object> cp : checkpoints) {
            long ts = (long) cp.get("timestamp");
            assertTrue(ts >= prev, "Timestamps should be monotonically increasing");
            prev = ts;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListAfterRestoreAndNewCheckpoint_forwardTruncated() {
        createCheckpoint("cp-0");
        createCheckpoint("cp-1");
        createCheckpoint("cp-2");

        // Restore to cp-0
        restoreHandler.execute(
                new Request("restore_checkpoint", Map.of("id", "0")), accessor);

        // Create new checkpoint -- truncates cp-1 and cp-2
        createCheckpoint("cp-new");

        Response resp = executeList();
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, data.get("count"));
        assertEquals(1, data.get("cursor"));

        List<Map<String, Object>> checkpoints =
                (List<Map<String, Object>>) data.get("checkpoints");
        assertEquals(2, checkpoints.size());
        assertEquals("cp-0", checkpoints.get(0).get("description"));
        assertEquals("cp-new", checkpoints.get(1).get("description"));
        assertTrue((boolean) checkpoints.get(1).get("current"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListWithDefaultMaxDepth() {
        CheckpointManager defaultManager = new CheckpointManager();
        ListCheckpointsHandler defaultListHandler = new ListCheckpointsHandler(defaultManager);

        Response resp = defaultListHandler.execute(
                new Request("list_checkpoints", Collections.emptyMap()), accessor);
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(CheckpointManager.DEFAULT_MAX_DEPTH, data.get("maxDepth"));
    }

    // --- Descriptor ---

    @Test
    void testDescriptionNotEmpty() {
        String desc = listHandler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("checkpoint") || desc.contains("Checkpoint"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoParams() {
        Map<String, Object> schema = listHandler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props);
        assertTrue(props.isEmpty());

        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(listHandler instanceof CommandHandler);
        assertTrue(listHandler instanceof CommandDescriptor);
    }

    // --- Helpers ---

    private void createCheckpoint(String description) {
        checkpointHandler.execute(
                new Request("checkpoint", Map.of("description", description)), accessor);
    }

    private Response executeList() {
        return listHandler.execute(
                new Request("list_checkpoints", Collections.emptyMap()), accessor);
    }
}
