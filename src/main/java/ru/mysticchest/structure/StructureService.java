package ru.mysticchest.structure;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.config.Cfg;
import ru.mysticchest.config.Settings;
import ru.mysticchest.core.Animator;
import ru.mysticchest.core.AsyncIO;
import ru.mysticchest.core.Scheduler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds the artistic structures around chests. Blocks are placed layer by layer by the shared Animator,
 * every replaced block is remembered (and written to data/structures so a crash can be undone), and the
 * terrain is restored when the chest is gone.
 */
public final class StructureService {
    public interface Callback { void done(Structure built); }

    public static final class Spec {
        public final StructureCatalog.Entry entry;
        public final Theme theme;   // built-in shapes only
        public final double decay;
        public final boolean rotate;
        public boolean debris;
        public int debrisRadius, debrisPieces;
        /** Quarter turns of a saved structure; -1 = random (or none when rotate is off). */
        public int turn = -1;
        Spec(StructureCatalog.Entry e, Theme t, double d, boolean r) { entry = e; theme = t; decay = d; rotate = r; }
    }

    private static final String[] PLAYER_MADE = {"CHEST", "BARREL", "FURNACE", "HOPPER", "DISPENSER", "DROPPER", "SHULKER", "_BED", "BED_BLOCK",
            "SIGN", "DOOR", "RAIL", "TORCH", "LANTERN", "BEACON", "SPAWNER", "PORTAL", "BREWING", "ENCHANT", "ANVIL", "CRAFTING", "JUKEBOX", "NOTE_BLOCK"};

    private final MysticChestPlugin plugin;
    private final List<Structure> active = new ArrayList<Structure>();
    private final StructureCatalog catalog;

    public StructureService(MysticChestPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new StructureCatalog(plugin);
    }

    public StructureCatalog catalog() { return catalog; }

    private Wand wand;
    public Wand wand() { if (wand == null) wand = new Wand(plugin); return wand; }

    /** Builds a structure at the player's feet without a chest and takes it down again after 40 seconds. */
    public void preview(final Player p, String name, String theme) {
        final Location at = p.getLocation().getBlock().getLocation();
        int turn = -1;
        if (theme != null && theme.matches("(?i)r[0-3]")) { turn = theme.charAt(1) - '0'; theme = null; }      // preview <name> r2 = turned twice
        Spec spec = resolve(null, null, null, name, theme, at);
        if (spec != null) spec.turn = turn;
        if (spec == null) { plugin.lang().send(p, "structure.unknown", "name", name); return; }
        plugin.lang().send(p, "structure.previewing", "name", spec.entry.id);
        build(spec, at, new Callback() {
            public void done(Structure st) {
                if (st == null) { plugin.lang().send(p, "structure.cannot-build"); return; }
                scheduleRestore(st, 40);
            }
        });
    }

    private File dir() { return new File(plugin.getDataFolder(), "data/structures"); }

    // ---- which structure, if any ---------------------------------------

