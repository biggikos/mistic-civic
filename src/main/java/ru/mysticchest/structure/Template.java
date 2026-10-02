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
    /** Sign markers found at save time: chest ([loot], tier in param), guard and boss spawn points. */
    public final List<Mark> marks = new ArrayList<Mark>();

    public static final class Mark {
        public final String type;       // "loot", "guard", "boss"
        public final int x, y, z;
        public final String param;      // tier id for loot, else ""
        Mark(String type, int x, int y, int z, String param) { this.type = type; this.x = x; this.y = y; this.z = z; this.param = param == null ? "" : param; }
        String encode() { return type + " " + x + " " + y + " " + z + (param.isEmpty() ? "" : " " + param); }
    }

    public int count(String type) { int n = 0; for (Mark m : marks) if (m.type.equals(type)) n++; return n; }

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
        // the chest and the sign markers stand in air cells next to the blocks: they belong to the footprint too
        minX = Math.min(minX, chestX); maxX = Math.max(maxX, chestX); minZ = Math.min(minZ, chestZ); maxZ = Math.max(maxZ, chestZ);
        height = Math.max(height, chestY + 2);
        for (Mark m : marks) {
            minX = Math.min(minX, m.x); maxX = Math.max(maxX, m.x); minZ = Math.min(minZ, m.z); maxZ = Math.max(maxZ, m.z);
            height = Math.max(height, m.y + 2);
        }
    }

    static boolean isAir(Block b) { return b.getType().name().endsWith("AIR"); }

    static boolean isMarker(Block b) {
        String n = b.getType().name();
        return n.equals("CHEST") || n.equals("TRAPPED_CHEST");
    }

    /** A sign line without colour codes; text typed through commands can arrive as {"text":"..."} or in quotes, so that is unwrapped too. */
    private static String plain(String line) {
        String t = org.bukkit.ChatColor.stripColor(line == null ? "" : line).trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\{\\s*\"text\"\\s*:\\s*\"(.*)\"\\s*\\}$").matcher(t);
        if (m.matches()) t = m.group(1).trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1).trim();
        return t;
    }

    /** Text of the first two lines of a sign, or null when the block is no sign. */
    @SuppressWarnings("deprecation")
    static String[] signLines(Block b) {
        if (!b.getType().name().contains("SIGN")) return null;
        try {
            org.bukkit.block.BlockState st = b.getState();
            if (!(st instanceof org.bukkit.block.Sign)) return null;
            String[] l = ((org.bukkit.block.Sign) st).getLines();
            return new String[]{plain(l[0]), plain(l.length > 1 ? l[1] : "")};
        } catch (Throwable t) { return null; }
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
        int[] signChest = null;
        for (int y = y2; y >= y1; y--) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    Block blk = a.getWorld().getBlockAt(x, y, z);
                    if (isAir(blk)) continue;
                    String[] sign = signLines(blk);
                    if (sign != null && sign[0].startsWith("[") && sign[0].endsWith("]")) {      // [chest] [loot] [guard] [boss]
                        String kind = sign[0].substring(1, sign[0].length() - 1).toLowerCase(java.util.Locale.ROOT);
                        int rx = x - cx, ry = y - y1, rz = z - cz;
                        if (kind.equals("chest")) { if (signChest == null) signChest = new int[]{rx, ry, rz}; continue; }
                        if (kind.equals("loot") || kind.equals("guard") || kind.equals("boss")) {
                            t.marks.add(new Mark(kind, rx, ry, rz, kind.equals("loot") ? sign[1].toLowerCase(java.util.Locale.ROOT).replace(' ', '_') : ""));
                            continue;
                        }
                    }
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
        if (!chest && signChest != null) { chest = true; t.chestX = signChest[0]; t.chestY = signChest[1]; t.chestZ = signChest[2]; }
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
        if (!marks.isEmpty()) {
            List<String> mk = new ArrayList<String>();
            for (Mark m : marks) mk.add(m.encode());
            y.set("marks", mk);
        }
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
            for (String s : y.getStringList("marks")) {
                String[] p = s.trim().split(" ");
                if (p.length < 4) continue;
                int mx = Integer.parseInt(p[1]), my = Integer.parseInt(p[2]), mz = Integer.parseInt(p[3]);
                if (Math.abs(mx) > 48 || Math.abs(mz) > 48 || my < 0 || my > 64) throw new IllegalArgumentException("mark out of range: " + s);
                if (!(p[0].equals("loot") || p[0].equals("guard") || p[0].equals("boss"))) continue;
                t.marks.add(new Mark(p[0], mx, my, mz, p.length > 4 ? p[4] : ""));
            }
            for (String s : y.getStringList("palette")) t.palette.add(Snap.decode(s));
            for (String l : y.getStringList("blocks")) {
                String[] p = l.split(" ");
                int[] c = {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])};
                // files can come from other servers (/mystic structure import): refuse anything outside the saveable size
                if (c[3] < 0 || c[3] >= t.palette.size() || Math.abs(c[0]) > 48 || Math.abs(c[2]) > 48 || c[1] < 0 || c[1] > 64)
                    throw new IllegalArgumentException("block out of range: " + l);
                t.cells.add(c);
                if (t.cells.size() > 80000) throw new IllegalArgumentException("too many blocks");
            }
            t.bounds();
            return t;
        } catch (Exception e) {
            log.warning("Cannot read structure '" + name + "': " + e);
            return null;
        }
    }
}
