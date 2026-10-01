package ru.mysticchest.util;

import java.util.ArrayList;
import java.util.List;

/** Pre-parsed "{key}" template: parsed once, applied without regex. */
public final class Template {
    private final String[] lits;
    private final String[] keys;

    private Template(String[] lits, String[] keys) {
        this.lits = lits;
        this.keys = keys;
    }

    public static Template parse(String s) {
        List<String> lits = new ArrayList<String>();
        List<String> keys = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '{') {
                int end = s.indexOf('}', i + 1);
                if (end > i + 1 && isKey(s, i + 1, end)) {
                    lits.add(cur.toString());
                    cur.setLength(0);
                    keys.add(s.substring(i + 1, end));
                    i = end + 1;
                    continue;
                }
            }
            cur.append(c);
            i++;
        }
        lits.add(cur.toString());
        return new Template(lits.toArray(new String[0]), keys.toArray(new String[0]));
    }

    private static boolean isKey(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) return false;
        }
        return true;
    }

    /** @param kv alternating key, value pairs; unknown keys are left as {key}. */
    public String apply(String... kv) {
        if (keys.length == 0) return lits[0];
        StringBuilder sb = new StringBuilder(lits[0].length() + 16 * keys.length);
        sb.append(lits[0]);
        for (int i = 0; i < keys.length; i++) {
            String v = null;
            for (int j = 0; j + 1 < kv.length; j += 2) {
                if (keys[i].equals(kv[j])) { v = kv[j + 1]; break; }
            }
            if (v == null) sb.append('{').append(keys[i]).append('}'); else sb.append(v);
            sb.append(lits[i + 1]);
        }
        return sb.toString();
    }
}