    /**
     * @return null when this spawn gets no structure. forceName/forceTheme are for /mystic spawn and previews.
     * Filters: a profile or tier may restrict the choice with shapes: [..] / shape: name.
     */
    public Spec resolve(Cfg profile, Cfg tier, String tierId, String forceName, String forceTheme, Location at) {
        Settings s = plugin.settings();
        boolean enabled = s.structEnabled;
        int chance = s.structChance;
        List<String> filter = null;
        String theme = s.structTheme;
        double decay = s.structDecay;
        boolean debris = s.debrisEnabled;
        int dRadius = s.debrisRadius, dPieces = s.debrisPieces;
        for (Cfg lvl : new Cfg[]{profile, tier}) {
            if (lvl == null || !lvl.exists()) continue;
            enabled = lvl.bool("enabled", enabled);
            chance = lvl.integer("chance", chance, 0, 100);
            if (lvl.has("shapes")) filter = lvl.strings("shapes");
            if (lvl.has("shape")) { filter = new ArrayList<String>(); filter.add(lvl.str("shape", "")); }
            theme = lvl.str("theme", theme);
            decay = lvl.decimal("decay", decay, 0, 1);
            Cfg d = lvl.sub("debris");
            if (d.exists()) {
                debris = d.bool("enabled", debris);
                dRadius = d.integer("radius", dRadius, 4, 80);
                dPieces = d.integer("pieces", dPieces, 0, 400);
            }
        }
        if (forceName != null || forceTheme != null) {
            enabled = true;
            chance = 100;
            if (forceName != null) { filter = new ArrayList<String>(); filter.add(forceName); }
            if (forceTheme != null) theme = forceTheme;
        }
        if (!enabled || s.lowResource && forceName == null) return null;
        if (chance < 100 && ThreadLocalRandom.current().nextInt(100) >= chance) return null;
        StructureCatalog.Entry e;
        if (forceName != null) {
            e = catalog.get(forceName);                       // forcing ignores enabled/weight
        } else {
            e = catalog.pick(filter, ThreadLocalRandom.current(),
                    new StructureCatalog.Context(tierId, String.valueOf(at.getBlock().getBiome()), at.getWorld().getName()));
        }
        if (e == null) return null;
        if (forceTheme == null && e.theme != null && (tier == null || !tier.has("theme"))) theme = e.theme;
        Theme t = Theme.parse(theme);
        Theme[] all = Theme.values();
        if (theme != null && theme.equalsIgnoreCase("RANDOM")) {
            t = all[ThreadLocalRandom.current().nextInt(all.length)];
        } else if (t == null) {
            Block b = at.getBlock();
            t = Theme.forBiome(String.valueOf(b.getBiome()), String.valueOf(at.getWorld().getEnvironment()));
            // AUTO follows the biome most of the time, but every so often it is something unexpected
            if (s.structSurprise > 0 && ThreadLocalRandom.current().nextInt(100) < s.structSurprise) t = all[ThreadLocalRandom.current().nextInt(all.length)];
        }
        Spec sp = new Spec(e, t, decay, e.rotate != null ? e.rotate : s.structRotate);
        sp.debris = debris;
        sp.debrisRadius = dRadius;
        // +-40% so the amount of rubble differs between spawns too
        sp.debrisPieces = (int) Math.round(dPieces * e.debrisScale * (0.6 + ThreadLocalRandom.current().nextDouble() * 0.8));
        return sp;
    }

    // ---- building ---------------------------------------------------------

    public void build(final Spec spec, final Location center, final Callback cb) {
        if (plugin.animator().size() >= plugin.settings().maxAnimations) { cb.done(null); return; }
        final World w = center.getWorld();
        final int cx = center.getBlockX(), cz = center.getBlockZ();
        final Blueprint bp;
        if (spec.entry.custom()) {
            bp = Blueprint.of(spec.entry.template, spec.turn >= 0 ? spec.turn : (spec.rotate ? ThreadLocalRandom.current().nextInt(4) : 0));
        } else {
            Canvas cv = new Canvas(new Random(System.nanoTime() ^ (cx * 31L + cz * 17L)), spec.decay);
            spec.entry.shape.draw(cv);
            bp = Blueprint.of(cv, spec.theme);
        }
        bp.name = spec.entry.id;
        Settings st = plugin.settings();
        int blend = st.structBlend ? st.structBlendWidth : 0;
        final int minX = cx + bp.minX - 1, maxX = cx + bp.maxX + 1, minZ = cz + bp.minZ - 1, maxZ = cz + bp.maxZ + 1;
        // chunks to load and keep: the structure + terrain skirt, and (optionally) the whole rubble field
        int reach = spec.debris && st.debrisLoadChunks ? Math.max(spec.debrisRadius, blend + 2) : blend + 2;
        final List<int[]> chunks = new ArrayList<int[]>();
        int cMinX = Math.min(minX - blend - 1, cx - reach), cMaxX = Math.max(maxX + blend + 1, cx + reach);
        int cMinZ = Math.min(minZ - blend - 1, cz - reach), cMaxZ = Math.max(maxZ + blend + 1, cz + reach);
        if (!(spec.debris && st.debrisLoadChunks)) { cMinX = minX - blend - 1; cMaxX = maxX + blend + 1; cMinZ = minZ - blend - 1; cMaxZ = maxZ + blend + 1; }
        for (int x = cMinX >> 4; x <= cMaxX >> 4; x++) for (int z = cMinZ >> 4; z <= cMaxZ >> 4; z++) chunks.add(new int[]{x, z});
        ensure(w, chunks, 0, new Runnable() {
            public void run() {
                try {
                    start(spec, bp, w, cx, cz, minX, maxX, minZ, maxZ, chunks, cb);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Structure failed: " + t);
                    for (int[] c : chunks) plugin.chests().release(w, c[0], c[1]);
                    cb.done(null);
                }
            }
        });
    }

