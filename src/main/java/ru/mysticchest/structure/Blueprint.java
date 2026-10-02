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
    final List<int[]> extraChests = new ArrayList<int[]>();
    int minX, maxX, minZ, maxZ, height, chestX, chestY, chestZ;
    /** true: everything inside the volume is cleared (built-in shapes). false: only trees/grass are, terrain stays. */
    boolean clearVolume;
    String name = "";
    int[] crater;
    final List<Template.Mark> marks = new ArrayList<Template.Mark>();
    final Set<Long> markCells = new HashSet<Long>();
    /** Rubble materials: base, accent, trim, light. */
    Placer[] debris;

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
        Placer base = theme.mat(cv.foundation);
        b.defaultFoundation = base;
        for (Long k : new ArrayList<Long>(b.foundation.keySet())) b.foundation.put(k, base);
        b.minX = cv.minX(); b.maxX = cv.maxX(); b.minZ = cv.minZ(); b.maxZ = cv.maxZ(); b.height = cv.height();
        b.chestX = cv.chestX; b.chestY = cv.chestY; b.chestZ = cv.chestZ;
        b.extraChests.addAll(cv.extraChests);
        b.clearVolume = true;
        b.crater = cv.crater;
        b.debris = new Placer[]{theme.mat(cv.debris[0]), theme.mat(cv.debris[1]), theme.mat(cv.debris[2]), theme.mat(cv.debris[3])};
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
        for (Template.Mark m : t.marks) { int[] r = turn(m.x, m.z, rot); b.marks.add(new Template.Mark(m.type, r[0], m.y, r[1], m.param)); b.markCells.add(Canvas.key(r[0], m.y, r[1])); }
        int[] ch = turn(t.chestX, t.chestZ, rot);
        b.chestX = ch[0]; b.chestY = t.chestY; b.chestZ = ch[1];
        b.minX = Integer.MAX_VALUE; b.minZ = Integer.MAX_VALUE; b.maxX = Integer.MIN_VALUE; b.maxZ = Integer.MIN_VALUE;
        for (int[] p : b.xyz) {
            b.minX = Math.min(b.minX, p[0]); b.maxX = Math.max(b.maxX, p[0]);
            b.minZ = Math.min(b.minZ, p[2]); b.maxZ = Math.max(b.maxZ, p[2]);
        }
        b.minX = Math.min(b.minX, Math.min(b.chestX, minOf(b.marks, true))); b.maxX = Math.max(b.maxX, Math.max(b.chestX, maxOf(b.marks, true)));
        b.minZ = Math.min(b.minZ, Math.min(b.chestZ, minOf(b.marks, false))); b.maxZ = Math.max(b.maxZ, Math.max(b.chestZ, maxOf(b.marks, false)));
        b.height = t.height;
        b.clearVolume = false;
        b.debris = debrisOf(t, rotated);
        return b;
    }

    private static int minOf(List<Template.Mark> l, boolean x) { int v = Integer.MAX_VALUE; for (Template.Mark m : l) v = Math.min(v, x ? m.x : m.z); return v; }
    private static int maxOf(List<Template.Mark> l, boolean x) { int v = Integer.MIN_VALUE; for (Template.Mark m : l) v = Math.max(v, x ? m.x : m.z); return v; }

    /** Rubble of a saved structure: its own three most used blocks (no doors, torches, glass...). */
    private static Placer[] debrisOf(Template t, Snap[] palette) {
        int[] uses = new int[palette.length];
        for (int[] c : t.cells) uses[c[3]]++;
        String[] skip = {"door", "torch", "sign", "bed", "chest", "lantern", "button", "pane", "glass", "ladder", "fence", "trapdoor", "carpet", "banner", "air", "leaves", "vine", "lever", "rail", "flower", "head", "pot", "water", "lava", "fire", "web"};
        Placer[] best = new Placer[3];
        int[] bu = new int[3];
        for (int i = 0; i < palette.length; i++) {
            String n = palette[i].encode().toLowerCase();
            boolean bad = false;
            for (String k : skip) if (n.contains(k)) { bad = true; break; }
            if (bad) continue;
            for (int r = 0; r < 3; r++) {
                if (uses[i] > bu[r]) {
                    for (int q = 2; q > r; q--) { best[q] = best[q - 1]; bu[q] = bu[q - 1]; }
                    best[r] = palette[i]; bu[r] = uses[i];
                    break;
                }
            }
        }
        if (best[0] == null) return null;                       // nothing usable: the caller falls back to the theme
        Placer a = best[1] != null ? best[1] : best[0], tr = best[2] != null ? best[2] : best[0];
        return new Placer[]{best[0], a, tr, best[0]};
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
