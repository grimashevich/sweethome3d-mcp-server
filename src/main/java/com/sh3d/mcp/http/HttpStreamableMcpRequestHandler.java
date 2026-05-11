package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.JsonUtil;
import com.sh3d.mcp.protocol.Response;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Strict MCP Streamable HTTP endpoint served on `/mcp`.
 * <p>
 * Supports POST and DELETE, and explicitly rejects GET with 405 because this server
 * does not currently expose an SSE stream for server-initiated messages.
 * The legacy SSE-style transport remains available separately on `/sse`.
 */
public class HttpStreamableMcpRequestHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(HttpStreamableMcpRequestHandler.class.getName());

    static final int MAX_REQUEST_BODY_SIZE = McpHttpUtil.MAX_REQUEST_BODY_SIZE;

    private final CommandRegistry commandRegistry;
    private final HomeAccessor accessor;
    private final SessionManager sessionManager;

    public HttpStreamableMcpRequestHandler(CommandRegistry commandRegistry, HomeAccessor accessor) {
        this(commandRegistry, accessor, new SessionManager());
    }

    public HttpStreamableMcpRequestHandler(CommandRegistry commandRegistry, HomeAccessor accessor,
                                           SessionManager sessionManager) {
        this.commandRegistry = commandRegistry;
        this.accessor = accessor;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!McpHttpUtil.validateOrigin(exchange)) {
                McpHttpUtil.sendJson(exchange, 403, JsonRpcProtocol.formatError(
                        null, JsonRpcProtocol.INTERNAL_ERROR, "Forbidden origin"));
                return;
            }

            switch (exchange.getRequestMethod()) {
                case "POST":
                    handlePost(exchange);
                    break;
                case "GET":
                    exchange.sendResponseHeaders(405, -1);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    McpHttpUtil.sendJson(exchange, 405, JsonRpcProtocol.formatError(
                            null, JsonRpcProtocol.METHOD_NOT_FOUND, "Method not allowed"));
                    break;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error handling HTTP Streamable MCP request", e);
            try {
                McpHttpUtil.sendJson(exchange, 500, JsonRpcProtocol.formatError(
                        null, JsonRpcProtocol.INTERNAL_ERROR, "Internal server error: " + e.getMessage()));
            } catch (IOException ignored) {
                // Client may already be gone.
            }
        } finally {
            exchange.close();
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String body = McpHttpUtil.readBody(exchange);
        if (body == null) {
            McpHttpUtil.sendJson(exchange, 413, JsonRpcProtocol.formatError(
                    null, JsonRpcProtocol.INVALID_REQUEST,
                    "Request body too large (limit: " + MAX_REQUEST_BODY_SIZE + " bytes)"));
            return;
        }
        if (body.isEmpty()) {
            McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(
                    null, JsonRpcProtocol.PARSE_ERROR, "Empty request body"));
            return;
        }

        Object parsed;
        try {
            parsed = JsonUtil.parse(body);
        } catch (IllegalArgumentException e) {
            McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(
                    null, JsonRpcProtocol.PARSE_ERROR, "Invalid JSON: " + e.getMessage()));
            return;
        }

        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) parsed;
            sendResult(exchange, handleSingleMessage(message, McpHttpUtil.getSessionIdHeader(exchange)));
            return;
        }
        if (parsed instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> batch = (List<Object>) parsed;
            handleBatch(exchange, batch);
            return;
        }

        McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(
                null, JsonRpcProtocol.INVALID_REQUEST, "Expected JSON object or array"));
    }

    private MessageResult handleSingleMessage(Map<String, Object> message, String sessionId) {
        if (isResponse(message)) {
            return MessageResult.empty(202);
        }

        if (isNotification(message)) {
            return handleNotificationMessage(message, sessionId, false);
        }

        return handleRequestMessage(message, sessionId, false);
    }

    private void handleBatch(HttpExchange exchange, List<Object> batch) throws IOException {
        if (batch.isEmpty()) {
            McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(
                    null, JsonRpcProtocol.INVALID_REQUEST, "Batch request must not be empty"));
            return;
        }

        String sessionId = McpHttpUtil.getSessionIdHeader(exchange);
        boolean hasRequests = containsRequest(batch);
        List<String> responses = new ArrayList<>();

        for (Object item : batch) {
            if (!(item instanceof Map)) {
                if (hasRequests) {
                    responses.add(JsonRpcProtocol.formatError(
                            null, JsonRpcProtocol.INVALID_REQUEST, "Batch entry must be a JSON object"));
                    continue;
                }
                McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(
                        null, JsonRpcProtocol.INVALID_REQUEST, "Batch entry must be a JSON object"));
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) item;
            MessageResult result;
            if (isResponse(message)) {
                continue;
            } else if (isNotification(message)) {
                result = handleNotificationMessage(message, sessionId, hasRequests);
            } else {
                result = handleRequestMessage(message, sessionId, true);
            }

            if (result.hasBody()) {
                if (hasRequests) {
                    responses.add(result.body);
                } else {
                    sendResult(exchange, result);
                    return;
                }
            }
        }

        if (responses.isEmpty()) {
            exchange.sendResponseHeaders(202, -1);
            return;
        }

        McpHttpUtil.sendJson(exchange, 200, formatBatchResponse(responses));
    }

    private MessageResult handleNotificationMessage(Map<String, Object> message, String sessionId,
                                                    boolean batchedWithRequests) {
        String method = JsonRpcProtocol.getMethod(message);
        if (method == null) {
            return errorResult(null, JsonRpcProtocol.INVALID_REQUEST, "Missing 'method' field",
                    batchedWithRequests ? 200 : 400);
        }

        if (!"notifications/initialized".equals(method)) {
            return MessageResult.empty(202);
        }

        MessageResult sessionError = validateSessionResult(sessionId, null, batchedWithRequests);
        if (sessionError != null) {
            return sessionError;
        }

        McpSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.setInitialized(true);
        }
        return MessageResult.empty(202);
    }

    private MessageResult handleRequestMessage(Map<String, Object> message, String sessionId, boolean batched) {
        Object id = JsonRpcProtocol.getId(message);
        String method = JsonRpcProtocol.getMethod(message);
        if (method == null) {
            return errorResult(id, JsonRpcProtocol.INVALID_REQUEST, "Missing 'method' field",
                    batched ? 200 : 400);
        }

        switch (method) {
            case "initialize":
                if (batched) {
                    return errorResult(id, JsonRpcProtocol.INVALID_REQUEST,
                            "initialize must be sent as a single request", 200);
                }
                return handleInitializeRequest(message, id);
            case "notifications/initialized":
                MessageResult initSessionError = validateSessionResult(sessionId, id, batched);
                if (initSessionError != null) {
                    return initSessionError;
                }
                McpSession initializedSession = sessionManager.getSession(sessionId);
                if (initializedSession != null) {
                    initializedSession.setInitialized(true);
                }
                return MessageResult.json(200, JsonRpcProtocol.formatResult(id, new LinkedHashMap<>()));
            case "tools/list":
                return handleToolsListRequest(id, sessionId, batched);
            case "tools/call":
                return handleToolsCallRequest(message, id, sessionId, batched);
            case "ping":
                return MessageResult.json(200,
                        JsonRpcProtocol.formatResult(id, new LinkedHashMap<>()));
            default:
                return MessageResult.json(200, JsonRpcProtocol.formatError(
                        id, JsonRpcProtocol.METHOD_NOT_FOUND, "Unknown method: " + method));
        }
    }

    private MessageResult handleInitializeRequest(Map<String, Object> request, Object id) {
        Map<String, Object> params = JsonRpcProtocol.getParams(request);
        String clientVersion = params.containsKey("protocolVersion")
                ? params.get("protocolVersion").toString()
                : McpHttpUtil.SUPPORTED_PROTOCOL_VERSION;

        McpSession session = sessionManager.createSession();
        LOG.info("HTTP Streamable MCP session created: " + session.getSessionId()
                + " (client version: " + clientVersion + ")");

        return new MessageResult(200,
                JsonRpcProtocol.formatInitializeResult(id, McpHttpUtil.SUPPORTED_PROTOCOL_VERSION),
                session.getSessionId());
    }

    private MessageResult handleToolsListRequest(Object id, String sessionId, boolean batched) {
        MessageResult sessionError = validateSessionResult(sessionId, id, batched);
        if (sessionError != null) {
            return sessionError;
        }
        return MessageResult.json(200,
                JsonRpcProtocol.formatToolsListResult(id, McpHttpUtil.buildToolsList(commandRegistry)));
    }

    @SuppressWarnings("unchecked")
    private MessageResult handleToolsCallRequest(Map<String, Object> request, Object id,
                                                 String sessionId, boolean batched) {
        MessageResult sessionError = validateSessionResult(sessionId, id, batched);
        if (sessionError != null) {
            return sessionError;
        }

        Map<String, Object> params = JsonRpcProtocol.getParams(request);
        Object nameObj = params.get("name");
        if (nameObj == null) {
            return MessageResult.json(200, JsonRpcProtocol.formatError(
                    id, JsonRpcProtocol.INVALID_PARAMS, "Missing 'name' in tools/call params"));
        }

        String toolName = nameObj.toString();
        String action = McpHttpUtil.resolveAction(commandRegistry, toolName);
        if (action == null) {
            return MessageResult.json(200, JsonRpcProtocol.formatError(
                    id, JsonRpcProtocol.METHOD_NOT_FOUND, "Unknown tool: " + toolName));
        }

        Object argsObj = params.get("arguments");
        Map<String, Object> arguments = argsObj instanceof Map
                ? (Map<String, Object>) argsObj
                : Collections.emptyMap();

        Response cmdResponse = commandRegistry.dispatch(new com.sh3d.mcp.protocol.Request(action, arguments),
                accessor);
        return MessageResult.json(200, JsonRpcProtocol.formatToolCallResult(id, cmdResponse));
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        String sessionId = McpHttpUtil.getSessionIdHeader(exchange);
        if (sessionId == null) {
            McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(
                    null, JsonRpcProtocol.INVALID_REQUEST, "Missing Mcp-Session-Id header"));
            return;
        }

        McpSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            McpHttpUtil.sendJson(exchange, 404, JsonRpcProtocol.formatError(
                    null, JsonRpcProtocol.INVALID_REQUEST, "Unknown or expired session: " + sessionId));
            return;
        }

        sessionManager.removeSession(sessionId);
        LOG.info("HTTP Streamable MCP session removed: " + sessionId);
        exchange.sendResponseHeaders(200, -1);
    }

    private MessageResult validateSessionResult(String sessionId, Object id, boolean batched) {
        if (sessionId == null) {
            return errorResult(id, JsonRpcProtocol.INVALID_REQUEST, "Missing Mcp-Session-Id header",
                    batched ? 200 : 400);
        }
        if (sessionManager.getSession(sessionId) == null) {
            return errorResult(id, JsonRpcProtocol.INVALID_REQUEST,
                    "Unknown or expired session: " + sessionId, batched ? 200 : 404);
        }
        return null;
    }

    private boolean containsRequest(List<Object> batch) {
        for (Object item : batch) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) item;
                if (isRequest(message)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRequest(Map<String, Object> message) {
        return message.containsKey("method") && message.containsKey("id");
    }

    private static boolean isNotification(Map<String, Object> message) {
        return message.containsKey("method") && !message.containsKey("id");
    }

    private static boolean isResponse(Map<String, Object> message) {
        return !message.containsKey("method")
                && (message.containsKey("result") || message.containsKey("error"));
    }

    private static MessageResult errorResult(Object id, int code, String message, int statusCode) {
        return MessageResult.json(statusCode, JsonRpcProtocol.formatError(id, code, message));
    }

    private static String formatBatchResponse(List<String> responses) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < responses.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(responses.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    private static void sendResult(HttpExchange exchange, MessageResult result) throws IOException {
        if (result.sessionIdHeader != null) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", result.sessionIdHeader);
        }
        if (result.hasBody()) {
            McpHttpUtil.sendJson(exchange, result.statusCode, result.body);
        } else {
            exchange.sendResponseHeaders(result.statusCode, -1);
        }
    }

    private static final class MessageResult {
        private final int statusCode;
        private final String body;
        private final String sessionIdHeader;

        private MessageResult(int statusCode, String body, String sessionIdHeader) {
            this.statusCode = statusCode;
            this.body = body;
            this.sessionIdHeader = sessionIdHeader;
        }

        static MessageResult json(int statusCode, String body) {
            return new MessageResult(statusCode, body, null);
        }

        static MessageResult empty(int statusCode) {
            return new MessageResult(statusCode, null, null);
        }

        boolean hasBody() {
            return body != null;
        }
    }
}
