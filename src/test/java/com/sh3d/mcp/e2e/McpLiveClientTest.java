package com.sh3d.mcp.e2e;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка клиента живых тестов на подставном сервере, без Sweet Home 3D: какие заголовки он шлёт
 * (сессия, токен) и замечает ли он негодный ответ. Настоящий сервер токен пока не проверяет,
 * поэтому иначе отправку заголовка {@code Authorization} проверить нечем.
 */
class McpLiveClientTest {

    /** Один принятый запрос: метод HTTP, заголовки, тело. */
    private static final class Seen {
        final String httpMethod;
        final String authorization;
        final String sessionId;
        final String accept;
        final String body;

        Seen(HttpExchange exchange, String body) {
            this.httpMethod = exchange.getRequestMethod();
            this.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            this.sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            this.accept = exchange.getRequestHeaders().getFirst("Accept");
            this.body = body;
        }
    }

    private HttpServer server;
    private final List<Seen> seen = Collections.synchronizedList(new ArrayList<>());

    /** Подмена ответа на tools/call: статус и тело; {@code null} — обычный успешный ответ. */
    private volatile Integer toolCallStatus;
    private volatile String toolCallBody;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @SuppressWarnings("unchecked")
    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        seen.add(new Seen(exchange, body));
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        Map<String, Object> request = (Map<String, Object>) Json.parse(body);
        String method = (String) request.get("method");
        Object id = request.get("id");
        if ("notifications/initialized".equals(method)) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        int status = 200;
        String reply;
        if ("initialize".equals(method)) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", "stub-session");
            reply = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"protocolVersion\":\"2025-03-26\"}}";
        } else if (toolCallBody != null) {
            status = toolCallStatus;
            reply = toolCallBody.replace("$ID", String.valueOf(id));
        } else {
            reply = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"isError\":false,"
                    + "\"content\":[{\"type\":\"text\",\"text\":\"{\\\"ok\\\":true}\"}]}}";
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private McpLiveClient client(String token) {
        return new McpLiveClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"), token);
    }

    @Test
    void sendsBearerTokenAndSessionOnEveryRequest() {
        McpLiveClient client = client("s3cret");

        client.initialize();
        Map<String, Object> data = client.call("get_state");
        client.close();

        assertEquals(Boolean.TRUE, data.get("ok"));
        assertEquals(4, seen.size(), "initialize, notifications/initialized, tools/call, DELETE");
        for (Seen request : seen) {
            assertEquals("Bearer s3cret", request.authorization, request.httpMethod + " " + request.body);
        }
        assertNull(seen.get(0).sessionId, "initialize is sent before a session exists");
        assertEquals("stub-session", seen.get(1).sessionId, "notifications/initialized carries the session");
        assertEquals("stub-session", seen.get(2).sessionId, "tools/call carries the session");
        assertEquals("DELETE", seen.get(3).httpMethod);
        assertEquals("stub-session", seen.get(3).sessionId, "DELETE names the session to close");
        assertTrue(seen.get(2).accept.contains("application/json") && seen.get(2).accept.contains("text/event-stream"),
                "Accept must list both response types of Streamable HTTP: " + seen.get(2).accept);
        assertTrue(seen.get(2).body.contains("\"name\":\"get_state\""), seen.get(2).body);
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutToken() {
        McpLiveClient client = client(null);

        client.initialize();
        client.call("get_state");
        client.close();

        assertFalse(seen.isEmpty());
        for (Seen request : seen) {
            assertNull(request.authorization, request.httpMethod + " " + request.body);
        }
    }

    @Test
    void readsTokenFromFileNamedBySystemProperty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("token");
        Files.write(file, "  tok-123\n".getBytes(StandardCharsets.UTF_8));
        String previous = System.getProperty("sh3d.e2e.tokenFile");
        try {
            System.clearProperty("sh3d.e2e.tokenFile");
            assertNull(LiveTestBase.bearerToken(), "no property — no token");

            System.setProperty("sh3d.e2e.tokenFile", file.toString());
            assertEquals("tok-123", LiveTestBase.bearerToken(), "the token is the trimmed file content");

            System.setProperty("sh3d.e2e.tokenFile", dir.resolve("missing").toString());
            assertThrows(IllegalStateException.class, LiveTestBase::bearerToken, "a missing token file is an error");
        } finally {
            if (previous == null) {
                System.clearProperty("sh3d.e2e.tokenFile");
            } else {
                System.setProperty("sh3d.e2e.tokenFile", previous);
            }
        }
    }

    @Test
    void commandErrorIsAResultButCallRefusesIt() {
        McpLiveClient client = client(null);
        client.initialize();
        toolCallStatus = 200;
        toolCallBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":{\"isError\":true,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"Wall not found\"}]}}";

        ToolResult result = client.callTool("delete_wall", McpLiveClient.args("id", "x"));
        assertTrue(result.isError());
        assertEquals("Wall not found", result.text());

        AssertionError error = assertThrows(AssertionError.class, () -> client.call("delete_wall", "id", "x"));
        assertTrue(error.getMessage().contains("Wall not found"), error.getMessage());
    }

    @Test
    void rejectsBrokenTransportAnswers() {
        McpLiveClient client = client(null);
        client.initialize();

        toolCallStatus = 401;
        toolCallBody = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"Unauthorized\"}}";
        assertThrows(AssertionError.class, () -> client.callTool("get_state", null), "HTTP 401");

        toolCallStatus = 200;
        toolCallBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":{\"isError\":false,\"content\":[NaN]}}";
        assertThrows(AssertionError.class, () -> client.callTool("get_state", null), "invalid JSON");

        toolCallBody = "{\"jsonrpc\":\"2.0\",\"id\":999999,\"result\":{\"isError\":false,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"{}\"}]}}";
        assertThrows(AssertionError.class, () -> client.callTool("get_state", null), "id not echoed");

        toolCallBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"error\":{\"code\":-32601,\"message\":\"Unknown tool\"}}";
        assertThrows(AssertionError.class, () -> client.callTool("get_state", null), "JSON-RPC error");

        toolCallBody = "{\"jsonrpc\":\"2.0\",\"id\":$ID,\"result\":{\"content\":[]}}";
        assertThrows(AssertionError.class, () -> client.callTool("get_state", null), "no isError, empty content");
    }
}
