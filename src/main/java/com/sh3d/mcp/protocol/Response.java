package com.sh3d.mcp.protocol;

import java.util.Collections;
import java.util.Map;

/**
 * Value object: JSON-ответ (status + data/message).
 */
public class Response {

    private final String status;
    private final Map<String, Object> data;
    private final String message;

    private Response(String status, Map<String, Object> data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    /**
     * Создаёт успешный ответ с данными.
     */
    public static Response ok(Map<String, Object> data) {
        return new Response("ok", data != null ? data : Collections.emptyMap(), null);
    }

    /**
     * Создаёт ответ с ошибкой.
     */
    public static Response error(String message) {
        return new Response("error", null, message);
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getData() {
        return data != null ? Collections.unmodifiableMap(data) : null;
    }

    public String getMessage() {
        return message;
    }

    public boolean isOk() {
        return "ok".equals(status);
    }

    public boolean isError() {
        return "error".equals(status);
    }
}
