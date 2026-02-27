package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandRegistry;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.CommandException;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

class BatchCommandsHandlerTest {

    private BatchCommandsHandler handler;
    private CommandRegistry registry;
    private CheckpointManager checkpointManager;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        home = new Home();
        accessor = createAccessor(home);
        checkpointManager = new CheckpointManager();
        registry = new CommandRegistry();
        registry.register("ping", (req, acc) ->
                Response.ok(Collections.singletonMap("pong", true)));
        registry.register("create_wall", new CreateWallHandler());
        registry.register("connect_walls", new ConnectWallsHandler());
        handler = new BatchCommandsHandler(registry, checkpointManager);
        registry.register("batch_commands", handler);
    }

    // --- Success cases ---

    @Test
    @SuppressWarnings("unchecked")
    void testSingleCommandSuccess() {
        Response resp = executeBatch(Arrays.asList(cmd("ping", null)));

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(1, data.get("total"));
        assertEquals(1, data.get("succeeded"));
        assertEquals(0, data.get("failed"));

        List<Object> results = (List<Object>) data.get("results");
        assertEquals(1, results.size());

        Map<String, Object> r = (Map<String, Object>) results.get(0);
        assertEquals("ok", r.get("status"));
        assertEquals("ping", r.get("action"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMultipleCommandsAllSucceed() {
        List<Map<String, Object>> cmds = Arrays.asList(
                cmd("create_wall", wallParams(0, 0, 500, 0)),
                cmd("create_wall", wallParams(500, 0, 500, 300)),
                cmd("create_wall", wallParams(500, 300, 0, 300)));

        Response resp = executeBatch(cmds);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(3, data.get("total"));
        assertEquals(3, data.get("succeeded"));
        assertEquals(0, data.get("failed"));
        assertEquals(3, home.getWalls().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCollectAllErrorStrategy() {
        List<Map<String, Object>> cmds = Arrays.asList(
                cmd("ping", null),
                cmd("nonexistent_action", null),
                cmd("ping", null));

        Response resp = executeBatch(cmds);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(3, data.get("total"));
        assertEquals(2, data.get("succeeded"));
        assertEquals(1, data.get("failed"));

        List<Object> results = (List<Object>) data.get("results");
        Map<String, Object> r0 = (Map<String, Object>) results.get(0);
        Map<String, Object> r1 = (Map<String, Object>) results.get(1);
        Map<String, Object> r2 = (Map<String, Object>) results.get(2);

        assertEquals("ok", r0.get("status"));
        assertEquals("error", r1.get("status"));
        assertTrue(((String) r1.get("message")).contains("Unknown action"));
        assertEquals("ok", r2.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSequentialExecutionDependsOnPrior() {
        // Pre-create walls to get stable IDs (UUIDs can't be predicted)
        Wall w1 = new Wall(0, 0, 500, 0, 10);
        Wall w2 = new Wall(500, 0, 500, 300, 10);
        home.addWall(w1);
        home.addWall(w2);

        List<Map<String, Object>> cmds = Arrays.asList(
                cmd("create_wall", wallParams(0, 0, 100, 0)),
                cmd("create_wall", wallParams(100, 0, 100, 100)),
                cmd("connect_walls", connectParams(w1.getId(), w2.getId())));

        Response resp = executeBatch(cmds);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(3, data.get("succeeded"));
        assertEquals(0, data.get("failed"));

        List<Object> results = (List<Object>) data.get("results");
        Map<String, Object> connectResult = (Map<String, Object>) results.get(2);
        assertEquals("ok", connectResult.get("status"));
    }

    @Test
    void testAutoCheckpointCreatedBeforeBatch() {
        assertEquals(0, checkpointManager.size());

        executeBatch(Arrays.asList(cmd("ping", null)));

        assertEquals(1, checkpointManager.size());
        assertTrue(checkpointManager.list().get(0).getDescription()
                .startsWith("Auto: before batch_commands"));
    }

    @Test
    void testCommandWithoutParams() {
        Response resp = executeBatch(Arrays.asList(cmd("ping", null)));

        assertTrue(resp.isOk());
        assertEquals(1, resp.getData().get("succeeded"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBatchSizeAtMaxSucceeds() {
        List<Map<String, Object>> cmds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            cmds.add(cmd("ping", null));
        }

        Response resp = executeBatch(cmds);

        assertTrue(resp.isOk());
        assertEquals(50, resp.getData().get("total"));
        assertEquals(50, resp.getData().get("succeeded"));
        assertEquals(0, resp.getData().get("failed"));
    }

    // --- Validation: top-level errors ---

    @Test
    void testMissingCommandsParam() {
        Request req = new Request("batch_commands", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Missing required parameter: commands"));
    }

    @Test
    void testCommandsNotArray() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("commands", "not_an_array");

        Request req = new Request("batch_commands", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("must be an array"));
    }

    @Test
    void testEmptyCommandsArray() {
        Response resp = executeBatch(Collections.emptyList());

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("must not be empty"));
    }

    @Test
    void testBatchSizeExceedsMax() {
        List<Map<String, Object>> cmds = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            cmds.add(cmd("ping", null));
        }

        Response resp = executeBatch(cmds);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("exceeds maximum of 50"));
    }

    // --- Validation: per-item errors (continue execution) ---

    @Test
    @SuppressWarnings("unchecked")
    void testNestedBatchCommandsRejected() {
        List<Map<String, Object>> cmds = Arrays.asList(
                cmd("ping", null),
                cmd("batch_commands", null),
                cmd("ping", null));

        Response resp = executeBatch(cmds);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(2, data.get("succeeded"));
        assertEquals(1, data.get("failed"));

        List<Object> results = (List<Object>) data.get("results");
        Map<String, Object> nested = (Map<String, Object>) results.get(1);
        assertEquals("error", nested.get("status"));
        assertTrue(((String) nested.get("message")).contains("Nested"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInvalidCommandFormatNotObject() {
        List<Object> rawCommands = new ArrayList<>();
        rawCommands.add("just_a_string");
        rawCommands.add(cmd("ping", null));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("commands", rawCommands);
        Request req = new Request("batch_commands", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(1, data.get("succeeded"));
        assertEquals(1, data.get("failed"));

        List<Object> results = (List<Object>) data.get("results");
        Map<String, Object> bad = (Map<String, Object>) results.get(0);
        assertEquals("error", bad.get("status"));
        assertTrue(((String) bad.get("message")).contains("not an object"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMissingActionField() {
        Map<String, Object> noAction = new LinkedHashMap<>();
        noAction.put("params", Collections.emptyMap());

        List<Map<String, Object>> cmds = Arrays.asList(noAction, cmd("ping", null));

        Response resp = executeBatch(cmds);

        assertTrue(resp.isOk());
        assertEquals(1, resp.getData().get("succeeded"));
        assertEquals(1, resp.getData().get("failed"));

        List<Object> results = (List<Object>) resp.getData().get("results");
        Map<String, Object> bad = (Map<String, Object>) results.get(0);
        assertTrue(((String) bad.get("message")).contains("missing 'action'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEmptyActionField() {
        Map<String, Object> emptyAction = new LinkedHashMap<>();
        emptyAction.put("action", "");

        Response resp = executeBatch(Arrays.asList(emptyAction));

        assertTrue(resp.isOk());
        assertEquals(1, resp.getData().get("failed"));

        List<Object> results = (List<Object>) resp.getData().get("results");
        Map<String, Object> bad = (Map<String, Object>) results.get(0);
        assertTrue(((String) bad.get("message")).contains("missing 'action'"));
    }

    // --- Result structure ---

    @Test
    @SuppressWarnings("unchecked")
    void testResultContainsIndex() {
        List<Map<String, Object>> cmds = Arrays.asList(
                cmd("ping", null),
                cmd("ping", null),
                cmd("ping", null));

        Response resp = executeBatch(cmds);
        List<Object> results = (List<Object>) resp.getData().get("results");

        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> r = (Map<String, Object>) results.get(i);
            assertEquals(i, r.get("index"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSuccessResultContainsData() {
        Response resp = executeBatch(Arrays.asList(
                cmd("create_wall", wallParams(0, 0, 100, 0))));

        List<Object> results = (List<Object>) resp.getData().get("results");
        Map<String, Object> r = (Map<String, Object>) results.get(0);
        assertEquals("ok", r.get("status"));
        assertNotNull(r.get("data"));
        assertTrue(r.get("data") instanceof Map);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testErrorResultContainsMessage() {
        Response resp = executeBatch(Arrays.asList(
                cmd("nonexistent", null)));

        List<Object> results = (List<Object>) resp.getData().get("results");
        Map<String, Object> r = (Map<String, Object>) results.get(0);
        assertEquals("error", r.get("status"));
        assertNotNull(r.get("message"));
        assertTrue(r.get("message") instanceof String);
    }

    // --- Exception handling in sub-commands ---

    @Test
    @SuppressWarnings("unchecked")
    void testSubCommandExceptionCaught() {
        registry.register("throw_command_ex", (req, acc) -> {
            throw new CommandException("test command error");
        });

        Response resp = executeBatch(Arrays.asList(
                cmd("throw_command_ex", null),
                cmd("ping", null)));

        assertTrue(resp.isOk());
        assertEquals(1, resp.getData().get("succeeded"));
        assertEquals(1, resp.getData().get("failed"));

        List<Object> results = (List<Object>) resp.getData().get("results");
        Map<String, Object> err = (Map<String, Object>) results.get(0);
        assertEquals("error", err.get("status"));
        assertTrue(((String) err.get("message")).contains("test command error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSubCommandRuntimeExceptionCaught() {
        registry.register("throw_runtime", (req, acc) -> {
            throw new RuntimeException("unexpected failure");
        });

        Response resp = executeBatch(Arrays.asList(
                cmd("throw_runtime", null),
                cmd("ping", null)));

        assertTrue(resp.isOk());
        assertEquals(1, resp.getData().get("succeeded"));
        assertEquals(1, resp.getData().get("failed"));

        List<Object> results = (List<Object>) resp.getData().get("results");
        Map<String, Object> err = (Map<String, Object>) results.get(0);
        assertEquals("error", err.get("status"));
        assertTrue(((String) err.get("message")).contains("Internal error"));
    }

    // --- Descriptor ---

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("batch") || desc.contains("multiple") || desc.contains("commands"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("commands"));

        Map<String, Object> commandsProp = (Map<String, Object>) properties.get("commands");
        assertEquals("array", commandsProp.get("type"));
        assertEquals(50, commandsProp.get("maxItems"));
        assertNotNull(commandsProp.get("items"));

        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("commands"));
    }

    // --- Helpers ---

    private Response executeBatch(List<? extends Map<String, Object>> commands) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("commands", commands);
        Request req = new Request("batch_commands", params);
        return handler.execute(req, accessor);
    }

    private static Map<String, Object> cmd(String action, Map<String, Object> params) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("action", action);
        if (params != null) {
            c.put("params", params);
        }
        return c;
    }

    private static Map<String, Object> wallParams(float xStart, float yStart,
                                                    float xEnd, float yEnd) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("xStart", xStart);
        p.put("yStart", yStart);
        p.put("xEnd", xEnd);
        p.put("yEnd", yEnd);
        return p;
    }

    private static Map<String, Object> connectParams(String wall1Id, String wall2Id) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("wall1Id", wall1Id);
        p.put("wall2Id", wall2Id);
        return p;
    }
}
