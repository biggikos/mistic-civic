package ru.mysticchest.stats;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.config.Cfg;
import ru.mysticchest.core.AsyncIO;

import java.io.File;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player counters (opens, rares, hunts, guards, bosses, captures) for the current period and for all time,
 * a leaderboard, period-end rewards and achievements. Everything is in memory and saved debounced.
 */
public final class StatsService {
    public static final String[] STATS = {"opens", "rares", "hunts", "guards", "bosses", "captures"};

    public static final class Row {
        public final UUID id; public final String name; public final int value;
        Row(UUID id, String name, int value) { this.id = id; this.name = name; this.value = value; }
    }

    private final MysticChestPlugin plugin;
    private final Map<UUID, Map<String, Integer>> period = new HashMap<UUID, Map<String, Integer>>();
    private final Map<UUID, Map<String, Integer>> total = new HashMap<UUID, Map<String, Integer>>();
    private final Map<UUID, String> names = new HashMap<UUID, String>();
    private final Map<UUID, java.util.Set<String>> done = new HashMap<UUID, java.util.Set<String>>();
    private String periodKey = "";

    public StatsService(MysticChestPlugin plugin) { this.plugin = plugin; }

    private File file() { return new File(plugin.getDataFolder(), "data/stats.yml"); }
    private Cfg cfg() { return plugin.settings().root.sub("leaderboard"); }

    // ---- period handling ---------------------------------------------------

    private String currentKey() {
        String p = cfg().str("period", "WEEKLY").toUpperCase(Locale.ROOT);
        LocalDate d = LocalDate.now();
        if (p.equals("MONTHLY")) return d.getYear() + "-" + String.format("%02d", d.getMonthValue());
        if (p.equals("DAILY")) return d.toString();
        if (p.equals("ALL")) return "all";
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public void load() {
        period.clear(); total.clear(); names.clear(); done.clear();
        File f = file();
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            periodKey = y.getString("period-key", "");
            read(y.getConfigurationSection("names"), null);
            readCounters(y.getConfigurationSection("period"), period);
            readCounters(y.getConfigurationSection("total"), total);
            ConfigurationSection dn = y.getConfigurationSection("achievements");
            if (dn != null) for (String k : dn.getKeys(false)) {
                try { done.put(UUID.fromString(k), new java.util.HashSet<String>(dn.getStringList(k))); } catch (IllegalArgumentException ignored) {}
            }
        }
        checkPeriod();
    }

    private void read(ConfigurationSection s, Object unused) {
        if (s == null) return;
        for (String k : s.getKeys(false)) { try { names.put(UUID.fromString(k), s.getString(k)); } catch (IllegalArgumentException ignored) {} }
    }

