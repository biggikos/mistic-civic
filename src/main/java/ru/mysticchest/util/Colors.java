package ru.mysticchest.util;

import org.bukkit.Color;

/** Colour helpers: tier colour from the first &-code of its name, "#rrggbb" parsing. */
public final class Colors {
    private static final String CODES = "0123456789abcdef";
    private static final int[] RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private Colors() {}

    /** h, s, v in 0..1. */
    public static Color hsv(double h, double sat, double v) {
        h = h - Math.floor(h);
        double c = v * sat, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c, r, g, b;
        int sector = (int) (h * 6);
        switch (sector) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x;
        }
        return Color.fromRGB((int) Math.round((r + m) * 255), (int) Math.round((g + m) * 255), (int) Math.round((b + m) * 255));
    }

    public static Color random() { return hsv(java.util.concurrent.ThreadLocalRandom.current().nextDouble(), 0.85 + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.15, 1.0); }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Color parse(String s, Color def) {
        if (s == null) return def;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return def;
        try { return Color.fromRGB(Integer.parseInt(h, 16)); } catch (NumberFormatException e) { return def; }
    }

    /** First legacy colour code in a name ("&6Rich" or "§6Rich") or {@code def}. */
    public static Color fromName(String name, Color def) {
        if (name == null) return def;
        for (int i = 0; i + 1 < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '&' || c == '§') {
                int k = CODES.indexOf(Character.toLowerCase(name.charAt(i + 1)));
                if (k >= 0 && k != 0) return Color.fromRGB(RGB[k]);
            }
        }
        return def;
    }
}
