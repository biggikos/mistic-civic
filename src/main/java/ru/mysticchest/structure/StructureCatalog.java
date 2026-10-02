package ru.mysticchest.structure;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.core.AsyncIO;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Everything that can be built: the five built-in shapes plus every file in structures/. Each entry has an
 * enabled flag, a weight and an optional fixed theme; they are edited in game (/mystic structure edit) and
 * saved in structures.yml.
 */
public final class StructureCatalog {
    public static final class Entry {
        public final String id;
        public final Shape shape;       // built-in, or null
        public Template template;       // custom, or null
        public boolean enabled = true;
        public int weight = 10;
        public String theme;            // null = decided by config / biome
        /** Filters and tuning (structures.yml, /mystic structure set): empty list = no restriction. */
        public final java.util.List<String> tags = new ArrayList<String>();
        public final java.util.List<String> tiers = new ArrayList<String>();
        public final java.util.List<String> biomes = new ArrayList<String>();
        public final java.util.List<String> worlds = new ArrayList<String>();
        public Boolean rotate;          // null = the global setting
        public double debrisScale = 1.0;

        Entry(String id, Shape shape, Template template) { this.id = id; this.shape = shape; this.template = template; }

        public boolean custom() { return shape == null; }
    }

    private static final Pattern NAME = Pattern.compile("[a-z0-9_\\-]{1,32}");
    private final MysticChestPlugin plugin;
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

    public StructureCatalog(MysticChestPlugin plugin) { this.plugin = plugin; }

    public File folder() { return new File(plugin.getDataFolder(), "structures"); }
    private File file() { return new File(plugin.getDataFolder(), "structures.yml"); }

    public static boolean validName(String n) { return n != null && NAME.matcher(n).matches(); }

    public Collection<Entry> all() { return entries.values(); }
    public Entry get(String id) { return id == null ? null : entries.get(id.toLowerCase(Locale.ROOT)); }

