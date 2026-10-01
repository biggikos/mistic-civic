package ru.mysticchest.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Text-level helpers for upgrading user yml files WITHOUT rewriting them through the Bukkit API
 * (which drops comments on 1.12-1.17). A "block" is a top-level key together with the column-0
 * comment lines right above it and everything indented below it.
 */
public final class YamlBlocks {
    private YamlBlocks() {}

    public static Map<String, String> topLevelBlocks(String text) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        String[] lines = text.split("\r?\n", -1);
        List<String> pending = new ArrayList<String>();
        String key = null;
        StringBuilder cur = null;
        for (String line : lines) {
            String k = topKey(line);
            if (k != null) {
                if (key != null) out.put(key, cur.toString());
                key = k;
                cur = new StringBuilder();
                boolean lead = true;
                for (String p : pending) {
                    if (lead && p.isEmpty()) continue;     // blank separator lines belong to the file, not the block
                    lead = false;
                    cur.append(p).append('\n');
                }
                pending.clear();
                cur.append(line).append('\n');
            } else if (line.isEmpty() || line.charAt(0) == '#') {
                pending.add(line);
            } else if (cur != null) {
                for (String p : pending) cur.append(p).append('\n');
                pending.clear();
                cur.append(line).append('\n');
            }
        }
        if (key != null) out.put(key, cur.toString());
        return out;
    }

    private static String topKey(String line) {
        if (line.isEmpty()) return null;
        char c = line.charAt(0);
        if (!(Character.isLetterOrDigit(c) || c == '_')) return null;
        int colon = line.indexOf(':');
        if (colon <= 0) return null;
        for (int i = 0; i < colon; i++) {
            char ch = line.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '-')) return null;
        }
        return line.substring(0, colon);
    }

    /** Appends every top-level block that the template has and the user file lacks. */
    public static String appendMissing(String userText, String templateText, Set<String> added) {
        Map<String, String> have = topLevelBlocks(userText);
        StringBuilder sb = new StringBuilder(userText);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        for (Map.Entry<String, String> e : topLevelBlocks(templateText).entrySet()) {
            if (have.containsKey(e.getKey())) continue;
            sb.append('\n').append(e.getValue());
            if (added != null) added.add(e.getKey());
        }
        return sb.toString();
    }
}
