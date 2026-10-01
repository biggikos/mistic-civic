package ru.mysticchest.structure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** A structure drawn in relative coordinates (x/z around the centre, y = 0 is the ground plane). No Bukkit here. */
public final class Canvas {
    public enum Slot { AIR, BASE, ACCENT, TRIM, LIGHT }

    private final Map<Long, Slot> cells = new LinkedHashMap<Long, Slot>();
    public final Random rnd;
    public final double decay;
    public int chestX, chestY, chestZ;
    private int minX = 0, maxX = 0, minZ = 0, maxZ = 0, maxY = 0;

    public Canvas(Random rnd, double decay) {
        this.rnd = rnd;
        this.decay = decay;
    }

    public static long key(int x, int y, int z) {
        return (((long) (x + 512) & 0x3FFL) << 40) | (((long) (z + 512) & 0x3FFL) << 20) | ((long) (y + 64) & 0xFFFFL);
    }

    public static int kx(long k) { return (int) ((k >> 40) & 0x3FF) - 512; }
    public static int kz(long k) { return (int) ((k >> 20) & 0x3FF) - 512; }
    public static int ky(long k) { return (int) (k & 0xFFFF) - 64; }

    public void set(int x, int y, int z, Slot s) {
        cells.put(key(x, y, z), s);
        if (s != Slot.AIR) {
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            maxY = Math.max(maxY, y);
        }
    }

    /** Like set, but the block may be missing in an "ancient" structure (probability = decay). */
    public void worn(int x, int y, int z, Slot s) {
        if (decay > 0 && rnd.nextDouble() < decay) return;
        set(x, y, z, s);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Slot s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
    }

    public void wornFill(int x1, int y1, int z1, int x2, int y2, int z2, Slot s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) worn(x, y, z, s);
    }

    public void chest(int x, int y, int z) { chestX = x; chestY = y; chestZ = z; set(x, y, z, Slot.AIR); }

    public Slot get(int x, int y, int z) {
        Slot s = cells.get(key(x, y, z));
        return s == null ? Slot.AIR : s;
    }

    public Map<Long, Slot> cells() { return cells; }
    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }
    public int height() { return maxY + 2; }
    public int count() {
        int n = 0;
        for (Slot s : cells.values()) if (s != Slot.AIR) n++;
        return n;
    }
}
