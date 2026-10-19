package net.nico.miniloader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器。
 * 加载器要读 mod.json，但 JDK 不带 JSON 库，也刻意不引第三方依赖，所以自己写一个。
 * 只覆盖 mod.json 需要的能力：object / array / string / number / bool / null。
 */
final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos < parser.src.length()) {
            throw new IllegalArgumentException("JSON 解析失败：位置 " + parser.pos + " 后仍有多余字符");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("JSON 顶层结构不是对象");
        }
        return (Map<String, Object>) value;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private Object readValue() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("JSON 意外结束");
        }
        return switch (src.charAt(pos)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Object readLiteral(String literal, Object value) {
        if (!src.startsWith(literal, pos)) {
            throw new IllegalArgumentException("JSON 位置 " + pos + " 处期待字面量 " + literal);
        }
        pos += literal.length();
        return value;
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // 吃掉 '{'
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("JSON 对象里期待 ',' 或 '}'，实际是 '" + c + "'");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // 吃掉 '['
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("JSON 数组里期待 ',' 或 ']'，实际是 '" + c + "'");
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    String hex = src.substring(pos, pos + 4);
                    pos += 4;
                    sb.append((char) Integer.parseInt(hex, 16));
                }
                default -> throw new IllegalArgumentException("未知转义序列 \\" + escape);
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || Character.isDigit(c)) {
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) {
            throw new IllegalArgumentException("JSON 位置 " + pos + " 处不是合法值");
        }
        return Double.parseDouble(src.substring(start, pos));
    }

    private char peek() {
        return src.charAt(pos);
    }

    private char next() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("JSON 意外结束");
        }
        return src.charAt(pos++);
    }

    private void expect(char expected) {
        char actual = next();
        if (actual != expected) {
            throw new IllegalArgumentException("JSON 位置 " + (pos - 1) + " 处期待 '" + expected + "'，实际是 '" + actual + "'");
        }
    }
}
