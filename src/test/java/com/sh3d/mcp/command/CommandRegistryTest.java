package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.CommandException;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CommandRegistryTest {

    private CommandRegistry registry;
    private HomeAccessor mockAccessor;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        registry.register("ping", (req, acc) ->
                Response.ok(Collections.singletonMap("version", "0.1.0")));
        mockAccessor = mock(HomeAccessor.class);
    }

    @Test
    void testRegisterAndDispatch() {
        Request req = new Request("ping", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isOk());
        assertNotNull(resp.getData());
        assertEquals("0.1.0", resp.getData().get("version"));
    }

    @Test
    void testDispatchUnknownAction() {
        Request req = new Request("unknown", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Unknown action"));
        assertTrue(resp.getMessage().contains("unknown"));
    }

    @Test
    void testDispatchCommandException() {
        registry.register("failing", (request, accessor) -> {
            throw new CommandException("Validation failed");
        });

        Request req = new Request("failing", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isError());
        assertEquals("Validation failed", resp.getMessage());
    }

    @Test
    void testDispatchInternalError() {
        registry.register("broken", (request, accessor) -> {
            throw new RuntimeException("NPE simulation");
        });

        Request req = new Request("broken", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Internal error"));
        assertTrue(resp.getMessage().contains("NPE simulation"));
    }

    @Test
    void testRegisterNullAction() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(null, (req, acc) -> Response.ok(Collections.emptyMap())));
    }

    @Test
    void testRegisterEmptyAction() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("", (req, acc) -> Response.ok(Collections.emptyMap())));
    }

    @Test
    void testRegisterNullHandler() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("action", null));
    }

    @Test
    void testDispatchMultipleHandlers() {
        registry.register("echo", (request, accessor) ->
                Response.ok(Collections.singletonMap("action", "echo")));

        Response pingResp = registry.dispatch(new Request("ping", Collections.emptyMap()), mockAccessor);
        Response echoResp = registry.dispatch(new Request("echo", Collections.emptyMap()), mockAccessor);

        assertTrue(pingResp.isOk());
        assertEquals("0.1.0", pingResp.getData().get("version"));
        assertTrue(echoResp.isOk());
        assertEquals("echo", echoResp.getData().get("action"));
    }

    @Test
    void testHasHandler() {
        assertTrue(registry.hasHandler("ping"));
        assertFalse(registry.hasHandler("nonexistent"));
    }

    @Test
    void testGetHandlersReturnsAllRegistered() {
        registry.register("echo", (req, acc) -> Response.ok(Collections.emptyMap()));
        Map<String, CommandHandler> handlers = registry.getHandlers();
        assertEquals(2, handlers.size());
        assertTrue(handlers.containsKey("ping"));
        assertTrue(handlers.containsKey("echo"));
    }

    @Test
    void testGetHandlersIsUnmodifiable() {
        Map<String, CommandHandler> handlers = registry.getHandlers();
        assertThrows(UnsupportedOperationException.class,
                () -> handlers.put("hack", (req, acc) -> Response.ok(Collections.emptyMap())));
    }

    // ==================== Unknown parameter warnings ====================

    @Test
    @SuppressWarnings("unchecked")
    void testDispatchAddsWarningsForUnknownParams() {
        // Handler that reads only "name" param
        registry.register("greet", (request, accessor) -> {
            String name = request.getString("name");
            return Response.ok(Collections.singletonMap("greeting", "Hello " + name));
        });

        // Call with "name" + unknown "extra" param
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "World");
        params.put("extra", "unknown");
        Request req = new Request("greet", params);
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isOk());
        assertEquals("Hello World", resp.getData().get("greeting"));
        assertTrue(resp.getData().containsKey("_warnings"));
        List<String> warnings = (List<String>) resp.getData().get("_warnings");
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.get(0).contains("extra"));
    }

    @Test
    void testDispatchNoWarningsWhenAllParamsRecognized() {
        registry.register("greet", (request, accessor) -> {
            request.getString("name");
            return Response.ok(Collections.singletonMap("greeting", "Hello"));
        });

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "World");
        Request req = new Request("greet", params);
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isOk());
        assertFalse(resp.getData().containsKey("_warnings"));
    }

    @Test
    void testDispatchNoWarningsOnErrorResponse() {
        registry.register("fail", (request, accessor) ->
                Response.error("Something went wrong"));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("extra", "unknown");
        Request req = new Request("fail", params);
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isError());
        // Error responses should not have _warnings
        assertNull(resp.getData());
    }

    @Test
    void testDispatchNoWarningsWhenNoParams() {
        Request req = new Request("ping", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isOk());
        assertFalse(resp.getData().containsKey("_warnings"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDispatchWarningsWithGetParamsTracking() {
        // Handler uses getParams().get() directly
        registry.register("direct", (request, accessor) -> {
            Object val = request.getParams().get("known");
            return Response.ok(Collections.singletonMap("val", val));
        });

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("known", "yes");
        params.put("unknown", "no");
        Request req = new Request("direct", params);
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isOk());
        assertTrue(resp.getData().containsKey("_warnings"));
        List<String> warnings = (List<String>) resp.getData().get("_warnings");
        // The warning should only contain the "unknown" param, not "known"
        assertEquals("Unknown parameters ignored: [unknown]", warnings.get(0));
    }
}
