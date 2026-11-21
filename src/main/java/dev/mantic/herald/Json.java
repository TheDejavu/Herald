package dev.mantic.herald;

import java.util.List;

/** Minimal JSON object writer: just enough for webhook payloads, so the library needs no JSON dependency. */
final class Json {
    private final StringBuilder sb = new StringBuilder("{");

    /** Null values are omitted. */
    Json put(String key, String value) {
        if (value != null) string(key(key), value);
        return this;
    }

    Json put(String key, boolean value) {
        key(key).append(value);
        return this;
    }

    Json put(String key, int value) {
        key(key).append(value);
        return this;
    }

    /** Adds already-serialized JSON (an object or array); null is omitted. */
    Json raw(String key, String json) {
        if (json != null) key(key).append(json);
        return this;
    }

    String end() {
        return sb.append('}').toString();
    }

    static String array(List<String> jsonValues) {
        return "[" + String.join(",", jsonValues) + "]";
    }

    static String quote(String s) {
        return string(new StringBuilder(), s).toString();
    }

    private StringBuilder key(String key) {
        if (sb.length() > 1) sb.append(',');
        return string(sb, key).append(':');
    }

    private static StringBuilder string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    // Control characters must be escaped; U+2028/2029 are escaped too since some parsers reject them raw.
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"');
    }
}
