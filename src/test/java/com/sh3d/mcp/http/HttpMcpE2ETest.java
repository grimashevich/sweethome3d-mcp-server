package com.sh3d.mcp.http;

import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.command.handler.GetStateHandler;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.protocol.JsonUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end HTTP test for the full MCP lifecycle via java.net.http.HttpClient.
 * <p>
 * Spins up a real {@link HttpMcpServer} on an ephemeral port with a real
 * {@link CommandRegistry} (containing {@code get_state}) and a real
 * {@link HomeAccessor} backed by an empty {@link Home}.
 * <p>
 * Full cycle: initialize -> tools/list -> tools/call(get_state) -> DELETE.
 */
class HttpMcpE2ETest {

    private HttpMcpServer server;
    private HttpClient httpClient;
    private int port;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();

        PluginConfig config = mock(PluginConfig.class);
        when(config.getPort()).thenReturn(port);

        Home home = new Home();
        HomeAccessor accessor = new HomeAccessor(home, null);

        CommandRegistry registry = new CommandRegistry();
        registry.register("get_state", new GetStateHandler());

        server = new HttpMcpServer(config, registry, accessor);
        server.start();

        // Wait for server to reach RUNNING state
        for (int i = 0; i < 100; i++) {
            if (server.isRunning()) break;
            Thread.sleep(50);
        }
        assertTrue(server.isRunning(), "Server should be RUNNING on port " + port);

        baseUrl = "http://127.0.0.1:" + port + "/mcp";

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    // === Full MCP Lifecycle ===

    @Test
    @SuppressWarnings("unchecked")
    void testFullMcpLifecycle() throws Exception {
        // --- Step 1: initialize ---
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                + "\"capabilities\":{},\"clientInfo\":{\"name\":\"e2e-test\",\"version\":\"1.0\"}}}";

        HttpResponse<String> initResponse = post(initBody, null);

        assertEquals(200, initResponse.statusCode(), "initialize should return 200");
        assertContentTypeJson(initResponse);

        // Extract Mcp-Session-Id header
        String sessionId = initResponse.headers()
                .firstValue("Mcp-Session-Id")
                .orElse(null);
        assertNotNull(sessionId, "initialize response must contain Mcp-Session-Id header");
        assertFalse(sessionId.isEmpty(), "Mcp-Session-Id must not be empty");

        // Parse and validate JSON-RPC response
        Map<String, Object> initResult = parseJsonRpc(initResponse.body());
        assertNoError(initResult);
        assertEquals(1L, toLong(initResult.get("id")), "id should match request id");

        Map<String, Object> result = (Map<String, Object>) initResult.get("result");
        assertNotNull(result, "initialize result must be present");
        assertEquals("2025-03-26", result.get("protocolVersion"));

        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertNotNull(serverInfo, "serverInfo must be present");
        assertEquals("sweethome3d", serverInfo.get("name"));
        assertNotNull(serverInfo.get("version"), "serverInfo.version must be present");

        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        assertNotNull(capabilities, "capabilities must be present");

        // --- Step 2: tools/list ---
        String toolsListBody = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";

        HttpResponse<String> toolsListResponse = post(toolsListBody, sessionId);

        assertEquals(200, toolsListResponse.statusCode(), "tools/list should return 200");
        assertContentTypeJson(toolsListResponse);

        Map<String, Object> toolsListResult = parseJsonRpc(toolsListResponse.body());
        assertNoError(toolsListResult);
        assertEquals(2L, toLong(toolsListResult.get("id")));

        Map<String, Object> toolsResult = (Map<String, Object>) toolsListResult.get("result");
        assertNotNull(toolsResult, "tools/list result must be present");

        List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsResult.get("tools");
        assertNotNull(tools, "tools array must be present");
        assertFalse(tools.isEmpty(), "tools array must not be empty");

        // Find get_state tool
        Map<String, Object> getStateTool = null;
        for (Map<String, Object> tool : tools) {
            if ("get_state".equals(tool.get("name"))) {
                getStateTool = tool;
                break;
            }
        }
        assertNotNull(getStateTool, "tools/list must contain get_state tool");
        assertNotNull(getStateTool.get("description"), "get_state tool must have description");
        assertNotNull(getStateTool.get("inputSchema"), "get_state tool must have inputSchema");

        // Validate inputSchema structure
        Map<String, Object> inputSchema = (Map<String, Object>) getStateTool.get("inputSchema");
        assertEquals("object", inputSchema.get("type"), "inputSchema type must be 'object'");

        // --- Step 3: tools/call(get_state) ---
        String toolsCallBody = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_state\",\"arguments\":{}}}";

        HttpResponse<String> toolsCallResponse = post(toolsCallBody, sessionId);

        assertEquals(200, toolsCallResponse.statusCode(), "tools/call should return 200");
        assertContentTypeJson(toolsCallResponse);

        Map<String, Object> toolsCallResult = parseJsonRpc(toolsCallResponse.body());
        assertNoError(toolsCallResult);
        assertEquals(3L, toLong(toolsCallResult.get("id")));

        Map<String, Object> callResult = (Map<String, Object>) toolsCallResult.get("result");
        assertNotNull(callResult, "tools/call result must be present");

        // isError should be false
        Object isError = callResult.get("isError");
        assertNotNull(isError, "isError field must be present");
        assertEquals(Boolean.FALSE, isError, "isError should be false for successful get_state");

        // content array
        List<Map<String, Object>> content = (List<Map<String, Object>>) callResult.get("content");
        assertNotNull(content, "content array must be present");
        assertFalse(content.isEmpty(), "content array must not be empty");

        // First content block should be text type
        Map<String, Object> contentBlock = content.get(0);
        assertEquals("text", contentBlock.get("type"), "content[0].type must be 'text'");

        String textValue = (String) contentBlock.get("text");
        assertNotNull(textValue, "content[0].text must not be null");

        // The text should be valid JSON (parseable)
        Object parsedState = JsonUtil.parse(textValue);
        assertNotNull(parsedState, "content[0].text must be valid JSON");
        assertTrue(parsedState instanceof Map, "content[0].text must parse to a Map");

        // Verify scene state structure (empty Home)
        Map<String, Object> sceneState = (Map<String, Object>) parsedState;
        assertEquals(0L, toLong(sceneState.get("wallCount")), "empty home should have 0 walls");
        assertEquals(0L, toLong(sceneState.get("furnitureCount")), "empty home should have 0 furniture");
        assertEquals(0L, toLong(sceneState.get("roomCount")), "empty home should have 0 rooms");
        assertNotNull(sceneState.get("camera"), "camera info must be present");

        // --- Step 4: DELETE (session cleanup) ---
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Mcp-Session-Id", sessionId)
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse = httpClient.send(deleteRequest,
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, deleteResponse.statusCode(), "DELETE should return 200");
    }

