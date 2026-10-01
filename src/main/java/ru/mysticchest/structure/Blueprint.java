package ru.mysticchest.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A structure ready to build: relative cells with their blocks, the chest cell, bounds and clearing rules. */
final class Blueprint {
    final List<int[]> xyz = new ArrayList<int[]>();
    final List<Placer> placers = new ArrayList<Placer>();
    final Set<Long> occupied = new HashSet<Long>();
    final Map<Long, Placer> foundation = new HashMap<Long, Placer>();   // column (dx,dz) -> block to extend downwards
    Placer defaultFoundation;
    int minX, maxX, minZ, maxZ, height, chestX, chestY, chestZ;
    /** true: everything inside the volume is cleared (built-in shapes). false: only trees/grass are, terrain stays. */
    boolean clearVolume;
    String name = "";

    private void add(int x, int y, int z, Placer p) {
        xyz.add(new int[]{x, y, z});
        placers.add(p);
        occupied.add(Canvas.key(x, y, z));
        if (y == 0) foundation.put(Canvas.key(x, 0, z), p);
    }

    static Blueprint of(Canvas cv, Theme theme) {
        Blueprint b = new Blueprint();
        for (java.util.Map.Entry<Long, Canvas.Slot> e : cv.cells().entrySet()) {
            if (e.getValue() == Canvas.Slot.AIR) continue;
            b.add(Canvas.kx(e.getKey()), Canvas.ky(e.getKey()), Canvas.kz(e.getKey()), theme.mat(e.getValue()));
        }
        // foundation columns only under solid floor blocks, and always the theme's base material
        Placer base = theme.mat(Canvas.Slot.BASE);
        b.defaultFoundation = base;
        for (Long k : new ArrayList<Long>(b.foundation.keySet())) b.foundation.put(k, base);
        b.minX = cv.minX(); b.maxX = cv.maxX(); b.minZ = cv.minZ(); b.maxZ = cv.maxZ(); b.height = cv.height();
        b.chestX = cv.chestX; b.chestY = cv.chestY; b.chestZ = cv.chestZ;
        b.clearVolume = true;
        return b;
    }

    static Blueprint of(Template t, int rot) {
        Blueprint b = new Blueprint();
        Snap[] rotated = new Snap[t.palette.size()];
        for (int i = 0; i < rotated.length; i++) rotated[i] = t.palette.get(i).rotate(rot);
        for (int[] c : t.cells) {
            int[] r = turn(c[0], c[2], rot);
            b.add(r[0], c[1], r[1], rotated[c[3]]);
        }
        int[] ch = turn(t.chestX, t.chestZ, rot);
        b.chestX = ch[0]; b.chestY = t.chestY; b.chestZ = ch[1];
        b.minX = Integer.MAX_VALUE; b.minZ = Integer.MAX_VALUE; b.maxX = Integer.MIN_VALUE; b.maxZ = Integer.MIN_VALUE;
        for (int[] p : b.xyz) {
            b.minX = Math.min(b.minX, p[0]); b.maxX = Math.max(b.maxX, p[0]);
            b.minZ = Math.min(b.minZ, p[2]); b.maxZ = Math.max(b.maxZ, p[2]);
        }
        b.height = t.height;
        b.clearVolume = false;
        return b;
    }

    /** Rotates (x, z) by quarter turns clockwise. */
    static int[] turn(int x, int z, int quarters) {
        switch (((quarters % 4) + 4) % 4) {
            case 1: return new int[]{-z, x};
            case 2: return new int[]{-x, -z};
            case 3: return new int[]{z, -x};
            default: return new int[]{x, z};
        }
    }
}
