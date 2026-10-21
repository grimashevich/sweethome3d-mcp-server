package com.sh3d.mcp.e2e;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный строгий JSON (RFC 8259) для живых тестов: разбор ответов сервера и запись запросов.
 * <p>
 * Разборщик плагина ({@code JsonUtil}) здесь намеренно не используется: тест выступает сторонним
 * клиентом, и ответ, который собственный разборщик плагина принял бы по ошибке (незакрытая
 * строка, {@code NaN}, лишняя запятая), обязан упасть.
 * <p>
 * Типы результата: {@code Map<String, Object>} (порядок ключей сохранён), {@code List<Object>},
 * {@code String}, {@code Long} (целое без дробной части и экспоненты), {@code Double},
 * {@code Boolean}, {@code null}.
 */
final class Json {

    private Json() {
    }

    static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON text is null");
        }
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("unexpected trailing characters");
        }
        return value;
    }

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        append(sb, value);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            appendString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON cannot represent " + d);
            }
            sb.append(d);
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                append(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                append(sb, item);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass());
        }
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        IllegalArgumentException error(String message) {
            int from = Math.max(0, pos - 20);
            int to = Math.min(text.length(), pos + 20);
            return new IllegalArgumentException("Invalid JSON at offset " + pos + ": " + message
                    + " (near \"" + text.substring(from, to) + "\")");
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            if (pos >= text.length()) {
                throw error("unexpected end of input");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw error("unexpected character '" + c + "'");
            }
        }

        private void expectLiteral(String literal) {
            if (!text.startsWith(literal, pos)) {
                throw error("expected " + literal);
            }
            pos += literal.length();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected a string key");
                }
                String key = readString();
                skipWhitespace();
                if (peek() != ':') {
                    throw error("expected ':'");
                }
                pos++;
                skipWhitespace();
                Object value = readValue();
                if (map.containsKey(key)) {
                    throw error("duplicate key \"" + key + "\"");
                }
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    pos--;
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(readValue());
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    pos--;
                    throw error("expected ',' or ']'");
                }
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw error("unexpected end of input");
            }
            return text.charAt(pos);
        }

        private String readString() {
            StringBuilder sb = new StringBuilder();
            pos++; // открывающая кавычка
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    pos--;
                    throw error("unescaped control character in string");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char escaped = peek();
                pos++;
                switch (escaped) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw error("truncated \\u escape");
                        }
                        String hex = text.substring(pos, pos + 4);
                        for (int i = 0; i < 4; i++) {
                            if (Character.digit(hex.charAt(i), 16) < 0) {
                                throw error("invalid \\u escape");
                            }
                        }
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default:
                        pos--;
                        throw error("invalid escape '\\" + escaped + "'");
                }
            }
        }

        private Object readNumber() {
            int start = pos;
            boolean integral = true;
            if (peek() == '-') {
                pos++;
            }
            if (pos >= text.length()) {
                throw error("truncated number");
            }
            if (text.charAt(pos) == '0') {
                pos++;
            } else if (text.charAt(pos) >= '1' && text.charAt(pos) <= '9') {
                skipDigits();
            } else {
                throw error("invalid number");
            }
            if (pos < text.length() && text.charAt(pos) == '.') {
                integral = false;
                pos++;
                requireDigit();
                skipDigits();
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                requireDigit();
                skipDigits();
            }
            String literal = text.substring(start, pos);
            if (integral) {
                try {
                    return Long.valueOf(literal);
                } catch (NumberFormatException e) {
                    // целое за пределами long — отдаём как double
                }
            }
            return Double.valueOf(literal);
        }

        private void requireDigit() {
            if (pos >= text.length() || text.charAt(pos) < '0' || text.charAt(pos) > '9') {
                throw error("digit expected");
            }
        }

        private void skipDigits() {
            while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                pos++;
            }
        }
    }
}
