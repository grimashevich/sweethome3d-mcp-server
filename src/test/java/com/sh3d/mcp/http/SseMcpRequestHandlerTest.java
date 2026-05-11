package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SseMcpRequestHandlerTest {

    private CommandRegistry commandRegistry;
    private HomeAccessor mockAccessor;
    private SseMcpRequestHandler handler;

    @BeforeEach
    void setUp() {
        commandRegistry = new CommandRegistry();
        mockAccessor = mock(HomeAccessor.class);
        handler = new SseMcpRequestHandler(commandRegistry, mockAccessor);
    }

    // === POST initialize ===

    @Test
    void testInitializeReturns200WithSessionId() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\"}}";

        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        Headers respHeaders = exchange.getResponseHeaders();
        String sessionId = respHeaders.getFirst("Mcp-Session-Id");
        assertNotNull(sessionId, "Response should contain Mcp-Session-Id header");
        assertFalse(sessionId.isEmpty());

        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(response.contains("\"protocolVersion\":\"2025-03-26\""));
        assertTrue(response.contains("\"capabilities\""));
        assertTrue(response.contains("\"serverInfo\""));
    }

    // === POST notifications/initialized ===

    @Test
    void testNotificationsInitializedReturns202() throws Exception {
        // First, get a session via initialize
        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(202, -1);
    }

    // === POST tools/list ===

    @Test
    void testToolsListWithValidSession() throws Exception {
        // Register a tool with descriptor
        registerTestTool("create_wall", "Create a wall", null);

        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"tools\""));
        assertTrue(response.contains("\"create_wall\""));
        assertTrue(response.contains("\"Create a wall\""));
    }

    @Test
    void testToolsListWithoutSessionReturns400() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Missing Mcp-Session-Id header"));
    }

    @Test
    void testToolsListWithUnknownSessionReturns400() throws Exception {
        registerTestTool("create_wall", "Create a wall", null);

        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        HttpExchange exchange = createPostExchange(body, "nonexistent-session-id", null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        // Truly unknown session ID (never created) is rejected with 400
        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Unknown session"));
    }

    @Test
    void testToolsListWithExpiredSessionAutoRecreatesSession() throws Exception {
        registerTestTool("create_wall", "Create a wall", null);

        // Initialize and delete a session so its ID becomes known-expired
        String sessionId = initializeSession();
        HttpExchange deleteExchange = createExchange("DELETE", null, sessionId, null);
        handler.handle(deleteExchange);

        // Now use the deleted session ID — should be auto-recreated (200, not 404)
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"tools\""));
        assertTrue(response.contains("\"create_wall\""));
    }

    // === POST tools/call ===

    @Test
    void testToolsCallRegisteredTool() throws Exception {
        // Register a tool that returns data
        commandRegistry.register("get_state", (req, acc) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("walls", 5);
            return Response.ok(data);
        });

        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_state\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"isError\":false"));
        assertTrue(response.contains("walls"));
    }

    @Test
    void testToolsCallUnknownTool() throws Exception {
        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nonexistent_tool\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Unknown tool: nonexistent_tool"));
    }

    @Test
    void testToolsCallWithToolNameFromDescriptor() throws Exception {
        // Register with action "create_walls" but toolName "create_room"
        registerTestTool("create_walls", "Create room walls", "create_room");

        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"create_room\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"isError\":false"));
    }

    @Test
    void testToolsCallWithUnknownSessionReturns400() throws Exception {
        commandRegistry.register("get_state", (req, acc) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("walls", 0);
            return Response.ok(data);
        });

        String body = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_state\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, "never-seen-session-id", null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        // Truly unknown session ID (never created) is rejected with 400
        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Unknown session"));
    }

    @Test
    void testToolsCallWithKnownExpiredSessionAutoRecreatesWithWarning() throws Exception {
        commandRegistry.register("get_state", (req, acc) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("walls", 0);
            return Response.ok(data);
        });

        // Initialize and delete a session so its ID becomes known-expired
        String sessionId = initializeSession();
        HttpExchange deleteExchange = createExchange("DELETE", null, sessionId, null);
        handler.handle(deleteExchange);

        // Now use the deleted (known-expired) session ID — should auto-recreate with warning
        String body = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_state\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("WARNING: MCP session was auto-recreated"));
        assertTrue(response.contains("get_state"));
    }

    @Test
    void testToolsCallWithoutSessionReturns400() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_state\"}}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void testToolsCallMissingNameInParams() throws Exception {
        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Missing 'name'"));
    }

    // === POST with missing/invalid data ===

    @Test
    void testPostMissingMethodReturns400() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Missing 'method' field"));
    }

    @Test
    void testPostEmptyBodyReturns400() throws Exception {
        HttpExchange exchange = createPostExchange("", null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Empty request body"));
    }

    @Test
    void testPostInvalidJsonReturns400() throws Exception {
        HttpExchange exchange = createPostExchange("{invalid json", null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Invalid JSON"));
    }

    @Test
    void testPostUnknownMethodReturnsError() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown/method\"}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Unknown method: unknown/method"));
    }

    // === POST ping ===

    @Test
    void testPostPingReturns200() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"id\":99"));
        assertTrue(response.contains("\"result\":{}"));
    }

    // === DELETE ===

    @Test
    void testDeleteWithSessionRemovesIt() throws Exception {
        String sessionId = initializeSession();

        HttpExchange exchange = createExchange("DELETE", null, sessionId, null);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(200, -1);

        // After deletion, the session ID is remembered as known-expired.
        // Using it again triggers auto-recreate (200, not 404).
        String body = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/list\"}";
        HttpExchange exchange2 = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody2 = captureResponseBody(exchange2);

        handler.handle(exchange2);

        verify(exchange2).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testDeleteWithoutSessionReturns200() throws Exception {
        HttpExchange exchange = createExchange("DELETE", null, null, null);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(200, -1);
    }

    // === GET ===

    @Test
    void testGetReturns405() throws Exception {
        HttpExchange exchange = createExchange("GET", null, null, null);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(405, -1);
    }

    // === Unsupported HTTP method ===

    @Test
    void testPutReturns405() throws Exception {
        HttpExchange exchange = createExchange("PUT", null, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(405), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Method not allowed"));
    }

    // === Body size limit ===

    @Test
    void testBodyExceedingLimitReturns413() throws Exception {
        // Create a body larger than MAX_REQUEST_BODY_SIZE
        int limit = SseMcpRequestHandler.MAX_REQUEST_BODY_SIZE;
        char[] bigChars = new char[limit + 100];
        java.util.Arrays.fill(bigChars, 'x');
        String bigBody = new String(bigChars);

        HttpExchange exchange = createPostExchange(bigBody, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(413), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("too large"));
    }

    @Test
    void testBodyAtExactLimitIsAccepted() throws Exception {
        // A body exactly at the limit should be accepted (and then fail on invalid JSON)
        int limit = SseMcpRequestHandler.MAX_REQUEST_BODY_SIZE;
        char[] exactChars = new char[limit];
        java.util.Arrays.fill(exactChars, '{');
        String exactBody = new String(exactChars);

        HttpExchange exchange = createPostExchange(exactBody, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        // Should NOT be 413 — body is within limit, but will fail JSON parsing (400)
        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Invalid JSON"));
    }

    @Test
    void testMaxRequestBodySizeIs10MB() {
        assertEquals(10 * 1024 * 1024, SseMcpRequestHandler.MAX_REQUEST_BODY_SIZE);
    }

    // === Origin validation ===

    @Test
    void testNullOriginAllowed() throws Exception {
        // No Origin header -- should be allowed (curl, non-browser)
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testLocalhostOriginAllowed() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "http://localhost:3000");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testLocalhostIpOriginAllowed() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "http://127.0.0.1:8080");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testHttpsLocalhostOriginAllowed() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "https://localhost");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testForeignOriginReturns403() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "https://evil.example.com");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(403), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Forbidden origin"));
    }

    @Test
    void testLocalhostSubdomainOriginBlocked() throws Exception {
        // http://localhost.evil.com should NOT be allowed (DNS rebinding attack)
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "http://localhost.evil.com");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(403), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Forbidden origin"));
    }

    @Test
    void testLocalhostIpSubdomainOriginBlocked() throws Exception {
        // http://127.0.0.1.evil.com should NOT be allowed
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "http://127.0.0.1.evil.com");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(403), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Forbidden origin"));
    }

    @Test
    void testLocalhostExactOriginAllowed() throws Exception {
        // http://localhost (without port) should be allowed
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "http://localhost");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testLocalhostWithPathOriginAllowed() throws Exception {
        // http://localhost/ should be allowed
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "http://localhost/");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testForeignOriginBlocksAllMethods() throws Exception {
        // DELETE with foreign origin should also be blocked
        HttpExchange exchange = createExchange("DELETE", null, null, "https://evil.example.com");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(403), anyLong());
    }

    // === Helpers ===

    /**
     * Performs an initialize handshake and returns the session ID.
     */
    private String initializeSession() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\"}}";
        HttpExchange exchange = createPostExchange(body, null, null);
        captureResponseBody(exchange);

        handler.handle(exchange);

        return exchange.getResponseHeaders().getFirst("Mcp-Session-Id");
    }

    /**
     * Creates a mock HttpExchange for POST requests.
     */
    private HttpExchange createPostExchange(String body, String sessionId, String origin) {
        return createExchange("POST", body, sessionId, origin);
    }

    /**
     * Creates a mock HttpExchange with the specified HTTP method, body, session ID, and origin.
     */
    private HttpExchange createExchange(String method, String body, String sessionId, String origin) {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn(method);

        Headers requestHeaders = new Headers();
        if (sessionId != null) {
            requestHeaders.set("Mcp-Session-Id", sessionId);
        }
        if (origin != null) {
            requestHeaders.set("Origin", origin);
        }
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        // Response headers -- use a real Headers object to capture values
        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        if (body != null) {
            InputStream bodyStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            when(exchange.getRequestBody()).thenReturn(bodyStream);
        } else {
            InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
            when(exchange.getRequestBody()).thenReturn(emptyStream);
        }

        return exchange;
    }

    /**
     * Captures the response body written via exchange.getResponseBody().
     */
    private ByteArrayOutputStream captureResponseBody(HttpExchange exchange) {
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);
        return responseBody;
    }

    /**
     * Registers a test tool with CommandDescriptor in the registry.
     */
    private void registerTestTool(String action, String description, String toolName) {
        CommandHandler testHandler = new CommandHandler() {
            @Override
            public Response execute(Request request, HomeAccessor accessor) {
                return Response.ok(Collections.singletonMap("status", "done"));
            }
        };

        // Wrap in a class implementing both interfaces
        class TestDescriptorHandler implements CommandHandler, CommandDescriptor {
            @Override
            public Response execute(Request request, HomeAccessor accessor) {
                return testHandler.execute(request, accessor);
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Map<String, Object> getSchema() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                schema.put("properties", new LinkedHashMap<>());
                return schema;
            }

            @Override
            public String getToolName() {
                return toolName;
            }
        }

        commandRegistry.register(action, new TestDescriptorHandler());
    }
}
