package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик HTTP-запросов для MCP Streamable HTTP endpoint.
 * <p>
 * POST /mcp — JSON-RPC 2.0 запросы (initialize, tools/list, tools/call)
 * GET /mcp — SSE-поток для server→client уведомлений
 * DELETE /mcp — завершение сессии
 */
public class McpRequestHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(McpRequestHandler.class.getName());

    /** Версия MCP-протокола, поддерживаемая сервером */
    private static final String SUPPORTED_PROTOCOL_VERSION = "2025-03-26";

    /** Maximum allowed request body size (10 MB). Bodies exceeding this limit result in HTTP 413. */
    static final int MAX_REQUEST_BODY_SIZE = 10 * 1024 * 1024;

    private final CommandRegistry commandRegistry;
    private final HomeAccessor accessor;
    private final SessionManager sessionManager;

    public McpRequestHandler(CommandRegistry commandRegistry, HomeAccessor accessor) {
        this(commandRegistry, accessor, new SessionManager());
    }

    public McpRequestHandler(CommandRegistry commandRegistry, HomeAccessor accessor,
                             SessionManager sessionManager) {
        this.commandRegistry = commandRegistry;
        this.accessor = accessor;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Валидация Origin (DNS rebinding protection)
            if (!validateOrigin(exchange)) {
                sendJson(exchange, 403, JsonRpcProtocol.formatError(null,
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
                    sendJson(exchange, 405, JsonRpcProtocol.formatError(null,
                            JsonRpcProtocol.METHOD_NOT_FOUND, "Method not allowed"));
                    break;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error handling MCP request", e);
            try {
                sendJson(exchange, 500, JsonRpcProtocol.formatError(null,
                        JsonRpcProtocol.INTERNAL_ERROR, "Internal server error: " + e.getMessage()));
            } catch (IOException ignored) {
                // Клиент мог уже отключиться
            }
        } finally {
            exchange.close();
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null) {
            sendJson(exchange, 413, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.INVALID_REQUEST,
                    "Request body too large (limit: " + MAX_REQUEST_BODY_SIZE + " bytes)"));
            return;
        }
        if (body.isEmpty()) {
            sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.PARSE_ERROR, "Empty request body"));
            return;
        }

        Map<String, Object> request;
        try {
            request = JsonRpcProtocol.parseRequest(body);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.PARSE_ERROR, "Invalid JSON: " + e.getMessage()));
            return;
        }

        String method = JsonRpcProtocol.getMethod(request);
        Object id = JsonRpcProtocol.getId(request);

        if (method == null) {
            sendJson(exchange, 400, JsonRpcProtocol.formatError(id,
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
                sendJson(exchange, 200, JsonRpcProtocol.formatResult(id, new java.util.LinkedHashMap<>()));
                break;
            default:
                sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
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
        sendJson(exchange, 200, response);

        LOG.info("MCP session created: " + session.getSessionId()
                + " (client version: " + clientVersion + ")");
    }

    private void handleInitialized(HttpExchange exchange, Map<String, Object> request)
            throws IOException {
        String sessionId = getSessionIdHeader(exchange);
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

        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map.Entry<String, CommandHandler> entry : commandRegistry.getHandlers().entrySet()) {
            String action = entry.getKey();
            CommandHandler handler = entry.getValue();

            if (handler instanceof CommandDescriptor) {
                CommandDescriptor descriptor = (CommandDescriptor) handler;
                Map<String, Object> tool = new LinkedHashMap<>();

                String toolName = descriptor.getToolName();
                tool.put("name", (toolName != null && !toolName.isEmpty()) ? toolName : action);
                tool.put("description", descriptor.getDescription());
                tool.put("inputSchema", descriptor.getSchema());
                tools.add(tool);
            }
        }

        // If session was auto-recreated, clear the flag (warning only shown on tools/call)
        if (session.isRecreated()) {
            session.setRecreated(false);
            LOG.info("Session recreated flag cleared on tools/list");
        }

        sendJson(exchange, 200, JsonRpcProtocol.formatToolsListResult(id, tools));
    }

    @SuppressWarnings("unchecked")
    private void handleToolsCall(HttpExchange exchange, Map<String, Object> request, Object id)
            throws IOException {
        McpSession session = validateSession(exchange);
        if (session == null) return;

        Map<String, Object> params = JsonRpcProtocol.getParams(request);
        Object nameObj = params.get("name");
        if (nameObj == null) {
            sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
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
        String action = resolveAction(toolName);
        if (action == null) {
            sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
                    JsonRpcProtocol.METHOD_NOT_FOUND, "Unknown tool: " + toolName));
            return;
        }

        // Dispatch через CommandRegistry
        Request cmdRequest = new Request(action, arguments);
        Response cmdResponse = commandRegistry.dispatch(cmdRequest, accessor);

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

        sendJson(exchange, 200, JsonRpcProtocol.formatToolCallResult(id, cmdResponse, warning));
    }

    /**
     * Разрешает toolName → action. Учитывает CommandDescriptor.getToolName().
     */
    private String resolveAction(String toolName) {
        // Прямое совпадение с action
        if (commandRegistry.hasHandler(toolName)) {
            return toolName;
        }
        // Поиск по CommandDescriptor.getToolName()
        for (Map.Entry<String, CommandHandler> entry : commandRegistry.getHandlers().entrySet()) {
            CommandHandler handler = entry.getValue();
            if (handler instanceof CommandDescriptor) {
                String descriptorToolName = ((CommandDescriptor) handler).getToolName();
                if (toolName.equals(descriptorToolName)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        // SSE-поток для server→client уведомлений
        // В MVP не используется — сервер не инициирует запросы к клиенту
        exchange.sendResponseHeaders(405, -1);
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        String sessionId = getSessionIdHeader(exchange);
        if (sessionId != null) {
            sessionManager.removeSession(sessionId);
            LOG.info("MCP session removed: " + sessionId);
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
        String sessionId = getSessionIdHeader(exchange);
        if (sessionId == null) {
            sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
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
                sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                        JsonRpcProtocol.INVALID_REQUEST, "Unknown session: " + sessionId));
                return null;
            }
        }
        return session;
    }

    private boolean validateOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        // null origin допустим (curl, non-browser clients)
        if (origin == null) {
            return true;
        }
        // Допускаем только localhost origins (exact match or with port/path)
        return isLocalhostOrigin(origin, "http://localhost")
                || isLocalhostOrigin(origin, "http://127.0.0.1")
                || isLocalhostOrigin(origin, "https://localhost")
                || isLocalhostOrigin(origin, "https://127.0.0.1");
    }

    /**
     * Checks that origin is exactly the given prefix, or the prefix followed by ':' or '/'.
     * This prevents DNS rebinding attacks like "http://localhost.evil.com".
     */
    private static boolean isLocalhostOrigin(String origin, String prefix) {
        if (!origin.startsWith(prefix)) {
            return false;
        }
        return origin.length() == prefix.length()
                || origin.charAt(prefix.length()) == ':'
                || origin.charAt(prefix.length()) == '/';
    }

    private String getSessionIdHeader(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
    }

    /**
     * Reads request body with a size limit of {@link #MAX_REQUEST_BODY_SIZE} bytes.
     * Counts raw bytes (not characters) to enforce the limit correctly for multibyte UTF-8.
     *
     * @return the body string, or {@code null} if the body exceeds the limit
     */
    private String readBody(HttpExchange exchange) throws IOException {
        try (java.io.InputStream is = exchange.getRequestBody()) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int totalBytes = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > MAX_REQUEST_BODY_SIZE) {
                    return null;
                }
                baos.write(buffer, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
