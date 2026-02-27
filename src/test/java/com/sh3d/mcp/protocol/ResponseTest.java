package com.sh3d.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {

    @Test
    void testOkResponse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        Response resp = Response.ok(data);
        assertTrue(resp.isOk());
        assertFalse(resp.isError());
        assertEquals("ok", resp.getStatus());
        assertEquals("value", resp.getData().get("key"));
        assertNull(resp.getMessage());
    }

    @Test
    void testErrorResponse() {
        Response resp = Response.error("Something failed");
        assertTrue(resp.isError());
        assertFalse(resp.isOk());
        assertEquals("error", resp.getStatus());
        assertEquals("Something failed", resp.getMessage());
        assertNull(resp.getData());
    }

    // ==================== Response.ok() with null data ====================

    @Test
    void testOkWithNullDataReturnsEmptyMap() {
        Response resp = Response.ok(null);
        assertTrue(resp.isOk());
        assertNotNull(resp.getData());
        assertTrue(resp.getData().isEmpty());
    }

    @Test
    void testOkWithEmptyMapReturnsEmptyMap() {
        Response resp = Response.ok(Collections.emptyMap());
        assertTrue(resp.isOk());
        assertNotNull(resp.getData());
        assertTrue(resp.getData().isEmpty());
    }

    // ==================== Response.error() with null message ====================

    @Test
    void testErrorWithNullMessage() {
        Response resp = Response.error(null);
        assertTrue(resp.isError());
        assertFalse(resp.isOk());
        assertNull(resp.getMessage());
        assertNull(resp.getData());
    }

    // ==================== isOk / isError for both cases ====================

    @Test
    void testIsOkReturnsFalseForError() {
        Response resp = Response.error("fail");
        assertFalse(resp.isOk());
    }

    @Test
    void testIsErrorReturnsFalseForOk() {
        Response resp = Response.ok(new LinkedHashMap<>());
        assertFalse(resp.isError());
    }

    // ==================== getData type ====================

    @Test
    void testGetDataReturnsUnmodifiableMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        Response resp = Response.ok(data);

        assertThrows(UnsupportedOperationException.class,
                () -> resp.getData().put("new", "val"));
    }

    @Test
    void testGetDataForErrorReturnsNull() {
        Response resp = Response.error("fail");
        assertNull(resp.getData());
    }

    // ==================== getMessage type ====================

    @Test
    void testGetMessageForOkReturnsNull() {
        Response resp = Response.ok(new LinkedHashMap<>());
        assertNull(resp.getMessage());
    }

    @Test
    void testGetMessageForErrorReturnsString() {
        Response resp = Response.error("failure");
        assertInstanceOf(String.class, resp.getMessage());
        assertEquals("failure", resp.getMessage());
    }

    // ==================== getStatus ====================

    @Test
    void testGetStatusOk() {
        Response resp = Response.ok(new LinkedHashMap<>());
        assertEquals("ok", resp.getStatus());
    }

    @Test
    void testGetStatusError() {
        Response resp = Response.error("fail");
        assertEquals("error", resp.getStatus());
    }

    // ==================== Data with various types ====================

    @Test
    void testOkDataContainsMultipleTypes() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("string", "hello");
        data.put("number", 42);
        data.put("float", 3.14);
        data.put("bool", true);

        Response resp = Response.ok(data);
        assertTrue(resp.isOk());
        assertEquals("hello", resp.getData().get("string"));
        assertEquals(42, resp.getData().get("number"));
        assertEquals(3.14, resp.getData().get("float"));
        assertEquals(true, resp.getData().get("bool"));
    }
}
