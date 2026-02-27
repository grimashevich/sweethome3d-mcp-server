package com.sh3d.mcp.protocol;

import java.util.List;
import java.util.Map;

/**
 * Утилиты JSON-парсинга и форматирования.
 * Переиспользуются в JsonRpcProtocol (HTTP MCP) и других компонентах.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    /**
     * Парсит JSON-строку в Java-объект (Map, List, String, Number, Boolean, null).
     */
    public static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid JSON: empty input");
        }
        JsonReader reader = new JsonReader(json);
        Object parsed = reader.readValue();
        reader.skipWhitespace();
        if (reader.hasMore()) {
            throw new IllegalArgumentException("Invalid JSON: unexpected content after root value");
        }
        return parsed;
    }

    /**
     * Сериализует Java-объект в JSON-строку (одна строка, без переносов).
     */
    public static String serialize(Object value) {
        StringBuilder sb = new StringBuilder();
        appendValue(sb, value);
        return sb.toString();
    }

    /**
     * Сериализует Java-объект в отформатированную JSON-строку (2 пробела на уровень).
     */
    public static String serializePretty(Object value) {
        StringBuilder sb = new StringBuilder();
        appendPretty(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            appendString(sb, (String) value);
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            appendObject(sb, (Map<String, Object>) value);
        } else if (value instanceof List) {
            appendArray(sb, (List<Object>) value);
        } else {
            appendString(sb, value.toString());
        }
    }

    public static void appendString(StringBuilder sb, String str) {
        sb.append('"');
        if (str != null) {
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                switch (c) {
                    case '"':  sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n");  break;
                    case '\r': sb.append("\\r");  break;
                    case '\t': sb.append("\\t");  break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
        }
        sb.append('"');
    }

    static void appendObject(StringBuilder sb, Map<String, Object> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, entry.getKey());
            sb.append(':');
            appendValue(sb, entry.getValue());
        }
        sb.append('}');
    }

    static void appendArray(StringBuilder sb, List<Object> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendValue(sb, list.get(i));
        }
        sb.append(']');
    }

    @SuppressWarnings("unchecked")
    private static void appendPretty(StringBuilder sb, Object value, int indent) {
        if (value instanceof Map) {
            appendPrettyObject(sb, (Map<String, Object>) value, indent);
        } else if (value instanceof List) {
            appendPrettyArray(sb, (List<Object>) value, indent);
        } else if (value instanceof String) {
            appendString(sb, (String) value);
        } else if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            appendString(sb, value.toString());
        }
    }

    private static void appendPrettyObject(StringBuilder sb, Map<String, Object> map, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int childIndent = indent + 2;
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            appendIndent(sb, childIndent);
            appendString(sb, entry.getKey());
            sb.append(": ");
            appendPretty(sb, entry.getValue(), childIndent);
        }
        sb.append('\n');
        appendIndent(sb, indent);
        sb.append('}');
    }

    private static void appendPrettyArray(StringBuilder sb, List<Object> list, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        int childIndent = indent + 2;
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",\n");
            }
            appendIndent(sb, childIndent);
            appendPretty(sb, list.get(i), childIndent);
        }
        sb.append('\n');
        appendIndent(sb, indent);
        sb.append(']');
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
    }

    /**
     * Recursive descent JSON parser.
     * Supports: String, Number (int/double/long), Boolean, Null, Object, Array.
     * Enforces a maximum nesting depth of {@link #MAX_DEPTH} to guard against
     * stack overflow from deeply nested or malicious input.
     */
    static final class JsonReader {
        private static final int MAX_DEPTH = 32;

        private final String src;
        private int pos;
        private int depth;

        JsonReader(String src) {
            this.src = src;
            this.pos = 0;
            this.depth = 0;
        }

        boolean hasMore() {
            return pos < src.length();
        }

        /** Reads the next JSON value (object, array, string, number, boolean, or null). */
        Object readValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw error("Unexpected end of input");
            }
            char c = src.charAt(pos);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't' || c == 'f') return readBoolean();
            if (c == 'n') return readNull();
            if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
            throw error("Unexpected character '" + c + "'");
        }

        /** Reads a JSON object ({@code {...}}) into a {@link java.util.LinkedHashMap}. */
        private Map<String, Object> readObject() {
            if (++depth > MAX_DEPTH) {
                throw error("Nesting depth exceeds maximum of " + MAX_DEPTH);
            }
            expect('{');
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                depth--;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                if (pos >= src.length()) {
                    throw error("Unterminated object");
                }
                if (src.charAt(pos) == '}') {
                    pos++;
                    depth--;
                    return map;
                }
                expect(',');
            }
        }

        /** Reads a JSON array ({@code [...]}) into a {@link java.util.ArrayList}. */
        private List<Object> readArray() {
            if (++depth > MAX_DEPTH) {
                throw error("Nesting depth exceeds maximum of " + MAX_DEPTH);
            }
            expect('[');
            List<Object> list = new java.util.ArrayList<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                if (pos >= src.length()) {
                    throw error("Unterminated array");
                }
                if (src.charAt(pos) == ']') {
                    pos++;
                    depth--;
                    return list;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw error("Unterminated escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw error("Unterminated \\uXXXX");
                            String hex = src.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("Invalid \\u escape: " + hex);
                            }
                            pos += 4;
                            break;
                        default:
                            throw error("Invalid escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Number readNumber() {
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') pos++;
            while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            String numStr = src.substring(start, pos);
            try {
                if (isFloat) {
                    return Double.parseDouble(numStr);
                }
                long val = Long.parseLong(numStr);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return (int) val;
                }
                return val;
            } catch (NumberFormatException e) {
                throw error("Invalid number: " + numStr);
            }
        }

        private Boolean readBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("Expected boolean");
        }

        private Object readNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("Expected null");
        }

        void skipWhitespace() {
            while (pos < src.length() && src.charAt(pos) <= ' ') {
                pos++;
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != expected) {
                char actual = pos < src.length() ? src.charAt(pos) : '?';
                throw error("Expected '" + expected + "' but got '" + actual + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Invalid JSON: " + msg + " at position " + pos);
        }
    }
}
