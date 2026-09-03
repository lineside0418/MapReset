package io.github.mapreset.io;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small strict codec for MapReset's own fixed JSON schemas; no runtime library is required. */
public final class JsonFiles {
    private JsonFiles() { }
    public static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    public static String string(String json, String key) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        if (!m.find()) throw new IllegalArgumentException("Missing JSON string: " + key);
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
    public static long number(String json, String key) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!m.find()) throw new IllegalArgumentException("Missing JSON number: " + key);
        return Long.parseLong(m.group(1));
    }
    public static String arrayBody(String json, String key) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        if (!m.find()) throw new IllegalArgumentException("Missing JSON array: " + key);
        return m.group(1);
    }
    public static List<String> stringArray(String json, String key) {
        String body = arrayBody(json, key); List<String> values = new ArrayList<>();
        Matcher m = Pattern.compile("\\\"([^\\\"]*)\\\"").matcher(body);
        while (m.find()) values.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        return values;
    }
}
