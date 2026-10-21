package com.sh3d.mcp.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка разборщика, которым живые тесты читают ответы сервера: ошибка в нём сделала бы
 * слепыми сами тесты, поэтому он проверяется обычным прогоном, без Sweet Home 3D.
 */
class JsonTest {

    @Test
    @SuppressWarnings("unchecked")
    void parsesNestedDocument() {
        Object parsed = Json.parse(" {\"a\": [1, -2.5, 1e2, true, false, null], \"b\": {\"c\": \"x\"}, \"d\": []} ");

        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals(Arrays.asList("a", "b", "d"), List.copyOf(map.keySet()), "key order is kept");
        List<Object> a = (List<Object>) map.get("a");
        assertEquals(Long.valueOf(1), a.get(0));
        assertEquals(Double.valueOf(-2.5), a.get(1));
        assertEquals(Double.valueOf(100), a.get(2));
        assertEquals(Boolean.TRUE, a.get(3));
        assertEquals(Boolean.FALSE, a.get(4));
        assertNull(a.get(5));
        assertEquals("x", ((Map<String, Object>) map.get("b")).get("c"));
        assertTrue(((List<Object>) map.get("d")).isEmpty());
    }

    @Test
    void parsesStringEscapes() {
        assertEquals("a\"b\\c/d\n\téЖ", Json.parse("\"a\\\"b\\\\c\\/d\\n\\t\\u00e9Ж\""));
    }

    @Test
    void integerBeyondLongBecomesDouble() {
        assertEquals(Double.valueOf(1e30), Json.parse("1000000000000000000000000000000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "{",
        "[1,]",
        "{\"a\":1,}",
        "{\"a\":1 \"b\":2}",
        "{a:1}",
        "{\"a\":1,\"a\":2}",
        "\"unterminated",
        "\"bad\\xescape\"",
        "\"tab\there\"",
        "NaN",
        "Infinity",
        "01",
        "1.",
        ".5",
        "1e",
        "+1",
        "tru",
        "nul",
        "{} x",
        "[1] [2]"
    })
    void rejectsInvalidJson(String text) {
        assertThrows(IllegalArgumentException.class, () -> Json.parse(text), "must be rejected: " + text);
    }

    @Test
    void writtenTextParsesBack() {
        Map<String, Object> value = McpLiveClient.args(
                "text", "line\nbreak \"quoted\" \\ \u0001",
                "int", 42L,
                "real", 1.5,
                "big", 1e40,
                "flag", true,
                "nothing", null,
                "list", Arrays.asList(1L, "two", McpLiveClient.args("k", "v")));

        assertEquals(value, Json.parse(Json.write(value)));
    }

    @Test
    void refusesToWriteNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.POSITIVE_INFINITY));
    }
}
