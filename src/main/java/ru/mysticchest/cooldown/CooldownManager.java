package ru.mysticchest.cooldown;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.core.AsyncIO;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldowns are plain "expires at" timestamps (no per-tick counters, no tasks). Daily counters and
 * pity counters live next to them. Expired data is dropped whenever the file is written.
 */
public final class CooldownManager {
    public static final UUID GLOBAL = new UUID(0L, 0L);

    private static final class PD {
        final Map<String, Long> cd = new HashMap<String, Long>();
        final Map<String, Integer> pity = new HashMap<String, Integer>();
        final Map<String, Integer> wins = new HashMap<String, Integer>();
        long day;
        int opens, buys;
    }

    private final MysticChestPlugin plugin;
    private final Map<UUID, PD> players = new HashMap<UUID, PD>();

    public CooldownManager(MysticChestPlugin plugin) { this.plugin = plugin; }

    private File file() { return new File(plugin.getDataFolder(), "data/players.yml"); }

    private static long today() { return java.time.LocalDate.now().toEpochDay(); }

    public void load() {
        players.clear();
        if (!file().exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection root = y.getConfigurationSection("players");
        if (root == null) return;
        long now = System.currentTimeMillis();
        for (String k : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                ConfigurationSection s = root.getConfigurationSection(k);
                PD d = new PD();
                ConfigurationSection cd = s.getConfigurationSection("cd");
                if (cd != null) {
                    for (String ck : cd.getKeys(false)) {
                        long at = cd.getLong(ck);
                        if (at > now) d.cd.put(ck, at);
                    }
                }
                ConfigurationSection pt = s.getConfigurationSection("pity");
                if (pt != null) for (String tk : pt.getKeys(false)) d.pity.put(tk, pt.getInt(tk));
                ConfigurationSection wn = s.getConfigurationSection("wins");
                if (wn != null) for (String wk : wn.getKeys(false)) d.wins.put(wk, wn.getInt(wk));
                d.day = s.getLong("day");
                d.opens = s.getInt("opens");
                d.buys = s.getInt("buys");
                players.put(id, d);
            } catch (IllegalArgumentException ignored) {
                // corrupt key, skip
            }
        }
    }

    private PD data(UUID id) {
        PD d = players.get(id);
        if (d == null) { d = new PD(); players.put(id, d); }
        long t = today();
        if (d.day != t) { d.day = t; d.opens = 0; d.buys = 0; }
        return d;
    }

    /** Seconds left (rounded up), 0 when free. */
    public long remaining(UUID id, String key) {
        PD d = players.get(id);
        if (d == null) return 0;
        Long at = d.cd.get(key);
        if (at == null) return 0;
        long left = at - System.currentTimeMillis();
        if (left <= 0) { d.cd.remove(key); return 0; }
        return (left + 999) / 1000;
    }

    public void start(UUID id, String key, long seconds) {
        if (seconds <= 0) return;
        data(id).cd.put(key, System.currentTimeMillis() + seconds * 1000L);
        dirty();
    }

    public int opens(UUID id) { return data(id).opens; }
    public int buys(UUID id) { return data(id).buys; }
    public void addOpen(UUID id) { data(id).opens++; dirty(); }
    public void addBuy(UUID id) { data(id).buys++; dirty(); }

    public int pity(UUID id, String tier) {
        PD d = players.get(id);
        Integer v = d == null ? null : d.pity.get(tier);
        return v == null ? 0 : v;
    }

    public void setPity(UUID id, String tier, int v) {
        PD d = data(id);
        if (v <= 0) d.pity.remove(tier); else d.pity.put(tier, v);
        dirty();
    }

    public int wins(UUID id, String entry) {
        PD d = players.get(id);
        Integer v = d == null ? null : d.wins.get(entry);
        return v == null ? 0 : v;
    }

    public void addWin(UUID id, String entry) {
        PD d = data(id);
        Integer v = d.wins.get(entry);
        d.wins.put(entry, v == null ? 1 : v + 1);
        dirty();
    }

    public int opensToday(UUID id) { return data(id).opens; }

    public long cooldownLeft(UUID id, String key) { return remaining(id, key); }

    public int size() { return players.size(); }

    private void dirty() {
        plugin.io().request(file(), new AsyncIO.Source() {
            public String content() { return serialize(); }
        });
    }

    private String serialize() {
        long now = System.currentTimeMillis(), today = today();
        YamlConfiguration y = new YamlConfiguration();
        for (Iterator<Map.Entry<UUID, PD>> it = players.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, PD> e = it.next();
            PD d = e.getValue();
            for (Iterator<Map.Entry<String, Long>> c = d.cd.entrySet().iterator(); c.hasNext(); ) {
                if (c.next().getValue() <= now) c.remove();
            }
            boolean todayData = d.day == today && (d.opens > 0 || d.buys > 0);
            if (d.cd.isEmpty() && d.pity.isEmpty() && d.wins.isEmpty() && !todayData) { it.remove(); continue; }
            String p = "players." + e.getKey();
            for (Map.Entry<String, Long> c : d.cd.entrySet()) y.set(p + ".cd." + c.getKey(), c.getValue());
            for (Map.Entry<String, Integer> c : d.pity.entrySet()) y.set(p + ".pity." + c.getKey(), c.getValue());
            for (Map.Entry<String, Integer> c : d.wins.entrySet()) y.set(p + ".wins." + c.getKey(), c.getValue());
            if (todayData) { y.set(p + ".day", d.day); y.set(p + ".opens", d.opens); y.set(p + ".buys", d.buys); }
        }
        return "## Player cooldowns, daily counters and pity counters. Managed by the plugin.\n" + y.saveToString();
    }
}
