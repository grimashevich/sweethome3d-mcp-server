package com.sh3d.mcp.e2e;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Результат {@code tools/call}: признак ошибки и блоки содержимого, как их отдал сервер.
 */
final class ToolResult {

    private final String tool;
    private final boolean error;
    private final List<Map<String, Object>> content;

    ToolResult(String tool, boolean error, List<Map<String, Object>> content) {
        this.tool = tool;
        this.error = error;
        this.content = content;
    }

    boolean isError() {
        return error;
    }

    List<Map<String, Object>> content() {
        return content;
    }

    /** Текст первого текстового блока; у ошибки это сообщение об ошибке. */
    String text() {
        for (Map<String, Object> block : content) {
            if ("text".equals(block.get("type"))) {
                return (String) block.get("text");
            }
        }
        throw new AssertionError(tool + ": the result has no text content block: " + content);
    }

    /** Текстовый блок, разобранный как JSON-объект, — так команды отдают свои данные. */
    @SuppressWarnings("unchecked")
    Map<String, Object> json() {
        String text = text();
        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (IllegalArgumentException e) {
            throw new AssertionError(tool + ": the text block is not valid JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map)) {
            throw new AssertionError(tool + ": the text block is not a JSON object: " + text);
        }
        return (Map<String, Object>) parsed;
    }

    /** Блоки-изображения ({@code type: image}) в порядке ответа. */
    List<Map<String, Object>> images() {
        List<Map<String, Object>> images = new ArrayList<>();
        for (Map<String, Object> block : content) {
            if ("image".equals(block.get("type"))) {
                images.add(block);
            }
        }
        return images;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(tool).append(error ? " -> ERROR" : " -> ok");
        for (Map<String, Object> block : content) {
            if ("text".equals(block.get("type"))) {
                sb.append(' ').append(block.get("text"));
            } else {
                sb.append(" [").append(block.get("type")).append(' ').append(block.get("mimeType")).append(']');
            }
        }
        return sb.toString();
    }
}