    // === Additional E2E edge-case tests ===

    @Test
    void testToolsListWithoutSessionReturns400() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

        HttpResponse<String> response = post(body, null);

        assertEquals(400, response.statusCode(),
                "tools/list without Mcp-Session-Id should return 400");
        assertContentTypeJson(response);
        assertTrue(response.body().contains("Missing Mcp-Session-Id"),
                "Error should mention missing session header");
    }

    @Test
    void testPingWithoutSession() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"ping\"}";

        HttpResponse<String> response = post(body, null);

        assertEquals(200, response.statusCode(), "ping should return 200");
        assertContentTypeJson(response);
        assertTrue(response.body().contains("\"result\":{}"),
                "ping result should be empty object");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testToolsCallUnknownToolReturnsError() throws Exception {
        // Initialize first
        String sessionId = initializeAndGetSessionId();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nonexistent_tool\",\"arguments\":{}}}";

        HttpResponse<String> response = post(body, sessionId);

        assertEquals(200, response.statusCode());
        Map<String, Object> parsed = parseJsonRpc(response.body());
        assertNotNull(parsed.get("error"), "Response should contain error for unknown tool");
        Map<String, Object> error = (Map<String, Object>) parsed.get("error");
        assertTrue(error.get("message").toString().contains("Unknown tool"),
                "Error message should mention unknown tool");
    }

    @Test
    void testDeleteWithoutSessionReturns200() throws Exception {
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(deleteRequest,
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "DELETE without session should still return 200");
    }

    @Test
    void testHealthEndpoint() throws Exception {
        String healthUrl = "http://127.0.0.1:" + port + "/health";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "health endpoint should return 200");
        assertTrue(response.body().contains("\"status\":\"ok\""),
                "health response should contain status ok");
    }

    @Test
    void testInvalidJsonReturns400() throws Exception {
        HttpResponse<String> response = post("{not valid json}", null);

        assertEquals(400, response.statusCode(), "Invalid JSON should return 400");
        assertContentTypeJson(response);
        assertTrue(response.body().contains("Invalid JSON"),
                "Error should mention invalid JSON");
    }

    @Test
    void testEmptyBodyReturns400() throws Exception {
        HttpResponse<String> response = post("", null);

        assertEquals(400, response.statusCode(), "Empty body should return 400");
        assertTrue(response.body().contains("Empty request body"),
                "Error should mention empty body");
    }

    // === Helper methods ===

    private HttpResponse<String> post(String body, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10));

        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private String initializeAndGetSessionId() throws Exception {
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\"}}";

        HttpResponse<String> response = post(initBody, null);
        assertEquals(200, response.statusCode());

        return response.headers()
                .firstValue("Mcp-Session-Id")
                .orElseThrow(() -> new AssertionError("Missing Mcp-Session-Id header"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonRpc(String json) {
        Object parsed = JsonUtil.parse(json);
        assertTrue(parsed instanceof Map, "JSON-RPC response must be an object");
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals("2.0", map.get("jsonrpc"), "jsonrpc version must be 2.0");
        return map;
    }

    private void assertNoError(Map<String, Object> jsonRpc) {
        assertNull(jsonRpc.get("error"),
                "JSON-RPC response should not contain error field, got: " + jsonRpc.get("error"));
        assertNotNull(jsonRpc.get("result"), "JSON-RPC response must contain result field");
    }

    private void assertContentTypeJson(HttpResponse<String> response) {
        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("");
        assertTrue(contentType.contains("application/json"),
                "Content-Type should contain application/json, got: " + contentType);
    }

    /**
     * Converts a parsed JSON number (Long or Double) to a long for comparison.
     * JsonUtil parses integers as Long, but this handles both cases.
     */
    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new AssertionError("Expected numeric value, got: " + value
                + " (" + (value == null ? "null" : value.getClass().getName()) + ")");
    }

    private static int findFreePort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Cannot find free port", e);
        }
    }
}
