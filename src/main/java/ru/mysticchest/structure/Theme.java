package ru.mysticchest.structure;

import java.util.Locale;

/** Colour scheme of a structure: four materials, each with fallbacks for older servers. */
public enum Theme {
    DESERT("SANDSTONE", "RED_SANDSTONE", "GOLD_BLOCK", "GLOWSTONE"),
    STONE("STONE_BRICKS", "MOSSY_STONE_BRICKS", "CHISELED_STONE_BRICKS", "GLOWSTONE"),
    NETHER("NETHER_BRICKS", "RED_NETHER_BRICKS", "OBSIDIAN", "GLOWSTONE"),
    END("END_STONE_BRICKS", "PURPUR_BLOCK", "OBSIDIAN", "SEA_LANTERN"),
    FROST("PACKED_ICE", "SNOW_BLOCK", "BLUE_ICE", "SEA_LANTERN"),
    OCEAN("PRISMARINE_BRICKS", "PRISMARINE", "DARK_PRISMARINE", "SEA_LANTERN");

    private final String[][] names;
    private Mat[] cache;

    Theme(String base, String accent, String trim, String light) {
        // trim falls back to the base material when a block is missing on old versions
        names = new String[][]{{base, "STONE_BRICKS"}, {accent, base}, {trim, base}, {light, "GLOWSTONE"}};
    }

    public Mat mat(Canvas.Slot s) {
        if (cache == null) {
            Mat[] c = new Mat[4];
            for (int i = 0; i < 4; i++) c[i] = Mat.of(names[i]);
            cache = c;
        }
        switch (s) {
            case ACCENT: return cache[1];
            case TRIM: return cache[2];
            case LIGHT: return cache[3];
            default: return cache[0];
        }
    }

    public static Theme parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    /** Pick a fitting theme from the biome / world name (both toString'd, so it works with enum and registry biomes). */
    public static Theme forBiome(String biome, String environment) {
        String b = (biome == null ? "" : biome).toUpperCase(Locale.ROOT), e = (environment == null ? "" : environment).toUpperCase(Locale.ROOT);
        if (e.contains("NETHER")) return NETHER;
        if (e.contains("END")) return END;
        if (b.contains("DESERT") || b.contains("BADLANDS") || b.contains("MESA") || b.contains("SAVANNA")) return DESERT;
        if (b.contains("SNOW") || b.contains("ICE") || b.contains("FROZEN") || b.contains("COLD")) return FROST;
        if (b.contains("OCEAN") || b.contains("BEACH") || b.contains("RIVER") || b.contains("SWAMP")) return OCEAN;
        return STONE;
    }
}
