package com.sh3d.mcp.http;

import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

final class McpHttpUtil {

    static final String SSE_ENDPOINT = "/sse";
    static final String MCP_ENDPOINT = "/mcp";
    static final String HEALTH_ENDPOINT = "/health";
    static final String SUPPORTED_PROTOCOL_VERSION = "2025-03-26";
    static final int MAX_REQUEST_BODY_SIZE = 10 * 1024 * 1024;

    private McpHttpUtil() {
    }

    static String buildLocalhostUrl(int port, String endpointPath) {
        return "http://localhost:" + port + endpointPath;
    }

    static String buildLoopbackUrl(int port, String endpointPath) {
        return "http://127.0.0.1:" + port + endpointPath;
    }

    static List<Map<String, Object>> buildToolsList(CommandRegistry commandRegistry) {
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
        return tools;
    }

    static String resolveAction(CommandRegistry commandRegistry, String toolName) {
        if (commandRegistry.hasHandler(toolName)) {
            return toolName;
        }
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

    static boolean validateOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) {
            return true;
        }
        return isLocalhostOrigin(origin, "http://localhost")
                || isLocalhostOrigin(origin, "http://127.0.0.1")
                || isLocalhostOrigin(origin, "https://localhost")
                || isLocalhostOrigin(origin, "https://127.0.0.1");
    }

    static String getSessionIdHeader(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
    }

    static String readBody(HttpExchange exchange) throws IOException {
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

    static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static boolean isLocalhostOrigin(String origin, String prefix) {
        if (!origin.startsWith(prefix)) {
            return false;
        }
        return origin.length() == prefix.length()
                || origin.charAt(prefix.length()) == ':'
                || origin.charAt(prefix.length()) == '/';
    }
}