    private void readCounters(ConfigurationSection s, Map<UUID, Map<String, Integer>> into) {
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                ConfigurationSection c = s.getConfigurationSection(k);
                Map<String, Integer> m = new HashMap<String, Integer>();
                for (String st : c.getKeys(false)) m.put(st, c.getInt(st));
                into.put(UUID.fromString(k), m);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /** Called at load and by a timer: pays out and resets when a new period has begun. */
    public void checkPeriod() {
        if (!cfg().bool("enabled", true)) return;
        String now = currentKey();
        if (periodKey.isEmpty()) { periodKey = now; dirty(); return; }
        if (now.equals(periodKey)) return;
        payout(periodKey);
        period.clear();
        periodKey = now;
        dirty();
    }

    private void payout(String finished) {
        String stat = cfg().str("reward-stat", "opens");
        List<Row> top = top(stat, false, 10);
        Cfg rewards = cfg().sub("rewards");
        StringBuilder winners = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            Row r = top.get(i);
            List<String> cmds = rewards.strings(String.valueOf(i + 1));
            if (cmds.isEmpty() && !rewards.has(String.valueOf(i + 1))) continue;
            for (String c : cmds) {
                String cmd = c.replace("{player}", r.name).replace("{rank}", String.valueOf(i + 1)).replace("{value}", String.valueOf(r.value)).replace("{stat}", stat);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
            if (winners.length() > 0) winners.append("&7, ");
            winners.append("&e#").append(i + 1).append(" &f").append(r.name).append(" &7(").append(r.value).append(")");
        }
        if (winners.length() > 0 && cfg().bool("announce-winners", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.lang().send(p, "top.winners", "period", finished, "stat", plugin.lang().get(p, "stat." + stat),
                        "winners", ru.mysticchest.util.Text.color(winners.toString()));
            }
        }
        plugin.getLogger().info("Leaderboard period " + finished + " ended; rewards paid for '" + stat + "'.");
    }

    // ---- counting ---------------------------------------------------------

    public void add(Player p, String stat) { add(p, stat, 1); }

    public void add(Player p, String stat, int n) {
        if (!cfg().bool("enabled", true)) return;
        UUID id = p.getUniqueId();
        names.put(id, p.getName());
        bump(period, id, stat, n);
        int t = bump(total, id, stat, n);
        dirty();
        achievements(p, stat, t);
    }

    private static int bump(Map<UUID, Map<String, Integer>> m, UUID id, String stat, int n) {
        Map<String, Integer> c = m.get(id);
        if (c == null) { c = new HashMap<String, Integer>(); m.put(id, c); }
        Integer v = c.get(stat);
        int nv = (v == null ? 0 : v) + n;
        c.put(stat, nv);
        return nv;
    }

    public int get(UUID id, String stat, boolean allTime) {
        Map<String, Integer> c = (allTime ? total : period).get(id);
        Integer v = c == null ? null : c.get(stat);
        return v == null ? 0 : v;
    }

    public List<Row> top(String stat, boolean allTime, int n) {
        List<Row> rows = new ArrayList<Row>();
        for (Map.Entry<UUID, Map<String, Integer>> e : (allTime ? total : period).entrySet()) {
            Integer v = e.getValue().get(stat);
            if (v != null && v > 0) rows.add(new Row(e.getKey(), names.containsKey(e.getKey()) ? names.get(e.getKey()) : "?", v));
        }
        Collections.sort(rows, new Comparator<Row>() { public int compare(Row a, Row b) { return b.value - a.value; } });
        return rows.size() > n ? new ArrayList<Row>(rows.subList(0, n)) : rows;
    }

    public String periodKey() { return periodKey; }

    // ---- achievements -------------------------------------------------------

    private String loc(ConfigurationSection s, String key, String code) {
        if (s.isConfigurationSection(key)) {
            ConfigurationSection m = s.getConfigurationSection(key);
            String v = m.getString(code);
            return v != null ? v : m.getString("en", "");
        }
        return s.getString(key, "");
    }

    private void achievements(Player p, String stat, int value) {
        ConfigurationSection root = plugin.settings().root.sub("achievements").raw();
        if (root == null) return;
        java.util.Set<String> got = done.get(p.getUniqueId());
        for (String id : root.getKeys(false)) {
            ConfigurationSection a = root.getConfigurationSection(id);
            if (a == null || !stat.equals(a.getString("stat")) || value < a.getInt("at", 1)) continue;
            if (got != null && got.contains(id)) continue;
            if (got == null) { got = new java.util.HashSet<String>(); done.put(p.getUniqueId(), got); }
            got.add(id);
            String code = plugin.lang().code(p);
            String title = loc(a, "title", code), sub = loc(a, "subtitle", code);
            if (!title.isEmpty() || !sub.isEmpty()) {
                try { p.sendTitle(ru.mysticchest.util.Text.color(title), ru.mysticchest.util.Text.color(sub), 10, 70, 20); } catch (Throwable ignored) {}
            }
            plugin.effects().soundTo(p, a.getString("sound", "ENTITY_PLAYER_LEVELUP"), 1f, 1f);
            if (a.getBoolean("announce", false)) {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    o.sendMessage(plugin.lang().get(o, "prefix") + plugin.lang().get(o, "achievement.announce", "player", p.getName(),
                            "title", ru.mysticchest.util.Text.color(title.isEmpty() ? id : title)));
                }
            }
            for (String c : a.getStringList("commands")) {
                String cmd = c.replace("{player}", p.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
            dirty();
        }
    }

    // ---- persistence --------------------------------------------------------

    private void dirty() {
        plugin.io().request(file(), new AsyncIO.Source() { public String content() { return serialize(); } });
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("period-key", periodKey);
        for (Map.Entry<UUID, String> e : names.entrySet()) y.set("names." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Map<String, Integer>> e : period.entrySet()) for (Map.Entry<String, Integer> c : e.getValue().entrySet()) y.set("period." + e.getKey() + "." + c.getKey(), c.getValue());
        for (Map.Entry<UUID, Map<String, Integer>> e : total.entrySet()) for (Map.Entry<String, Integer> c : e.getValue().entrySet()) y.set("total." + e.getKey() + "." + c.getKey(), c.getValue());
        for (Map.Entry<UUID, java.util.Set<String>> e : done.entrySet()) y.set("achievements." + e.getKey(), new ArrayList<String>(e.getValue()));
        return "## Player statistics, leaderboard period and unlocked achievements. Managed by the plugin.\n" + y.saveToString();
    }
}
