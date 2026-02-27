package com.sh3d.mcp.http;

import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.protocol.JsonUtil;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcProtocolTest {

    // === parseRequest ===

    @Test
    void testParseRequestValidJsonRpc() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}";
        Map<String, Object> request = JsonRpcProtocol.parseRequest(json);

        assertEquals("2.0", request.get("jsonrpc"));
        assertEquals(1, request.get("id"));
        assertEquals("initialize", request.get("method"));
        assertNotNull(request.get("params"));
    }

    @Test
    void testParseRequestMinimalObject() {
        String json = "{\"method\":\"ping\"}";
        Map<String, Object> request = JsonRpcProtocol.parseRequest(json);

        assertEquals("ping", request.get("method"));
        assertNull(request.get("id"));
    }

    @Test
    void testParseRequestInvalidJsonThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest("{broken json"));
    }

    @Test
    void testParseRequestEmptyStringThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest(""));
    }

    @Test
    void testParseRequestNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest(null));
    }

    @Test
    void testParseRequestArrayThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest("[1, 2, 3]"));
    }

    @Test
    void testParseRequestStringLiteralThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest("\"just a string\""));
    }

    // === getMethod ===

    @Test
    void testGetMethodReturnsMethod() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", "tools/list");
        assertEquals("tools/list", JsonRpcProtocol.getMethod(request));
    }

    @Test
    void testGetMethodReturnsNullWhenMissing() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", 1);
        assertNull(JsonRpcProtocol.getMethod(request));
    }

    @Test
    void testGetMethodConvertsNonStringToString() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", 42);
        assertEquals("42", JsonRpcProtocol.getMethod(request));
    }

    // === getId ===

    @Test
    void testGetIdReturnsIntegerId() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", 42);
        assertEquals(42, JsonRpcProtocol.getId(request));
    }

    @Test
    void testGetIdReturnsStringId() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", "abc-123");
        assertEquals("abc-123", JsonRpcProtocol.getId(request));
    }

    @Test
    void testGetIdReturnsNullWhenMissing() {
        Map<String, Object> request = new LinkedHashMap<>();
        assertNull(JsonRpcProtocol.getId(request));
    }

    // === getParams ===

    @Test
    void testGetParamsReturnsParamsMap() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "test_tool");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("params", params);

        Map<String, Object> result = JsonRpcProtocol.getParams(request);
        assertEquals("test_tool", result.get("name"));
    }

    @Test
    void testGetParamsReturnsEmptyMapWhenMissing() {
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Object> result = JsonRpcProtocol.getParams(request);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetParamsReturnsEmptyMapWhenNotAMap() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("params", "not a map");
        Map<String, Object> result = JsonRpcProtocol.getParams(request);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // === formatResult ===

    @Test
    void testFormatResultWithIntegerId() {
        String json = JsonRpcProtocol.formatResult(1, "ok");
        Map<String, Object> parsed = parseJson(json);
        assertEquals("2.0", parsed.get("jsonrpc"));
        assertEquals(1, parsed.get("id"));
        assertEquals("ok", parsed.get("result"));
    }

    @Test
    void testFormatResultWithStringId() {
        String json = JsonRpcProtocol.formatResult("abc", "done");
        Map<String, Object> parsed = parseJson(json);
        assertEquals("abc", parsed.get("id"));
        assertEquals("done", parsed.get("result"));
    }

    @Test
    void testFormatResultWithNullId() {
        String json = JsonRpcProtocol.formatResult(null, "value");
        Map<String, Object> parsed = parseJson(json);
        assertNull(parsed.get("id"));
        assertEquals("value", parsed.get("result"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatResultWithMapResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", "value");
        String json = JsonRpcProtocol.formatResult(1, result);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> parsedResult = (Map<String, Object>) parsed.get("result");
        assertEquals("value", parsedResult.get("key"));
    }

    @Test
    void testFormatResultWithNullResult() {
        String json = JsonRpcProtocol.formatResult(1, null);
        Map<String, Object> parsed = parseJson(json);
        assertNull(parsed.get("result"));
    }

    // === formatError ===

    @Test
    @SuppressWarnings("unchecked")
    void testFormatErrorParseError() {
        String json = JsonRpcProtocol.formatError(null, JsonRpcProtocol.PARSE_ERROR, "Invalid JSON");
        Map<String, Object> parsed = parseJson(json);
        assertEquals("2.0", parsed.get("jsonrpc"));
        assertNull(parsed.get("id"));
        Map<String, Object> error = (Map<String, Object>) parsed.get("error");
        assertEquals(-32700, error.get("code"));
        assertEquals("Invalid JSON", error.get("message"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatErrorInvalidRequest() {
        String json = JsonRpcProtocol.formatError(1, JsonRpcProtocol.INVALID_REQUEST, "Bad request");
        Map<String, Object> parsed = parseJson(json);
        assertEquals(1, parsed.get("id"));
        Map<String, Object> error = (Map<String, Object>) parsed.get("error");
        assertEquals(-32600, error.get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatErrorMethodNotFound() {
        String json = JsonRpcProtocol.formatError(2, JsonRpcProtocol.METHOD_NOT_FOUND, "Not found");
        Map<String, Object> parsed = parseJson(json);
        assertEquals(-32601, ((Map<String, Object>) parsed.get("error")).get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatErrorInvalidParams() {
        String json = JsonRpcProtocol.formatError(3, JsonRpcProtocol.INVALID_PARAMS, "Invalid params");
        Map<String, Object> parsed = parseJson(json);
        assertEquals(-32602, ((Map<String, Object>) parsed.get("error")).get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatErrorInternalError() {
        String json = JsonRpcProtocol.formatError(4, JsonRpcProtocol.INTERNAL_ERROR, "Internal error");
        Map<String, Object> parsed = parseJson(json);
        assertEquals(-32603, ((Map<String, Object>) parsed.get("error")).get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatErrorEscapesMessage() {
        String json = JsonRpcProtocol.formatError(1, JsonRpcProtocol.PARSE_ERROR, "Error with \"quotes\"");
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> error = (Map<String, Object>) parsed.get("error");
        assertEquals("Error with \"quotes\"", error.get("message"));
    }

    // === formatInitializeResult ===

    @Test
    @SuppressWarnings("unchecked")
    void testFormatInitializeResultContainsProtocolVersion() {
        String json = JsonRpcProtocol.formatInitializeResult(1, "2025-03-26");
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        assertEquals("2025-03-26", result.get("protocolVersion"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatInitializeResultContainsCapabilities() {
        String json = JsonRpcProtocol.formatInitializeResult(1, "2025-03-26");
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        Map<String, Object> tools = (Map<String, Object>) capabilities.get("tools");
        assertEquals(true, tools.get("listChanged"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatInitializeResultContainsServerInfo() {
        String json = JsonRpcProtocol.formatInitializeResult(1, "2025-03-26");
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertEquals("sweethome3d", serverInfo.get("name"));
        assertEquals(PluginConfig.PLUGIN_VERSION, serverInfo.get("version"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatInitializeResultIsValidJsonRpc() {
        String json = JsonRpcProtocol.formatInitializeResult(42, "2025-03-26");
        Map<String, Object> parsed = parseJson(json);
        assertEquals("2.0", parsed.get("jsonrpc"));
        assertEquals(42, parsed.get("id"));
        assertNotNull(parsed.get("result"));
        assertInstanceOf(Map.class, parsed.get("result"));
    }

    // === formatToolsListResult ===

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolsListResultEmptyList() {
        String json = JsonRpcProtocol.formatToolsListResult(1, Collections.emptyList());
        Map<String, Object> parsed = parseJson(json);
        assertEquals("2.0", parsed.get("jsonrpc"));
        assertEquals(1, parsed.get("id"));
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> tools = (List<Object>) result.get("tools");
        assertTrue(tools.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolsListResultWithTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "create_wall");
        tool.put("description", "Create a wall");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        tool.put("inputSchema", schema);
        tools.add(tool);

        String json = JsonRpcProtocol.formatToolsListResult(1, tools);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> parsedTools = (List<Object>) result.get("tools");
        assertEquals(1, parsedTools.size());
        Map<String, Object> parsedTool = (Map<String, Object>) parsedTools.get(0);
        assertEquals("create_wall", parsedTool.get("name"));
        assertEquals("Create a wall", parsedTool.get("description"));
        Map<String, Object> parsedSchema = (Map<String, Object>) parsedTool.get("inputSchema");
        assertEquals("object", parsedSchema.get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolsListResultMultipleTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        Map<String, Object> tool1 = new LinkedHashMap<>();
        tool1.put("name", "tool_a");
        tools.add(tool1);
        Map<String, Object> tool2 = new LinkedHashMap<>();
        tool2.put("name", "tool_b");
        tools.add(tool2);

        String json = JsonRpcProtocol.formatToolsListResult(1, tools);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> parsedTools = (List<Object>) result.get("tools");
        assertEquals(2, parsedTools.size());
        assertEquals("tool_a", ((Map<String, Object>) parsedTools.get(0)).get("name"));
        assertEquals("tool_b", ((Map<String, Object>) parsedTools.get(1)).get("name"));
    }

    // === formatToolCallResult ===

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultOkResponseTextContent() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wallId", "wall-1");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(1, response);
        Map<String, Object> parsed = parseJson(json);
        assertEquals("2.0", parsed.get("jsonrpc"));
        assertEquals(1, parsed.get("id"));
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        assertEquals(false, result.get("isError"));
        List<Object> content = (List<Object>) result.get("content");
        assertEquals(1, content.size());
        Map<String, Object> textBlock = (Map<String, Object>) content.get(0);
        assertEquals("text", textBlock.get("type"));
        Map<String, Object> innerData = parseJson((String) textBlock.get("text"));
        assertEquals("wall-1", innerData.get("wallId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultErrorResponse() {
        Response response = Response.error("Wall not found");

        String json = JsonRpcProtocol.formatToolCallResult(2, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        assertEquals(true, result.get("isError"));
        List<Object> content = (List<Object>) result.get("content");
        Map<String, Object> textBlock = (Map<String, Object>) content.get(0);
        assertEquals("text", textBlock.get("type"));
        assertEquals("Wall not found", textBlock.get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultImageResponse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("image", "iVBORw0KGgoAAAANSUhEUgAAAAE=");
        data.put("mimeType", "image/png");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(3, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        assertEquals(false, result.get("isError"));
        List<Object> content = (List<Object>) result.get("content");
        assertEquals(1, content.size());
        Map<String, Object> imageBlock = (Map<String, Object>) content.get(0);
        assertEquals("image", imageBlock.get("type"));
        assertEquals("iVBORw0KGgoAAAANSUhEUgAAAAE=", imageBlock.get("data"));
        assertEquals("image/png", imageBlock.get("mimeType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultImageResponseDefaultMimeType() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("image", "base64data");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(4, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> content = (List<Object>) result.get("content");
        Map<String, Object> imageBlock = (Map<String, Object>) content.get(0);
        assertEquals("image/png", imageBlock.get("mimeType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultOkWithNullData() {
        Response response = Response.ok(null);

        String json = JsonRpcProtocol.formatToolCallResult(5, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        assertEquals(false, result.get("isError"));
        List<Object> content = (List<Object>) result.get("content");
        Map<String, Object> textBlock = (Map<String, Object>) content.get(0);
        assertEquals("text", textBlock.get("type"));
        assertNotNull(textBlock.get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultOkWithEmptyData() {
        Response response = Response.ok(Collections.emptyMap());

        String json = JsonRpcProtocol.formatToolCallResult(6, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        assertEquals(false, result.get("isError"));
        List<Object> content = (List<Object>) result.get("content");
        Map<String, Object> textBlock = (Map<String, Object>) content.get(0);
        assertEquals("text", textBlock.get("type"));
    }

    // === formatToolCallResult: multi-content (_image, _images) ===

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultNewImageWithMetadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("width", 800);
        data.put("height", 600);
        data.put("size_bytes", 45000);
        data.put("_image", "jpegBase64Data");
        data.put("_mimeType", "image/jpeg");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(10, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        assertEquals(false, result.get("isError"));
        List<Object> content = (List<Object>) result.get("content");
        assertEquals(2, content.size());
        // Text block with metadata
        Map<String, Object> textBlock = (Map<String, Object>) content.get(0);
        assertEquals("text", textBlock.get("type"));
        Map<String, Object> metadata = parseJson((String) textBlock.get("text"));
        assertEquals(800, metadata.get("width"));
        assertEquals(600, metadata.get("height"));
        assertEquals(45000, metadata.get("size_bytes"));
        assertFalse(metadata.containsKey("_image"));
        assertFalse(metadata.containsKey("_mimeType"));
        // Image block
        Map<String, Object> imageBlock = (Map<String, Object>) content.get(1);
        assertEquals("image", imageBlock.get("type"));
        assertEquals("jpegBase64Data", imageBlock.get("data"));
        assertEquals("image/jpeg", imageBlock.get("mimeType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultNewImageDefaultMimeType() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_image", "pngBase64");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(11, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> content = (List<Object>) result.get("content");
        Map<String, Object> imageBlock = (Map<String, Object>) content.get(content.size() - 1);
        assertEquals("image", imageBlock.get("type"));
        assertEquals("image/png", imageBlock.get("mimeType"));
        assertEquals("pngBase64", imageBlock.get("data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultNewImageNoMetadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_image", "onlyImage");
        data.put("_mimeType", "image/png");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(12, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> content = (List<Object>) result.get("content");
        assertEquals(1, content.size(), "Should only have image block when no metadata");
        Map<String, Object> imageBlock = (Map<String, Object>) content.get(0);
        assertEquals("image", imageBlock.get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultMultipleImages() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("view", "overhead");
        data.put("imageCount", 2);

        List<Map<String, Object>> images = new ArrayList<>();
        Map<String, Object> img1 = new LinkedHashMap<>();
        img1.put("data", "img1base64");
        img1.put("mimeType", "image/jpeg");
        images.add(img1);
        Map<String, Object> img2 = new LinkedHashMap<>();
        img2.put("data", "img2base64");
        img2.put("mimeType", "image/jpeg");
        images.add(img2);
        data.put("_images", images);

        Response response = Response.ok(data);
        String json = JsonRpcProtocol.formatToolCallResult(13, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        assertEquals(false, result.get("isError"));
        List<Object> content = (List<Object>) result.get("content");
        assertEquals(3, content.size()); // text + 2 images
        // Text block with metadata
        Map<String, Object> textBlock = (Map<String, Object>) content.get(0);
        assertEquals("text", textBlock.get("type"));
        Map<String, Object> metadata = parseJson((String) textBlock.get("text"));
        assertEquals("overhead", metadata.get("view"));
        assertEquals(2, metadata.get("imageCount"));
        // Image blocks
        Map<String, Object> imgBlock1 = (Map<String, Object>) content.get(1);
        assertEquals("image", imgBlock1.get("type"));
        assertEquals("img1base64", imgBlock1.get("data"));
        Map<String, Object> imgBlock2 = (Map<String, Object>) content.get(2);
        assertEquals("image", imgBlock2.get("type"));
        assertEquals("img2base64", imgBlock2.get("data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultMultipleImagesDefaultMimeType() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> images = new ArrayList<>();
        Map<String, Object> img = new LinkedHashMap<>();
        img.put("data", "noMimeImage");
        images.add(img);
        data.put("_images", images);

        Response response = Response.ok(data);
        String json = JsonRpcProtocol.formatToolCallResult(14, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> content = (List<Object>) result.get("content");
        Map<String, Object> imageBlock = null;
        for (Object c : content) {
            Map<String, Object> block = (Map<String, Object>) c;
            if ("image".equals(block.get("type"))) {
                imageBlock = block;
                break;
            }
        }
        assertNotNull(imageBlock);
        assertEquals("image/png", imageBlock.get("mimeType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultUnderscoreKeysExcludedFromMetadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("format", "jpeg");
        data.put("_image", "secretBase64");
        data.put("_mimeType", "image/jpeg");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(15, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> content = (List<Object>) result.get("content");
        assertEquals(2, content.size());
        // Text block should have "format" but not underscore keys
        Map<String, Object> textBlock = (Map<String, Object>) content.get(0);
        assertEquals("text", textBlock.get("type"));
        Map<String, Object> metadata = parseJson((String) textBlock.get("text"));
        assertEquals("jpeg", metadata.get("format"));
        assertFalse(metadata.containsKey("_image"), "Metadata should NOT contain '_image' key");
        assertFalse(metadata.containsKey("_mimeType"), "Metadata should NOT contain '_mimeType' key");
        // Image block should have the base64 data
        Map<String, Object> imageBlock = (Map<String, Object>) content.get(1);
        assertEquals("image", imageBlock.get("type"));
        assertEquals("secretBase64", imageBlock.get("data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFormatToolCallResultLegacyImageStillWorks() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("image", "legacyBase64");
        data.put("mimeType", "image/png");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(16, response);
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> result = (Map<String, Object>) parsed.get("result");
        List<Object> content = (List<Object>) result.get("content");
        assertEquals(1, content.size());
        Map<String, Object> imageBlock = (Map<String, Object>) content.get(0);
        assertEquals("image", imageBlock.get("type"));
        assertEquals("legacyBase64", imageBlock.get("data"));
        assertEquals("image/png", imageBlock.get("mimeType"));
    }

    // === Error code constants ===

    @Test
    void testErrorCodeConstants() {
        assertEquals(-32700, JsonRpcProtocol.PARSE_ERROR);
        assertEquals(-32600, JsonRpcProtocol.INVALID_REQUEST);
        assertEquals(-32601, JsonRpcProtocol.METHOD_NOT_FOUND);
        assertEquals(-32602, JsonRpcProtocol.INVALID_PARAMS);
        assertEquals(-32603, JsonRpcProtocol.INTERNAL_ERROR);
    }

    // === Helper ===

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        return (Map<String, Object>) JsonUtil.parse(json);
    }
}
