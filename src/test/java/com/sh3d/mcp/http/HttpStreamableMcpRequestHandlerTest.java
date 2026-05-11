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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpStreamableMcpRequestHandlerTest {

    private CommandRegistry commandRegistry;
    private HomeAccessor mockAccessor;
    private HttpStreamableMcpRequestHandler handler;

    @BeforeEach
    void setUp() {
        commandRegistry = new CommandRegistry();
        mockAccessor = mock(HomeAccessor.class);
        handler = new HttpStreamableMcpRequestHandler(commandRegistry, mockAccessor);
    }

    @Test
    void testInitializeReturns200WithSessionId() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\"}}";

        HttpExchange exchange = createPostExchange(body, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(200, responseBody.size());
        String sessionId = exchange.getResponseHeaders().getFirst("Mcp-Session-Id");
        assertNotNull(sessionId, "initialize should return a streamable session id");
        assertFalse(sessionId.isEmpty());
        assertTrue(responseBody.toString(StandardCharsets.UTF_8.name())
                .contains("\"protocolVersion\":\"2025-03-26\""));
    }

    @Test
    void testBatchNotificationsReturn202() throws Exception {
        String sessionId = initializeSession();
        String body = "[{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]";

        HttpExchange exchange = createPostExchange(body, sessionId);
        captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(202, -1);
    }

    @Test
    void testBatchRequestsReturnJsonArray() throws Exception {
        registerTestTool("get_state", "Get scene state", null);
        String sessionId = initializeSession();
        String body = "["
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"
                + "]";

        HttpExchange exchange = createPostExchange(body, sessionId);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(200, responseBody.size());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.startsWith("["));
        assertTrue(response.contains("\"id\":1"));
        assertTrue(response.contains("\"id\":2"));
        assertTrue(response.contains("\"tools\""));
    }

    @Test
    void testToolsListUnknownSessionReturns404() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";

        HttpExchange exchange = createPostExchange(body, "never-seen-session-id");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(404, responseBody.size());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Unknown or expired session"));
    }

    private String initializeSession() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\"}}";
        HttpExchange exchange = createPostExchange(body, null);
        captureResponseBody(exchange);

        handler.handle(exchange);

        return exchange.getResponseHeaders().getFirst("Mcp-Session-Id");
    }

    private HttpExchange createPostExchange(String body, String sessionId) {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("POST");

        Headers requestHeaders = new Headers();
        if (sessionId != null) {
            requestHeaders.set("Mcp-Session-Id", sessionId);
        }
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        InputStream bodyStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        when(exchange.getRequestBody()).thenReturn(bodyStream);
        return exchange;
    }

    private ByteArrayOutputStream captureResponseBody(HttpExchange exchange) {
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);
        return responseBody;
    }

    private void registerTestTool(String action, String description, String toolName) {
        class TestDescriptorHandler implements CommandHandler, CommandDescriptor {
            @Override
            public Response execute(Request request, HomeAccessor accessor) {
                return Response.ok(Collections.singletonMap("status", "done"));
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
