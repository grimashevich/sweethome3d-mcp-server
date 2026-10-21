package com.sh3d.mcp.e2e;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Клиент MCP (Streamable HTTP, JSON-RPC 2.0) для живых тестов: {@code initialize} →
 * {@code Mcp-Session-Id} → {@code tools/list} / {@code tools/call}.
 * <p>
 * Ведёт себя как сторонний клиент: шлёт заголовки, которые требует протокол, и проверяет
 * транспортную часть каждого ответа (HTTP 200, JSON, эхо {@code id}). Если задан токен, каждый
 * запрос несёт {@code Authorization: Bearer <токен>}.
 */
final class McpLiveClient {

    static final String PROTOCOL_VERSION = "2025-03-26";
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final URI endpoint;
    private final String bearerToken;
    private final HttpClient http;
    private String sessionId;
    private long nextId = 1;

    /**
     * @param endpoint    адрес {@code /mcp}
     * @param bearerToken токен для заголовка {@code Authorization} или {@code null}, если сервер его не требует
     */
    McpLiveClient(URI endpoint, String bearerToken) {
        this.endpoint = endpoint;
        this.bearerToken = bearerToken;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Собирает аргументы команды из пар «имя, значение», сохраняя порядок. */
    static Map<String, Object> args(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("args() expects name/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    String sessionId() {
        return sessionId;
    }

    /**
     * Рукопожатие: {@code initialize}, запоминание {@code Mcp-Session-Id}, уведомление
     * {@code notifications/initialized}.
     *
     * @return поле {@code result} ответа на {@code initialize}
     */
    Map<String, Object> initialize() {
        Map<String, Object> params = args(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", new LinkedHashMap<String, Object>(),
                "clientInfo", args("name", "sh3d-mcp-e2e", "version", "1"));
        long id = nextId++;
        HttpResponse<String> response = post(Json.write(envelope(id, "initialize", params)), DEFAULT_TIMEOUT);
        Map<String, Object> reply = parseReply("initialize", id, response);

        sessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null);
        if (sessionId == null || sessionId.isEmpty()) {
            throw new AssertionError("initialize: the response has no Mcp-Session-Id header");
        }

        // Уведомление без id: по протоколу ответ — 202 без тела
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        HttpResponse<String> ack = post(Json.write(notification), DEFAULT_TIMEOUT);
        if (ack.statusCode() != 202) {
            throw new AssertionError("notifications/initialized: expected HTTP 202, got "
                    + ack.statusCode() + " " + ack.body());
        }
        return resultOf("initialize", reply);
    }

    /** Запрос JSON-RPC, от которого ждут успешный ответ; возвращает поле {@code result}. */
    Map<String, Object> request(String method, Map<String, Object> params) {
        return request(method, params, DEFAULT_TIMEOUT);
    }

    Map<String, Object> request(String method, Map<String, Object> params, Duration timeout) {
        long id = nextId++;
        HttpResponse<String> response = post(Json.write(envelope(id, method, params)), timeout);
        return resultOf(method, parseReply(method, id, response));
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> listTools() {
        Object tools = request("tools/list", null).get("tools");
        if (!(tools instanceof List)) {
            throw new AssertionError("tools/list: 'tools' is not an array: " + tools);
        }
        return (List<Map<String, Object>>) tools;
    }

    ToolResult callTool(String name, Map<String, Object> arguments) {
        return callTool(name, arguments, DEFAULT_TIMEOUT);
    }

    /**
     * Вызов команды. Ошибка команды ({@code isError: true}) — обычный результат, а не исключение.
     *
     * @param timeout жёсткий предел ожидания ответа; по истечении тест падает
     */
    @SuppressWarnings("unchecked")
    ToolResult callTool(String name, Map<String, Object> arguments, Duration timeout) {
        Map<String, Object> params = args(
                "name", name,
                "arguments", arguments != null ? arguments : new LinkedHashMap<String, Object>());
        Map<String, Object> result = request("tools/call", params, timeout);

        Object isError = result.get("isError");
        if (!(isError instanceof Boolean)) {
            throw new AssertionError(name + ": 'isError' must be a boolean, got: " + isError);
        }
        Object content = result.get("content");
        if (!(content instanceof List) || ((List<?>) content).isEmpty()) {
            throw new AssertionError(name + ": 'content' must be a non-empty array, got: " + content);
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Object block : (List<Object>) content) {
            if (!(block instanceof Map)) {
                throw new AssertionError(name + ": a content block is not an object: " + block);
            }
            blocks.add((Map<String, Object>) block);
        }
        return new ToolResult(name, (Boolean) isError, blocks);
    }

    /** Вызов команды, от которой ждут успех; возвращает её данные (JSON-объект из текстового блока). */
    Map<String, Object> call(String name, Object... keysAndValues) {
        ToolResult result = callTool(name, args(keysAndValues));
        if (result.isError()) {
            throw new AssertionError("Command failed: " + result);
        }
        return result.json();
    }

    /** Завершает сессию ({@code DELETE /mcp}). Ошибки не бросает: это уборка после теста. */
    void close() {
        if (sessionId == null) {
            return;
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Mcp-Session-Id", sessionId)
                    .DELETE();
            authorize(builder);
            http.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // сервер мог уже остановиться
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sessionId = null;
        }
    }

    /** Сырой POST на {@code /mcp} с заголовками протокола; для проверок транспортного уровня. */
    HttpResponse<String> post(String body, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        authorize(builder);
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new NoAnswerError("No answer from " + endpoint + " (time limit " + timeout.getSeconds()
                    + " s; is Sweet Home 3D still running?): " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + endpoint, e);
        }
    }

    /** Сервер не ответил вовсе: соединение не установлено, оборвано или истекло время ожидания. */
    static final class NoAnswerError extends AssertionError {
        private static final long serialVersionUID = 1L;

        NoAnswerError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void authorize(HttpRequest.Builder builder) {
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
    }

    private static Map<String, Object> envelope(long id, String method, Map<String, Object> params) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("method", method);
        if (params != null) {
            envelope.put("params", params);
        }
        return envelope;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseReply(String method, long id, HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new AssertionError(method + ": expected HTTP 200, got " + response.statusCode()
                    + " " + response.body());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.startsWith("application/json")) {
            throw new AssertionError(method + ": expected an application/json response, got '" + contentType + "'");
        }
        Object parsed;
        try {
            parsed = Json.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new AssertionError(method + ": the response body is not valid JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map)) {
            throw new AssertionError(method + ": the response is not a JSON object: " + response.body());
        }
        Map<String, Object> reply = (Map<String, Object>) parsed;
        if (!"2.0".equals(reply.get("jsonrpc"))) {
            throw new AssertionError(method + ": 'jsonrpc' must be \"2.0\": " + response.body());
        }
        if (!Long.valueOf(id).equals(reply.get("id"))) {
            throw new AssertionError(method + ": the response id " + reply.get("id")
                    + " does not echo the request id " + id);
        }
        return reply;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultOf(String method, Map<String, Object> reply) {
        if (reply.containsKey("error")) {
            throw new AssertionError(method + ": JSON-RPC error: " + reply.get("error"));
        }
        Object result = reply.get("result");
        if (!(result instanceof Map)) {
            throw new AssertionError(method + ": 'result' is not an object: " + result);
        }
        return (Map<String, Object>) result;
    }
}