    public void load() {
        entries.clear();
        for (Shape s : Shape.values()) {
            Entry e = new Entry(s.name().toLowerCase(Locale.ROOT), s, null);
            e.tags.addAll(Arrays.asList(s.tags()));
            if (s.special()) e.enabled = false;        // event-only shapes stay out of the random rotation
            entries.put(e.id, e);
        }
        File[] files = folder().listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName();
                if (!n.endsWith(".yml")) continue;
                String id = n.substring(0, n.length() - 4).toLowerCase(Locale.ROOT);
                if (!validName(id) || entries.containsKey(id)) continue;
                Template t = Template.load(id, f, plugin.getLogger());
                if (t == null) continue;
                Entry e = new Entry(id, null, t);
                entries.put(id, e);
            }
        }
        if (file().exists()) {
            ConfigurationSection root = YamlConfiguration.loadConfiguration(file()).getConfigurationSection("structures");
            if (root != null) {
                for (String k : root.getKeys(false)) {
                    Entry e = entries.get(k.toLowerCase(Locale.ROOT));
                    ConfigurationSection s = root.getConfigurationSection(k);
                    if (e == null || s == null) continue;
                    e.enabled = s.getBoolean("enabled", true);
                    e.weight = Math.max(0, s.getInt("weight", 10));
                    String th = s.getString("theme");
                    e.theme = th == null || th.equalsIgnoreCase("AUTO") ? null : th.toUpperCase(Locale.ROOT);
                    if (s.isSet("tags")) { e.tags.clear(); e.tags.addAll(lower(s.getStringList("tags"))); }
                    e.tiers.addAll(lower(s.getStringList("tiers")));
                    e.biomes.addAll(lower(s.getStringList("biomes")));
                    e.worlds.addAll(s.getStringList("worlds"));
                    if (s.isSet("rotate")) e.rotate = s.getBoolean("rotate");
                    e.debrisScale = Math.max(0, Math.min(5, s.getDouble("debris", 1.0)));
                }
            }
        } else {
            save();
        }
    }

    public void save() {
        plugin.io().request(file(), new AsyncIO.Source() {
            public String content() {
                YamlConfiguration y = new YamlConfiguration();
                for (Entry e : entries.values()) {
                    String p = "structures." + e.id;
                    y.set(p + ".enabled", e.enabled);
                    y.set(p + ".weight", e.weight);
                    y.set(p + ".theme", e.theme == null ? "AUTO" : e.theme);
                    y.set(p + ".tags", e.tags);
                    if (!e.tiers.isEmpty()) y.set(p + ".tiers", e.tiers);
                    if (!e.biomes.isEmpty()) y.set(p + ".biomes", e.biomes);
                    if (!e.worlds.isEmpty()) y.set(p + ".worlds", e.worlds);
                    if (e.rotate != null) y.set(p + ".rotate", e.rotate);
                    if (e.debrisScale != 1.0) y.set(p + ".debris", e.debrisScale);
                }
                return "## Which structures mystic chests can appear in. Edit in game: /mystic structure edit\n"
                        + "## enabled: in the rotation or not    weight: bigger = picked more often\n"
                        + "## theme: AUTO (biome / config) or DESERT, STONE, NETHER, END, FROST, OCEAN (built-in shapes only)\n"
                        + "## tags: for shapes: [tag:nether] filters   tiers / biomes / worlds: where it may appear (empty = anywhere)\n"
                        + "## rotate: false = never turn it (custom ones)   debris: multiplier of the rubble around it\n"
                        + "## Easiest: /mystic structure set <name> <tags|tiers|biomes|worlds|rotate|debris|weight|theme> <value>\n" + y.saveToString();
            }
        });
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<String>();
        for (String s : in) out.add(s.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    /** What a structure is picked for: used to honour its tiers / biomes / worlds lists. */
    public static final class Context {
        public final String tier, biome, world;
        public Context(String tier, String biome, String world) { this.tier = tier == null ? null : tier.toLowerCase(Locale.ROOT); this.biome = biome == null ? "" : biome.toLowerCase(Locale.ROOT); this.world = world; }
    }

    private static boolean allows(Entry e, Context c) {
        if (c == null) return true;
        if (!e.tiers.isEmpty() && (c.tier == null || !e.tiers.contains(c.tier))) return false;
        if (!e.worlds.isEmpty()) { boolean in = false; for (String w : e.worlds) if (w.equalsIgnoreCase(c.world)) in = true; if (!in) return false; }
        if (!e.biomes.isEmpty()) { boolean in = false; for (String b : e.biomes) if (c.biome.contains(b)) in = true; if (!in) return false; }
        return true;
    }

    /** Weighted pick among enabled entries, optionally restricted to the given names ("tag:xyz" selects by tag). */
    public Entry pick(List<String> filter, Random r, Context ctx) {
        List<Entry> ok = new ArrayList<Entry>();
        long total = 0;
        for (Entry e : entries.values()) {
            if (!e.enabled || e.weight <= 0 || !allows(e, ctx)) continue;
            if (filter != null && !filter.isEmpty()) {
                boolean in = false;
                for (String f : filter) {
                    String q = f.trim();
                    if (q.regionMatches(true, 0, "tag:", 0, 4)) { if (e.tags.contains(q.substring(4).trim().toLowerCase(Locale.ROOT))) in = true; }
                    else if (q.equalsIgnoreCase(e.id)) in = true;
                }
                if (!in) continue;
            }
            ok.add(e);
            total += e.weight;
        }
        if (ok.isEmpty()) return null;
        long x = (long) (r.nextDouble() * total);
        for (Entry e : ok) { x -= e.weight; if (x < 0) return e; }
        return ok.get(ok.size() - 1);
    }

    public double chance(Entry e) {
        if (!e.enabled) return 0;
        long total = 0;
        for (Entry o : entries.values()) if (o.enabled) total += o.weight;
        return total == 0 ? 0 : e.weight * 100.0 / total;
    }

    public Entry add(Template t) {
        Entry e = new Entry(t.name, null, t);
        Entry old = entries.get(t.name);
        if (old != null) { e.enabled = old.enabled; e.weight = old.weight; e.theme = old.theme; }
        entries.put(t.name, e);
        save();
        return e;
    }

    public boolean remove(String id) {
        Entry e = entries.get(id.toLowerCase(Locale.ROOT));
        if (e == null || !e.custom()) return false;
        entries.remove(e.id);
        new File(folder(), e.id + ".yml").delete();
        save();
        return true;
    }
}
