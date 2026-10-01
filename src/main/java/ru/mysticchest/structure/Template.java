package ru.mysticchest.structure;

import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A structure saved in game with /mystic structure save. Blocks are stored relative to the centre of the
 * selection (x/z) and its lowest layer (y = 0), the chest position is marked by a chest block that was inside
 * the selection. Air is not stored, so the surrounding terrain stays untouched.
 */
public final class Template {
    public final String name;
    final List<int[]> cells = new ArrayList<int[]>();     // dx, dy, dz, paletteIndex
    final List<Snap> palette = new ArrayList<Snap>();
    public int chestX, chestY, chestZ;
    public int minX, maxX, minZ, maxZ, height;

    Template(String name) { this.name = name; }

    public int blocks() { return cells.size(); }
    public int width() { return maxX - minX + 1; }
    public int depth() { return maxZ - minZ + 1; }

    private void bounds() {
        minX = minZ = Integer.MAX_VALUE; maxX = maxZ = Integer.MIN_VALUE; height = 0;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
            height = Math.max(height, c[1] + 2);
        }
        if (cells.isEmpty()) { minX = maxX = minZ = maxZ = 0; }
    }

    static boolean isAir(Block b) { return b.getType().name().endsWith("AIR"); }

    static boolean isMarker(Block b) {
        String n = b.getType().name();
        return n.equals("CHEST") || n.equals("TRAPPED_CHEST");
    }

    /** Reads a cuboid. @return null (and fills {@code error}) when it cannot be saved. */
    public static Template capture(String name, Block a, Block b, String[] error) {
        int x1 = Math.min(a.getX(), b.getX()), x2 = Math.max(a.getX(), b.getX());
        int y1 = Math.min(a.getY(), b.getY()), y2 = Math.max(a.getY(), b.getY());
        int z1 = Math.min(a.getZ(), b.getZ()), z2 = Math.max(a.getZ(), b.getZ());
        long vol = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (x2 - x1 > 47 || z2 - z1 > 47 || y2 - y1 > 63 || vol > 80000) { error[0] = "too-big"; return null; }
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        Template t = new Template(name);
        Map<String, Integer> index = new HashMap<String, Integer>();
        boolean chest = false;
        for (int y = y2; y >= y1; y--) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    Block blk = a.getWorld().getBlockAt(x, y, z);
                    if (isAir(blk)) continue;
                    if (!chest && isMarker(blk)) {
                        chest = true;
                        t.chestX = x - cx; t.chestY = y - y1; t.chestZ = z - cz;
                        continue;
                    }
                    Snap s = Snap.of(blk);
                    String enc = s.encode();
                    Integer i = index.get(enc);
                    if (i == null) { i = t.palette.size(); t.palette.add(s); index.put(enc, i); }
                    t.cells.add(new int[]{x - cx, y - y1, z - cz, i});
                }
            }
        }
        if (!chest) { error[0] = "no-chest"; return null; }
        if (t.cells.size() < 4) { error[0] = "empty"; return null; }
        t.bounds();
        return t;
    }

    public String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("format", Mat.LEGACY ? "legacy" : "modern");
        y.set("chest", java.util.Arrays.asList(chestX, chestY, chestZ));
        List<String> pal = new ArrayList<String>();
        for (Snap s : palette) pal.add(s.encode());
        y.set("palette", pal);
        List<String> blocks = new ArrayList<String>(cells.size());
        for (int[] c : cells) blocks.add(c[0] + " " + c[1] + " " + c[2] + " " + c[3]);
        y.set("blocks", blocks);
        return "## Saved by /mystic structure save. Coordinates are relative to the centre of the selection,\n"
                + "## y = 0 is its lowest layer. The chest cell is where mystic chests appear.\n" + y.saveToString();
    }

    /** @return null when the file is unreadable or was saved on an incompatible server version. */
    public static Template load(String name, File f, java.util.logging.Logger log) {
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            boolean legacy = "legacy".equals(y.getString("format", "modern"));
            if (legacy != Mat.LEGACY) {
                log.warning("Structure '" + name + "' was saved on a " + (legacy ? "1.12" : "1.13+") + " server and cannot be used here.");
                return null;
            }
            Template t = new Template(name);
            List<Integer> ch = y.getIntegerList("chest");
            if (ch.size() != 3) return null;
            t.chestX = ch.get(0); t.chestY = ch.get(1); t.chestZ = ch.get(2);
            for (String s : y.getStringList("palette")) t.palette.add(Snap.decode(s));
            for (String l : y.getStringList("blocks")) {
                String[] p = l.split(" ");
                t.cells.add(new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])});
            }
            t.bounds();
            return t;
        } catch (Exception e) {
            log.warning("Cannot read structure '" + name + "': " + e);
            return null;
        }
    }
}
