package ru.mysticchest.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final boolean HEX_SUPPORTED = detectHex();

    private Text() {}

    private static boolean detectHex() {
        try {
            ChatColor.class.getMethod("of", String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static String color(String s) {
        if (s == null) return "";
        if (HEX_SUPPORTED) {
            Matcher m = HEX.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                StringBuilder r = new StringBuilder("§x");
                for (char c : m.group(1).toCharArray()) r.append('§').append(c);
                m.appendReplacement(sb, r.toString());
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static final boolean LONG_TITLES = detectLongTitles();

    private static boolean detectLongTitles() {
        // inventory titles are capped at 32 chars before 1.14
        try {
            String v = org.bukkit.Bukkit.getBukkitVersion();   // 1.12.2-R0.1-SNAPSHOT, or 26.1.2.build.N-stable since the year-based versions
            String[] p = v.split("[.-]");
            if (Integer.parseInt(p[0]) >= 26) return true;
            return Integer.parseInt(p[1]) >= 14;
        } catch (Exception e) {
            return false;
        }
    }

    public static String title(String s) {
        if (LONG_TITLES || s.length() <= 32) return s;
        String cut = s.substring(0, 32);
        return cut.endsWith("§") ? cut.substring(0, 31) : cut;
    }

    public static String format(long n) {
        return String.format("%,d", n).replace(',', ' ');
    }
}