    private void ensure(final World w, final List<int[]> list, final int i, final Runnable done) {
        if (i >= list.size()) { done.run(); return; }
        int[] c = list.get(i);
        plugin.chests().hold(w, c[0], c[1]);
        plugin.locators().withChunk(w, c[0], c[1], new Runnable() {
            public void run() { ensure(w, list, i + 1, done); }
        });
    }

    private static boolean isLiquid(Placer p) { return p instanceof Mat && ((Mat) p).liquid(); }

    /** Profile suffix of [loot] chests: they do not count towards max-active and do not announce their own wake-up. */
    public static final String EXTRA = ":extra";

    /** Chests marked with [loot] signs in a saved structure; each one is a mystic chest of the marked tier (or of {@code main}). */
    public void placeExtras(Structure st, ru.mysticchest.chest.Tier main, String profile) {
        for (int i = 0; i < st.lootPoints.size(); i++) {
            ru.mysticchest.chest.Tier t = st.lootTiers.get(i).isEmpty() ? main : plugin.tiers().get(st.lootTiers.get(i));
            if (t == null) { plugin.getLogger().warning("[structure " + st.name + "] [loot] sign names an unknown tier '" + st.lootTiers.get(i) + "', the main tier is used."); t = main; }
            plugin.chests().place(st.lootPoints.get(i), t, profile + EXTRA, null, st);
        }
    }

    /** Things that are removed or built over: foliage and plants, never the ground itself. */
    private static boolean soft(String n) {
        if (n.endsWith("LEAVES") || n.endsWith("_LOG") || n.equals("LOG") || n.equals("LOG_2") || n.endsWith("_STEM") && !n.startsWith("STRIPPED")) return true;
        if (n.endsWith("_WOOD") && !n.endsWith("PLANKS")) return true;         // tree trunk blocks (OAK_WOOD), not planks
        if (n.equals("GRASS") || n.equals("SHORT_GRASS") || n.equals("TALL_GRASS") || n.equals("LONG_GRASS") || n.equals("FERN") || n.equals("LARGE_FERN")
                || n.equals("DEAD_BUSH") || n.equals("SNOW") || n.equals("VINE") || n.equals("DOUBLE_PLANT") || n.equals("SEAGRASS") || n.equals("TALL_SEAGRASS")) return true;
        return n.contains("FLOWER") || n.endsWith("_TULIP") || n.equals("POPPY") || n.equals("DANDELION") || n.equals("RED_ROSE") || n.equals("YELLOW_FLOWER")
                || n.endsWith("BUSH") && !n.startsWith("SWEET") || n.endsWith("MUSHROOM") || n.equals("SUGAR_CANE") || n.equals("REEDS");
    }

