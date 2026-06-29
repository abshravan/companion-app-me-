package protocol;

import java.util.Hashtable;
import java.util.Vector;

/**
 * A tiny JSON reader/writer written for CLDC 1.1 / MIDP 2.0.
 *
 * Why hand-rolled: Java ME ships no JSON library, the heap is small, and pulling
 * a third-party parser would bloat the jar. The wire protocol is deliberately
 * flat (see docs/protocol/PROTOCOL.md), so a compact recursive-descent parser is
 * enough and stays cheap.
 *
 * CLDC constraints honoured here:
 *   - no generics, no enhanced-for
 *   - StringBuffer instead of StringBuilder
 *   - java.util.Hashtable / Vector instead of the Collections framework
 *
 * Parsed objects are Hashtables. Values are String, Boolean, Long (numbers —
 * see {@link #parseNumber()}), Hashtable (nested object) or Vector (array). JSON
 * null values are dropped (a Hashtable cannot store null), which matches the
 * spec's "missing field -> use default" rule.
 *
 * Numbers are always returned as {@link Long}: CLDC 1.0 has no {@code Double},
 * and the protocol only uses integer fields, so avoiding floating point keeps
 * the client loadable on the widest range of devices.
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String text) {
        this.s = text;
        this.i = 0;
    }

    /**
     * Parse one line into a JSON object.
     *
     * @return the parsed Hashtable, or null if the line is not a valid JSON
     *         object. Never throws, so callers implement the spec's "drop
     *         malformed line" rule simply by checking for null.
     */
    public static Hashtable parseObject(String line) {
        if (line == null) {
            return null;
        }
        try {
            Json p = new Json(line);
            Object value = p.parseValue();
            p.skipWhitespace();
            if (!p.atEnd()) {
                return null; // trailing characters
            }
            if (value instanceof Hashtable) {
                return (Hashtable) value;
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Escape a string's special characters for inclusion in a JSON line. */
    public static String escape(String value) {
        StringBuffer sb = new StringBuffer(value.length() + 8);
        appendEscaped(sb, value);
        return sb.toString();
    }

    /** Append {@code "value"} (quoted and escaped) to {@code sb}. */
    public static void appendEscaped(StringBuffer sb, String value) {
        sb.append('"');
        int n = value.length();
        for (int k = 0; k < n; k++) {
            char c = value.charAt(k);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int pad = hex.length(); pad < 4; pad++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---- recursive descent ----

    private Object parseValue() {
        skipWhitespace();
        if (atEnd()) {
            throw new RuntimeException("empty");
        }
        char c = s.charAt(i);
        if (c == '{') {
            return parseObjectValue();
        } else if (c == '[') {
            return parseArray();
        } else if (c == '"') {
            return parseString();
        } else if (c == 't' || c == 'f') {
            return parseBoolean();
        } else if (c == 'n') {
            parseNull();
            return null;
        } else {
            return parseNumber();
        }
    }

    private Hashtable parseObjectValue() {
        expect('{');
        Hashtable map = new Hashtable();
        skipWhitespace();
        if (peek() == '}') {
            i++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new RuntimeException("expected key");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            if (value != null) {
                map.put(key, value);
            }
            skipWhitespace();
            char c = next();
            if (c == ',') {
                continue;
            } else if (c == '}') {
                return map;
            } else {
                throw new RuntimeException("expected , or }");
            }
        }
    }

    private Vector parseArray() {
        expect('[');
        Vector list = new Vector();
        skipWhitespace();
        if (peek() == ']') {
            i++;
            return list;
        }
        while (true) {
            Object value = parseValue();
            list.addElement(value);
            skipWhitespace();
            char c = next();
            if (c == ',') {
                continue;
            } else if (c == ']') {
                return list;
            } else {
                throw new RuntimeException("expected , or ]");
            }
        }
    }

    private String parseString() {
        expect('"');
        StringBuffer sb = new StringBuffer();
        while (true) {
            if (atEnd()) {
                throw new RuntimeException("unterminated string");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            } else if (c == '\\') {
                sb.append(parseEscape());
            } else {
                sb.append(c);
            }
        }
    }

    private char parseEscape() {
        if (atEnd()) {
            throw new RuntimeException("bad escape");
        }
        char c = s.charAt(i++);
        switch (c) {
            case '"':  return '"';
            case '\\': return '\\';
            case '/':  return '/';
            case 'n':  return '\n';
            case 'r':  return '\r';
            case 't':  return '\t';
            case 'b':  return '\b';
            case 'f':  return '\f';
            case 'u': {
                if (i + 4 > s.length()) {
                    throw new RuntimeException("bad unicode escape");
                }
                String hex = s.substring(i, i + 4);
                i += 4;
                return (char) Integer.parseInt(hex, 16);
            }
            default:
                throw new RuntimeException("bad escape");
        }
    }

    private Boolean parseBoolean() {
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        throw new RuntimeException("expected boolean");
    }

    private void parseNull() {
        if (!s.startsWith("null", i)) {
            throw new RuntimeException("expected null");
        }
        i += 4;
    }

    /**
     * Parse a JSON number and return it as a {@link Long}.
     *
     * CLDC 1.0 has no {@code java.lang.Double}, and referencing it would make the
     * whole class fail to load on such a device ({@code NoClassDefFoundError}).
     * The protocol only uses integer fields (version, battery level, timestamps),
     * so the full numeric token is consumed (to keep framing intact) but only its
     * integer part is converted; any fraction/exponent is discarded.
     */
    private Object parseNumber() {
        int start = i;
        if (peek() == '-') {
            i++;
        }
        int intEnd = -1; // first index of '.', 'e' or 'E' — where the integer part ends
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                i++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                if (intEnd == -1 && (c == '.' || c == 'e' || c == 'E')) {
                    intEnd = i;
                }
                i++;
            } else {
                break;
            }
        }
        if (i == start) {
            throw new RuntimeException("bad number");
        }
        String intPart = s.substring(start, intEnd == -1 ? i : intEnd);
        if (intPart.length() == 0 || intPart.equals("-")) {
            return new Long(0L);
        }
        try {
            return new Long(Long.parseLong(intPart));
        } catch (NumberFormatException e) {
            return new Long(0L);
        }
    }

    private char peek() {
        return atEnd() ? ' ' : s.charAt(i);
    }

    private char next() {
        if (atEnd()) {
            throw new RuntimeException("unexpected end");
        }
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) {
            throw new RuntimeException("expected " + c);
        }
    }

    private void skipWhitespace() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
            } else {
                break;
            }
        }
    }

    private boolean atEnd() {
        return i >= s.length();
    }
}
