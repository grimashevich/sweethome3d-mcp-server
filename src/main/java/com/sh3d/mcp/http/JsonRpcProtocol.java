package com.sh3d.mcp.http;

import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.protocol.JsonUtil;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-RPC 2.0 парсер/форматтер для MCP Streamable HTTP.
 * Использует {@link JsonUtil} для низкоуровневого JSON-парсинга.
 */
public final class JsonRpcProtocol {

    /** Ошибки JSON-RPC 2.0 */
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    private JsonRpcProtocol() {
    }

    /**
     * Парсит JSON-RPC 2.0 запрос. Возвращает Map с ключами: jsonrpc, id, method, params.
     * id может быть null (для notification).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseRequest(String json) {
        Object parsed = JsonUtil.parse(json);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    /**
     * Извлекает method из JSON-RPC запроса.
     */
    public static String getMethod(Map<String, Object> request) {
        Object method = request.get("method");
        return method != null ? method.toString() : null;
    }

    /**
     * Извлекает id из JSON-RPC запроса. Может быть null (notification).
     */
    public static Object getId(Map<String, Object> request) {
        return request.get("id");
    }

    /**
     * Извлекает params из JSON-RPC запроса.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getParams(Map<String, Object> request) {
        Object params = request.get("params");
        if (params instanceof Map) {
            return (Map<String, Object>) params;
        }
        return Collections.emptyMap();
    }

    /**
     * Форматирует JSON-RPC 2.0 result response.
     */
    public static String formatResult(Object id, Object result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":");
        JsonUtil.appendValue(sb, id);
        sb.append(",\"result\":");
        JsonUtil.appendValue(sb, result);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Форматирует JSON-RPC 2.0 error response.
     */
    public static String formatError(Object id, int code, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":");
        JsonUtil.appendValue(sb, id);
        sb.append(",\"error\":{\"code\":");
        sb.append(code);
        sb.append(",\"message\":");
        JsonUtil.appendString(sb, message);
        sb.append("}}");
        return sb.toString();
    }

    /**
     * Форматирует MCP initialize result.
     */
    public static String formatInitializeResult(Object id, String protocolVersion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", protocolVersion);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("listChanged", Boolean.TRUE);
        capabilities.put("tools", tools);
        result.put("capabilities", capabilities);

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "sweethome3d");
        serverInfo.put("version", PluginConfig.PLUGIN_VERSION);
        result.put("serverInfo", serverInfo);

        return formatResult(id, result);
    }

    /**
     * Форматирует MCP tools/list result из списка tool-описаний.
     */
    public static String formatToolsListResult(Object id, List<Map<String, Object>> tools) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);
        return formatResult(id, result);
    }

    /**
     * Трансформирует Response (из CommandHandler) в MCP tools/call result.
     *
     * <p>Поддерживает три режима content-блоков:
     * <ul>
     *   <li><b>Multi-content (новый)</b> — data содержит {@code _image} (одно изображение)
     *       или {@code _images} (список изображений). Метаданные (ключи без {@code _})
     *       сериализуются в text block, изображения — в image block(s).</li>
     *   <li><b>Legacy image</b> — data содержит {@code image} (обратная совместимость).
     *       Возвращает одиночный image content block.</li>
     *   <li><b>Text-only</b> — всё остальное. JSON-сериализация data в text block.</li>
     * </ul>
     */
    public static String formatToolCallResult(Object id, Response response) {
        return formatToolCallResult(id, response, null);
    }

    /**
     * Formats a tools/call result with an optional warning message prepended.
     * If warning is non-null and non-empty, it is inserted as the first text content block.
     */
    public static String formatToolCallResult(Object id, Response response, String warning) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Object> content = new ArrayList<>();

        if (warning != null && !warning.isEmpty()) {
            Map<String, Object> warningContent = new LinkedHashMap<>();
            warningContent.put("type", "text");
            warningContent.put("text", warning);
            content.add(warningContent);
        }

        buildToolCallContent(content, response);
        result.put("isError", response.isError() ? Boolean.TRUE : Boolean.FALSE);
        result.put("content", content);
        return formatResult(id, result);
    }

    /**
     * Builds the content blocks for a tools/call result into the provided list.
     */
    @SuppressWarnings("unchecked")
    private static void buildToolCallContent(List<Object> content, Response response) {
        if (response.isOk()) {
            Map<String, Object> data = response.getData();

            if (data != null && (data.containsKey("_image") || data.containsKey("_images"))) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (!entry.getKey().startsWith("_")) {
                        metadata.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!metadata.isEmpty()) {
                    Map<String, Object> textContent = new LinkedHashMap<>();
                    textContent.put("type", "text");
                    textContent.put("text", JsonUtil.serialize(metadata));
                    content.add(textContent);
                }
                if (data.containsKey("_image")) {
                    String mimeType = data.containsKey("_mimeType")
                            ? data.get("_mimeType").toString() : "image/png";
                    Map<String, Object> imageContent = new LinkedHashMap<>();
                    imageContent.put("type", "image");
                    imageContent.put("data", data.get("_image"));
                    imageContent.put("mimeType", mimeType);
                    content.add(imageContent);
                }
                if (data.containsKey("_images")) {
                    List<Map<String, Object>> images = (List<Map<String, Object>>) data.get("_images");
                    for (Map<String, Object> img : images) {
                        Map<String, Object> imageContent = new LinkedHashMap<>();
                        imageContent.put("type", "image");
                        imageContent.put("data", img.get("data"));
                        Object mime = img.get("mimeType");
                        imageContent.put("mimeType", mime != null ? mime : "image/png");
                        content.add(imageContent);
                    }
                }
            } else if (data != null && data.containsKey("image")) {
                Map<String, Object> imageContent = new LinkedHashMap<>();
                imageContent.put("type", "image");
                imageContent.put("data", data.get("image"));
                String mimeType = data.containsKey("mimeType")
                        ? data.get("mimeType").toString() : "image/png";
                imageContent.put("mimeType", mimeType);
                content.add(imageContent);
            } else {
                Map<String, Object> textContent = new LinkedHashMap<>();
                textContent.put("type", "text");
                textContent.put("text", JsonUtil.serialize(data));
                content.add(textContent);
            }
        } else {
            Map<String, Object> textContent = new LinkedHashMap<>();
            textContent.put("type", "text");
            textContent.put("text", response.getMessage());
            content.add(textContent);
        }
    }
}