    private int groundY(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        for (int guard = 0; guard < 300 && y < w.getMaxHeight() - 2 && !w.getBlockAt(x, y, z).getType().name().endsWith("AIR"); guard++) y++;
        int min = ru.mysticchest.spawn.Locators.minHeight(w);
        while (y > min + 1 && (w.getBlockAt(x, y - 1, z).getType().name().endsWith("AIR") || soft(w.getBlockAt(x, y - 1, z).getType().name()))) y--;
        return y;
    }

    private void start(Spec spec, Blueprint bp, World w, int cx, int cz, int minX, int maxX, int minZ, int maxZ,
                       List<int[]> chunks, Callback cb) {
        Settings s = plugin.settings();
        int minH = ru.mysticchest.spawn.Locators.minHeight(w);
        // site check: slope, liquid, and nothing that looks player-made inside the volume
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        long sum = 0;
        int n = 0;
        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                int g = groundY(w, x, z);
                lo = Math.min(lo, g); hi = Math.max(hi, g);
                sum += g; n++;
                if (w.getBlockAt(x, g - 1, z).isLiquid()) { abort(w, chunks, cb, "water"); return; }
            }
        }
        if (hi - lo > s.structMaxSlope) { abort(w, chunks, cb, "too steep (" + (hi - lo) + ")"); return; }
        int baseY = (int) Math.round(sum / (double) n);
        int top = baseY + bp.height;
        if (top >= w.getMaxHeight() || baseY < minH + 2) { abort(w, chunks, cb, "height limit"); return; }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = baseY; y <= top; y++) {
                    String name = w.getBlockAt(x, y, z).getType().name();
                    for (String bad : PLAYER_MADE) if (name.contains(bad)) { abort(w, chunks, cb, "player-made block " + name); return; }
                }
            }
        }
        // operations: clear, foundation, then the structure bottom-up
        final List<int[]> ops = new ArrayList<int[]>();
        final List<Placer> placers = new ArrayList<Placer>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = baseY; y <= top; y++) {
                    if (bp.occupied.contains(Canvas.key(x - cx, y - baseY, z - cz))) continue;
                    Block b = w.getBlockAt(x, y, z);
                    String nm = b.getType().name();
                    if (nm.endsWith("AIR")) continue;
                    boolean chestCell = x - cx == bp.chestX && y - baseY == bp.chestY && z - cz == bp.chestZ;
                    if (bp.clearVolume || soft(nm) || chestCell || bp.markCells.contains(Canvas.key(x - cx, y - baseY, z - cz))) { ops.add(new int[]{x, y, z}); placers.add(null); }
                }
            }
        }
        for (java.util.Map.Entry<Long, Placer> e : bp.foundation.entrySet()) {
            int rx = Canvas.kx(e.getKey()), rz = Canvas.kz(e.getKey());
            for (int y = baseY - 1; y >= Math.max(minH + 1, baseY - 10); y--) {
                Block b = w.getBlockAt(cx + rx, y, cz + rz);
                String nm = b.getType().name();
                if (!(nm.endsWith("AIR") || b.isLiquid() || soft(nm))) break;
                ops.add(new int[]{cx + rx, y, cz + rz}); placers.add(e.getValue());
            }
        }
        int before = ops.size();
        if (s.structBlend) addSkirt(ops, placers, w, cx, cz, baseY, bp, s.structBlendWidth);
        if (s.debug) plugin.getLogger().info("[structure] baseY=" + baseY + " ground " + lo + ".." + hi + ", skirt ops=" + (ops.size() - before));
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < bp.xyz.size(); i++) order.add(i);
        final List<int[]> rel = bp.xyz;
        java.util.Collections.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                int[] pa = rel.get(a), pb = rel.get(b);
                boolean la = isLiquid(bp.placers.get(a)), lb = isLiquid(bp.placers.get(b));
                if (la != lb) return la ? 1 : -1;               // lava and water last: they only flow once everything around them stands
                if (pa[1] != pb[1]) return pa[1] < pb[1] ? -1 : 1;
                double da = Math.hypot(pa[0], pa[2]), db = Math.hypot(pb[0], pb[2]);
                return da < db ? -1 : (da > db ? 1 : 0);
            }
        });
        for (int i : order) {
            int[] p = bp.xyz.get(i);
            ops.add(new int[]{cx + p[0], baseY + p[1], cz + p[2]});
            placers.add(bp.placers.get(i));
        }
        int mainOps = ops.size();
        if (spec.debris && spec.debrisPieces > 0) addDebris(ops, placers, w, cx, cz, bp, bp.debris != null ? bp.debris : new Placer[]{spec.theme.mat(Canvas.Slot.BASE), spec.theme.mat(Canvas.Slot.ACCENT), spec.theme.mat(Canvas.Slot.TRIM), spec.theme.mat(Canvas.Slot.LIGHT)}, spec.debrisRadius, spec.debrisPieces);
        int keepFrom = s.debrisKeep && ops.size() > mainOps ? mainOps : -1;
        Location chest = new Location(w, cx + bp.chestX, baseY + bp.chestY, cz + bp.chestZ);
        Structure st = new Structure(w, minX, maxX, baseY - 10, top, minZ, maxZ, chest);
        st.chunks.addAll(chunks);
        st.name = bp.name;
        st.center = new Location(w, cx + 0.5, baseY, cz + 0.5);
        if (bp.crater != null) st.crater = new Location(w, cx + bp.crater[0] + 0.5, baseY + bp.crater[1] + 1, cz + bp.crater[2] + 0.5);
        for (int[] e : bp.extraChests) st.extraChests.add(new Location(w, cx + e[0], baseY + e[1], cz + e[2]));
        for (Template.Mark m : bp.marks) {
            Location at = new Location(w, cx + m.x, baseY + m.y, cz + m.z);
            if (m.type.equals("guard")) st.guardPoints.add(at);
            else if (m.type.equals("boss")) st.bossPoints.add(at);
            else { st.lootPoints.add(at); st.lootTiers.add(m.param); }
        }
        active.add(st);
        plugin.animator().add(new BuildJob(st, ops, placers, cb, new Location(w, cx + 0.5, baseY + 1, cz + 0.5), keepFrom));
    }

    /**
     * Terrain skirt: where the ground next to the structure lies lower than the floor, earth is filled in
     * (grass on top) so the building sits INTO the landscape, sloping down by one block per step away from it.
     */
    private void addSkirt(List<int[]> ops, List<Placer> placers, World w, int cx, int cz, int baseY, Blueprint bp, int width) {
        int minH = ru.mysticchest.spawn.Locators.minHeight(w);
        for (int x = cx + bp.minX - width - 1; x <= cx + bp.maxX + width + 1; x++) {
            for (int z = cz + bp.minZ - width - 1; z <= cz + bp.maxZ + width + 1; z++) {
                int rx = x - cx, rz = z - cz;
                int d = Math.max(Math.max(bp.minX - 1 - rx, rx - bp.maxX - 1), Math.max(bp.minZ - 1 - rz, rz - bp.maxZ - 1)) + 1;   // 1 = right next to the footprint
                if (d < 1 || d > width) continue;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                int g = groundY(w, x, z);                        // first air above the ground
                int want = baseY - d;                             // wanted height of the topmost ground block
                int top = g - 1;
                if (top >= want || top <= minH + 1) continue;
                Block surface = w.getBlockAt(x, top, z);
                if (surface.isLiquid() || surface.getType().name().endsWith("AIR")) continue;
                Placer topMat = Snap.of(surface);
                Block below = w.getBlockAt(x, top - 1, z);
                Placer fill = below.getType().name().endsWith("AIR") || below.isLiquid() ? topMat : Snap.of(below);
                for (int y = top + 1; y <= want; y++) {
                    ops.add(new int[]{x, y, z});
                    placers.add(y == want ? topMat : fill);
                }
            }
        }
    }

    /** Scatters rubble (rocks, clusters, toppled columns, broken stubs, rare arch pieces) over the surroundings. */
    private void addDebris(List<int[]> ops, List<Placer> placers, World w, int cx, int cz, Blueprint bp, Placer[] pal, int radius, int pieces) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int minR = Math.max(Math.max(Math.abs(bp.minX), Math.abs(bp.maxX)), Math.max(Math.abs(bp.minZ), Math.abs(bp.maxZ))) + 3;
        if (radius <= minR + 1) return;
        final List<int[]> newOps = new ArrayList<int[]>();
        final List<Placer> newPl = new ArrayList<Placer>();
        Set<Long> planned = new HashSet<Long>();
        int placed = 0;
        for (int attempt = 0; attempt < pieces * 4 && placed < pieces; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = minR + (radius - minR) * Math.pow(r.nextDouble(), 1.4);
            int x = cx + (int) Math.round(Math.cos(ang) * dist), z = cz + (int) Math.round(Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int g = groundY(w, x, z);
            Block under = w.getBlockAt(x, g - 1, z);
            if (under.isLiquid() || under.getType().name().endsWith("AIR") || !w.getBlockAt(x, g, z).getType().name().endsWith("AIR")) continue;
            if (nearPlayerMade(w, x, g, z)) continue;
            int roll = r.nextInt(100);
            if (roll < 38) {                                                   // a single rock
                put(newOps, newPl, planned, w, x, g, z, r.nextInt(4) == 0 ? pal[1] : pal[0]);
            } else if (roll < 63) {                                            // a small cluster, maybe two layers high
                int n = 2 + r.nextInt(3);
                for (int i = 0; i < n; i++) {
                    int dx = r.nextInt(3) - 1, dz = r.nextInt(3) - 1;
                    if (!w.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) continue;
                    int gg = groundY(w, x + dx, z + dz);
                    if (Math.abs(gg - g) > 1) continue;
                    put(newOps, newPl, planned, w, x + dx, gg, z + dz, r.nextBoolean() ? pal[0] : pal[1]);
                    if (r.nextInt(3) == 0) put(newOps, newPl, planned, w, x + dx, gg + 1, z + dz, pal[0]);
                }
            } else if (roll < 78) {                                            // a toppled column lying on the ground
                boolean alongX = r.nextBoolean();
                int len = 3 + r.nextInt(2);
                for (int i = 0; i < len; i++) {
                    int px = x + (alongX ? i : 0), pz = z + (alongX ? 0 : i);
                    if (!w.isChunkLoaded(px >> 4, pz >> 4)) break;
                    int gg = groundY(w, px, pz);
                    if (Math.abs(gg - g) > 1) break;
                    put(newOps, newPl, planned, w, px, gg, pz, i == len - 1 ? pal[2] : pal[1]);
                }
            } else if (roll < 95) {                                            // a broken stub still standing
                int h = 2 + r.nextInt(3);
                for (int i = 0; i < h; i++) put(newOps, newPl, planned, w, x, g + i, z, pal[0]);
                put(newOps, newPl, planned, w, x, g + h, z, r.nextInt(4) == 0 ? pal[3] : pal[1]);
            } else {                                                           // two stubs and a lintel: part of an arch
                int h = 3 + r.nextInt(2);
                boolean alongX = r.nextBoolean();
                int ox = alongX ? 3 : 0, oz = alongX ? 0 : 3;
                if (!w.isChunkLoaded((x + ox) >> 4, (z + oz) >> 4)) continue;
                int g2 = groundY(w, x + ox, z + oz);
                if (Math.abs(g2 - g) > 1) continue;
                for (int i = 0; i < h; i++) { put(newOps, newPl, planned, w, x, g + i, z, pal[0]); put(newOps, newPl, planned, w, x + ox, g2 + i, z + oz, pal[0]); }
                for (int t = 0; t <= 3; t++) put(newOps, newPl, planned, w, x + (alongX ? t : 0), g + h, z + (alongX ? 0 : t), t == 1 || t == 2 ? pal[1] : pal[2]);
            }
            placed++;
        }
        // the rubble appears from the structure outwards, like a shock wave
        Integer[] idx = new Integer[newOps.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        final int fx = cx, fz = cz;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                double da = Math.hypot(newOps.get(a)[0] - fx, newOps.get(a)[2] - fz), db = Math.hypot(newOps.get(b)[0] - fx, newOps.get(b)[2] - fz);
                return da < db ? -1 : (da > db ? 1 : 0);
            }
        });
        for (Integer i : idx) { ops.add(newOps.get(i)); placers.add(newPl.get(i)); }
    }

    private void put(List<int[]> ops, List<Placer> pl, Set<Long> planned, World w, int x, int y, int z, Placer m) {
        if (!w.getBlockAt(x, y, z).getType().name().endsWith("AIR")) return;
        if (!planned.add(ru.mysticchest.chest.ChestManager.key(x, y, z))) return;
        ops.add(new int[]{x, y, z});
        pl.add(m);
    }

    private boolean nearPlayerMade(World w, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!w.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) continue;
            for (int dy = -1; dy <= 2; dy++) {
                String n = w.getBlockAt(x + dx, y + dy, z + dz).getType().name();
                for (String bad : PLAYER_MADE) if (n.contains(bad)) return true;
            }
        }
        return false;
    }

    private void abort(World w, List<int[]> chunks, Callback cb, String why) {
        plugin.diag().add("structure", "site rejected at " + w.getName() + " (" + why + "), the chest is placed without a structure");
        if (plugin.settings().debug) plugin.getLogger().info("[structure] site rejected: " + why);
        for (int[] c : chunks) plugin.chests().release(w, c[0], c[1]);
        cb.done(null);
    }

    private final class BuildJob implements Animator.Animation {
        final Structure st;
        final List<int[]> ops;
        final List<Placer> mats;
        final Callback cb;
        final Location center;
        final Set<Long> seen = new HashSet<Long>();
        int idx, tick;

        final int keepFrom;

        BuildJob(Structure st, List<int[]> ops, List<Placer> mats, Callback cb, Location center, int keepFrom) {
            this.st = st; this.ops = ops; this.mats = mats; this.cb = cb; this.center = center; this.keepFrom = keepFrom;
        }

        public boolean tick() {
            int per = Math.max(1, plugin.settings().structSpeed);
            for (int i = 0; i < per && idx < ops.size(); i++, idx++) {
                int[] p = ops.get(idx);
                Block b = st.world.getBlockAt(p[0], p[1], p[2]);
                boolean keep = keepFrom >= 0 && idx >= keepFrom;      // rubble that stays after the collapse is not recorded
                if (!keep && seen.add(ru.mysticchest.chest.ChestManager.key(p[0], p[1], p[2]))) {
                    st.positions.add(p);
                    st.originals.add(Snap.of(b));
                }
                Placer m = mats.get(idx);
                if (m == null) b.setType(org.bukkit.Material.AIR, false); else m.apply(b);
            }
            if (tick++ % 3 == 0) plugin.effects().soundAt(plugin.settings().structSound, center, 0.7f, 0.7f + (float) idx / ops.size());
            if (idx < ops.size()) return true;
            finish();
            return false;
        }

        private void finish() {
            st.file = new File(dir(), UUID.randomUUID() + ".txt");
            plugin.io().request(st.file, new AsyncIO.Source() { public String content() { return encode(st); } });
            cb.done(st);
        }

        public void abort() { restoreNow(st); cb.done(null); }
    }

    // ---- persistence -------------------------------------------------------

    private static String encode(Structure st) {
        StringBuilder sb = new StringBuilder("v1\n").append(st.world.getName()).append('\n');
        for (int i = 0; i < st.positions.size(); i++) {
            int[] p = st.positions.get(i);
            sb.append(p[0]).append(';').append(p[1]).append(';').append(p[2]).append(';').append(st.originals.get(i).encode()).append('\n');
        }
        return sb.toString();
    }

    /** Startup: undo structures that a crash left behind. */
    public void cleanupLeftovers() {
        File[] files = dir().listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().endsWith(".txt")) continue;
            try {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                World w = lines.size() > 2 ? Bukkit.getWorld(lines.get(1)) : null;
                if (w == null) continue;
                for (int i = lines.size() - 1; i >= 2; i--) {
                    String[] p = lines.get(i).split(";", 4);
                    if (p.length < 4) continue;
                    int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) w.getChunkAt(x >> 4, z >> 4);
                    Snap.decode(p[3]).apply(w.getBlockAt(x, y, z));
                }
                f.delete();
                plugin.getLogger().info("Restored terrain of a structure left by a crash (" + (lines.size() - 2) + " blocks).");
            } catch (Exception e) {
                plugin.getLogger().warning("Cannot restore " + f.getName() + ": " + e);
            }
        }
    }

    // ---- removal ---------------------------------------------------------

    /** The chest is gone: after a delay the structure collapses (top-down) and the terrain returns. */
    public void scheduleRestore(final Structure st) { scheduleRestore(st, plugin.settings().structCollapseDelay); }

    public void scheduleRestore(final Structure st, int delaySeconds) {
        if (st == null || st.restoring) return;
        st.restoring = true;
        if (plugin.settings().debug) plugin.getLogger().info("[structure] restore scheduled in " + delaySeconds + "s (" + st.blocks() + " blocks)");
        long delay = delaySeconds * 1000L;
        plugin.scheduler().later(delay, new Runnable() {
            public void run() {
                Settings s = plugin.settings();
                if (s.structCollapse && plugin.animator().size() < s.maxAnimations) plugin.animator().add(new CollapseJob(st));
                else restoreNow(st);
            }
        });
    }

    private final class CollapseJob implements Animator.Animation {
        final Structure st;
        int idx;
        int tick;

        CollapseJob(Structure st) { this.st = st; this.idx = st.positions.size() - 1; }

        public boolean tick() {
            int per = Math.max(2, plugin.settings().structSpeed * 2);
            for (int i = 0; i < per && idx >= 0; i++, idx--) {
                int[] p = st.positions.get(idx);
                st.originals.get(idx).apply(st.world.getBlockAt(p[0], p[1], p[2]));
            }
            if (tick++ % 4 == 0) plugin.effects().soundAt(plugin.settings().structSound, st.chest, 0.6f, 0.5f);
            if (idx >= 0) return true;
            close(st);
            return false;
        }

        public void abort() { restoreNow(st); }
    }

    public void restoreNow(Structure st) {
        if (st.restored) return;
        for (int i = st.positions.size() - 1; i >= 0; i--) {
            int[] p = st.positions.get(i);
            st.originals.get(i).apply(st.world.getBlockAt(p[0], p[1], p[2]));
        }
        close(st);
    }

    private void close(Structure st) {
        if (st.restored) return;
        if (plugin.settings().debug) plugin.getLogger().info("[structure] terrain restored");
        st.restored = true;
        active.remove(st);
        for (int[] c : st.chunks) plugin.chests().release(st.world, c[0], c[1]);
        if (st.file != null) st.file.delete();
    }

    public boolean protects(Block b) {
        if (active.isEmpty() || !plugin.settings().structProtect) return false;
        for (Structure st : active) if (!st.restored && st.contains(b)) return true;
        return false;
    }

    public int count() { return active.size(); }

    /** Plugin shutdown: put every piece of terrain back right now. */
    public void shutdown() {
        for (Structure st : new ArrayList<Structure>(active)) restoreNow(st);
    }
}
