package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Response;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик HTTP-запросов для legacy SSE-style endpoint `/sse`.
 * <p>
 * POST /sse — JSON-RPC 2.0 запросы (initialize, tools/list, tools/call)
 * GET /sse — SSE-поток для server→client уведомлений
 * DELETE /sse — завершение сессии
 */
public class SseMcpRequestHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(SseMcpRequestHandler.class.getName());

    /** Версия MCP-протокола, поддерживаемая сервером */
    private static final String SUPPORTED_PROTOCOL_VERSION = McpHttpUtil.SUPPORTED_PROTOCOL_VERSION;

    /** Maximum allowed request body size (10 MB). Bodies exceeding this limit result in HTTP 413. */
    static final int MAX_REQUEST_BODY_SIZE = McpHttpUtil.MAX_REQUEST_BODY_SIZE;

    private final CommandRegistry commandRegistry;
    private final HomeAccessor accessor;
    private final SessionManager sessionManager;

    public SseMcpRequestHandler(CommandRegistry commandRegistry, HomeAccessor accessor) {
        this(commandRegistry, accessor, new SessionManager());
    }

    public SseMcpRequestHandler(CommandRegistry commandRegistry, HomeAccessor accessor,
                                SessionManager sessionManager) {
        this.commandRegistry = commandRegistry;
        this.accessor = accessor;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Валидация Origin (DNS rebinding protection)
            if (!McpHttpUtil.validateOrigin(exchange)) {
                McpHttpUtil.sendJson(exchange, 403, JsonRpcProtocol.formatError(null,
                        JsonRpcProtocol.INTERNAL_ERROR, "Forbidden origin"));
                return;
            }

            String method = exchange.getRequestMethod();
            switch (method) {
                case "POST":
                    handlePost(exchange);
                    break;
                case "GET":
                    handleGet(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    McpHttpUtil.sendJson(exchange, 405, JsonRpcProtocol.formatError(null,
                            JsonRpcProtocol.METHOD_NOT_FOUND, "Method not allowed"));
                    break;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error handling SSE MCP request", e);
            try {
                McpHttpUtil.sendJson(exchange, 500, JsonRpcProtocol.formatError(null,
                        JsonRpcProtocol.INTERNAL_ERROR, "Internal server error: " + e.getMessage()));
            } catch (IOException ignored) {
                // Клиент мог уже отключиться
            }
        } finally {
            exchange.close();
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String body = McpHttpUtil.readBody(exchange);
        if (body == null) {
            McpHttpUtil.sendJson(exchange, 413, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.INVALID_REQUEST,
                    "Request body too large (limit: " + MAX_REQUEST_BODY_SIZE + " bytes)"));
            return;
        }
        if (body.isEmpty()) {
            McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.PARSE_ERROR, "Empty request body"));
            return;
        }

        Map<String, Object> request;
        try {
            request = JsonRpcProtocol.parseRequest(body);
        } catch (IllegalArgumentException e) {
            McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.PARSE_ERROR, "Invalid JSON: " + e.getMessage()));
            return;
        }

        String method = JsonRpcProtocol.getMethod(request);
        Object id = JsonRpcProtocol.getId(request);

        if (method == null) {
            McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(id,
                    JsonRpcProtocol.INVALID_REQUEST, "Missing 'method' field"));
            return;
        }

        LOG.fine("MCP request: method=" + method + " id=" + id);

        switch (method) {
            case "initialize":
                handleInitialize(exchange, request, id);
                break;
            case "notifications/initialized":
                handleInitialized(exchange, request);
                break;
            case "tools/list":
                handleToolsList(exchange, request, id);
                break;
            case "tools/call":
                handleToolsCall(exchange, request, id);
                break;
            case "ping":
                McpHttpUtil.sendJson(exchange, 200,
                        JsonRpcProtocol.formatResult(id, new java.util.LinkedHashMap<>()));
                break;
            default:
                McpHttpUtil.sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
                        JsonRpcProtocol.METHOD_NOT_FOUND, "Unknown method: " + method));
                break;
        }
    }

    private void handleInitialize(HttpExchange exchange, Map<String, Object> request, Object id)
            throws IOException {
        Map<String, Object> params = JsonRpcProtocol.getParams(request);
        String clientVersion = params.containsKey("protocolVersion")
                ? params.get("protocolVersion").toString()
                : SUPPORTED_PROTOCOL_VERSION;

        // Negotiation: сервер отвечает своей версией
        McpSession session = sessionManager.createSession();

        exchange.getResponseHeaders().set("Mcp-Session-Id", session.getSessionId());

        String response = JsonRpcProtocol.formatInitializeResult(id, SUPPORTED_PROTOCOL_VERSION);
        McpHttpUtil.sendJson(exchange, 200, response);

        LOG.info("SSE MCP session created: " + session.getSessionId()
                + " (client version: " + clientVersion + ")");
    }

    private void handleInitialized(HttpExchange exchange, Map<String, Object> request)
            throws IOException {
        String sessionId = McpHttpUtil.getSessionIdHeader(exchange);
        McpSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.setInitialized(true);
        }
        // Notification → 202 Accepted, no body
        exchange.sendResponseHeaders(202, -1);
    }

    private void handleToolsList(HttpExchange exchange, Map<String, Object> request, Object id)
            throws IOException {
        McpSession session = validateSession(exchange);
        if (session == null) return;

        java.util.List<Map<String, Object>> tools = McpHttpUtil.buildToolsList(commandRegistry);

        // If session was auto-recreated, clear the flag (warning only shown on tools/call)
        if (session.isRecreated()) {
            session.setRecreated(false);
            LOG.info("Session recreated flag cleared on tools/list");
        }

        McpHttpUtil.sendJson(exchange, 200, JsonRpcProtocol.formatToolsListResult(id, tools));
    }

    @SuppressWarnings("unchecked")
    private void handleToolsCall(HttpExchange exchange, Map<String, Object> request, Object id)
            throws IOException {
        McpSession session = validateSession(exchange);
        if (session == null) return;

        Map<String, Object> params = JsonRpcProtocol.getParams(request);
        Object nameObj = params.get("name");
        if (nameObj == null) {
            McpHttpUtil.sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
                    JsonRpcProtocol.INVALID_PARAMS, "Missing 'name' in tools/call params"));
            return;
        }
        String toolName = nameObj.toString();

        // Извлекаем arguments
        Map<String, Object> arguments;
        Object argsObj = params.get("arguments");
        if (argsObj instanceof Map) {
            arguments = (Map<String, Object>) argsObj;
        } else {
            arguments = Collections.emptyMap();
        }

        // Находим action по toolName (может совпадать с action или CommandDescriptor.getToolName())
        String action = McpHttpUtil.resolveAction(commandRegistry, toolName);
        if (action == null) {
            McpHttpUtil.sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
                    JsonRpcProtocol.METHOD_NOT_FOUND, "Unknown tool: " + toolName));
            return;
        }

        // Dispatch через CommandRegistry
        Response cmdResponse = commandRegistry.dispatch(new com.sh3d.mcp.protocol.Request(action, arguments),
                accessor);

        // If session was auto-recreated (server restart), prepend a warning
        String warning = null;
        if (session.isRecreated()) {
            warning = "WARNING: MCP session was auto-recreated because the Sweet Home 3D "
                    + "server was restarted. The scene content may have changed — all previously "
                    + "known object IDs (walls, furniture, rooms) are likely invalid. "
                    + "Call get_state to refresh your knowledge of the current scene before "
                    + "making any modifications.";
            session.setRecreated(false);
        }

        McpHttpUtil.sendJson(exchange, 200, JsonRpcProtocol.formatToolCallResult(id, cmdResponse, warning));
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        // SSE-поток для server→client уведомлений
        // В MVP не используется — сервер не инициирует запросы к клиенту
        exchange.sendResponseHeaders(405, -1);
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        String sessionId = McpHttpUtil.getSessionIdHeader(exchange);
        if (sessionId != null) {
            sessionManager.removeSession(sessionId);
            LOG.info("SSE MCP session removed: " + sessionId);
        }
        exchange.sendResponseHeaders(200, -1);
    }

    // === Утилиты ===

    /**
     * Валидирует сессию по Mcp-Session-Id header.
     * <p>
     * If the session expired but was previously created via handshake (known expired ID),
     * it is auto-recreated. Truly unknown IDs (never created on this server) are rejected
     * with HTTP 400. Returns null and sends an error if the header is missing or the ID
     * is unknown.
     */
    private McpSession validateSession(HttpExchange exchange) throws IOException {
        String sessionId = McpHttpUtil.getSessionIdHeader(exchange);
        if (sessionId == null) {
            McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.INVALID_REQUEST, "Missing Mcp-Session-Id header"));
            return null;
        }
        McpSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            if (sessionManager.isKnownExpired(sessionId)) {
                // Auto-recreate session: the server was likely restarted (SH3D restart,
                // plugin redeploy) while the MCP client still holds the old session ID.
                // This is a single-user local plugin — safe to auto-recover.
                session = sessionManager.createSessionWithId(sessionId);
                session.setInitialized(true);
                session.setRecreated(true);
                LOG.warning("MCP session auto-recreated with same ID: " + sessionId
                        + " (server was likely restarted)");
            } else {
                // Truly unknown session ID — never created on this server
                McpHttpUtil.sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                        JsonRpcProtocol.INVALID_REQUEST, "Unknown session: " + sessionId));
                return null;
            }
        }
        return session;
    }
}
