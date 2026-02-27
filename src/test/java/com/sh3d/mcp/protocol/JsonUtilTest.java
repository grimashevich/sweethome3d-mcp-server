package com.sh3d.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void testSerializePrettySimpleObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("value", 42);

        String result = JsonUtil.serializePretty(map);
        assertEquals("{\n  \"name\": \"test\",\n  \"value\": 42\n}\n", result);
    }

    @Test
    void testSerializePrettyNestedObject() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("type", "http");
        inner.put("url", "http://localhost:9877/mcp");

        Map<String, Object> servers = new LinkedHashMap<>();
        servers.put("sweethome3d", inner);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mcpServers", servers);

        String result = JsonUtil.serializePretty(root);
        String expected = "{\n"
                + "  \"mcpServers\": {\n"
                + "    \"sweethome3d\": {\n"
                + "      \"type\": \"http\",\n"
                + "      \"url\": \"http://localhost:9877/mcp\"\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        assertEquals(expected, result);
    }

    @Test
    void testSerializePrettyEmptyObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        assertEquals("{}\n", JsonUtil.serializePretty(map));
    }

    @Test
    void testSerializePrettyArray() {
        List<Object> list = new ArrayList<>(Arrays.asList("a", "b", "c"));

        String result = JsonUtil.serializePretty(list);
        assertEquals("[\n  \"a\",\n  \"b\",\n  \"c\"\n]\n", result);
    }

    @Test
    void testSerializePrettyEmptyArray() {
        List<Object> list = new ArrayList<>();
        assertEquals("[]\n", JsonUtil.serializePretty(list));
    }

    @Test
    void testSerializePrettyMixedTypes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("string", "hello");
        map.put("number", 3.14);
        map.put("bool", true);
        map.put("nil", null);

        String result = JsonUtil.serializePretty(map);
        assertTrue(result.contains("  \"string\": \"hello\""));
        assertTrue(result.contains("  \"number\": 3.14"));
        assertTrue(result.contains("  \"bool\": true"));
        assertTrue(result.contains("  \"nil\": null"));
    }

    @Test
    void testSerializePrettyObjectWithArray() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("items", new ArrayList<>(Arrays.asList(1, 2, 3)));

        String result = JsonUtil.serializePretty(map);
        String expected = "{\n"
                + "  \"items\": [\n"
                + "    1,\n"
                + "    2,\n"
                + "    3\n"
                + "  ]\n"
                + "}\n";
        assertEquals(expected, result);
    }

    @Test
    void testSerializePrettyRoundTrip() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("type", "http");
        inner.put("url", "http://localhost:9877/mcp");

        Map<String, Object> servers = new LinkedHashMap<>();
        servers.put("sweethome3d", inner);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mcpServers", servers);

        String pretty = JsonUtil.serializePretty(root);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonUtil.parse(pretty);
        assertEquals(root, parsed);
    }

    @Test
    void testSerializePrettyEscapedStrings() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("msg", "line1\nline2");
        map.put("path", "C:\\Users\\test");

        String result = JsonUtil.serializePretty(map);
        assertTrue(result.contains("\"msg\": \"line1\\nline2\""));
        assertTrue(result.contains("\"path\": \"C:\\\\Users\\\\test\""));
    }
}
