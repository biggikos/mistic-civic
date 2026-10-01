package ru.mysticchest.stats;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.core.AsyncIO;
import ru.mysticchest.core.Scheduler;
import ru.mysticchest.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leaderboard holograms (armor stands) in the world, refreshed every 30 seconds. */
public final class BoardService {
    private static final String TAG = "mc-board";

    private static final class Board {
        final String name, stat;
        final Location loc;
        final boolean all;
        final List<ArmorStand> stands = new ArrayList<ArmorStand>();
        Board(String n, Location l, String s, boolean a) { name = n; loc = l; stat = s; all = a; }
    }

    private final MysticChestPlugin plugin;
    private final Map<String, Board> boards = new LinkedHashMap<String, Board>();
    private Scheduler.Handle timer;

    public BoardService(MysticChestPlugin plugin) { this.plugin = plugin; }

    private File file() { return new File(plugin.getDataFolder(), "data/boards.yml"); }

    public void load() {
        shutdown();
        boards.clear();
        if (file().exists()) {
            for (String s : YamlConfiguration.loadConfiguration(file()).getStringList("boards")) {
                String[] p = s.split(";");
                World w = p.length >= 7 ? Bukkit.getWorld(p[1]) : null;
                if (w == null) continue;
                Location l = new Location(w, Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]));
                boards.put(p[0], new Board(p[0], l, p[5], Boolean.parseBoolean(p[6])));
            }
        }
        for (Board b : boards.values()) purge(b);
        schedule();
    }

    private void purge(Board b) {
        if (!b.loc.getWorld().isChunkLoaded(b.loc.getBlockX() >> 4, b.loc.getBlockZ() >> 4)) return;
        for (Entity e : b.loc.getWorld().getNearbyEntities(b.loc, 2, 5, 2)) {
            if (e instanceof ArmorStand && e.getScoreboardTags().contains(TAG)) e.remove();
        }
    }

    public boolean create(String name, Location l, String stat, boolean all) {
        if (boards.containsKey(name)) return false;
        boards.put(name, new Board(name, l.clone(), stat, all));
        save();
        refresh();
        schedule();
        return true;
    }

    public boolean remove(String name) {
        Board b = boards.remove(name);
        if (b == null) return false;
        clear(b);
        save();
        return true;
    }

    public java.util.Set<String> names() { return boards.keySet(); }

    private void save() {
        plugin.io().request(file(), new AsyncIO.Source() {
            public String content() {
                List<String> out = new ArrayList<String>();
                for (Board b : boards.values()) {
                    out.add(b.name + ";" + b.loc.getWorld().getName() + ";" + b.loc.getX() + ";" + b.loc.getY() + ";" + b.loc.getZ() + ";" + b.stat + ";" + b.all);
                }
                YamlConfiguration y = new YamlConfiguration();
                y.set("boards", out);
                return "## Leaderboard holograms (/mystic board). Managed by the plugin.\n" + y.saveToString();
            }
        });
    }

    private void schedule() {
        if (timer != null) timer.cancel();
        timer = null;
        if (boards.isEmpty()) return;
        timer = plugin.scheduler().later(30000L, new Runnable() {
            public void run() { refresh(); schedule(); }
        });
    }

    private void clear(Board b) {
        for (ArmorStand s : b.stands) if (s.isValid()) s.remove();
        b.stands.clear();
    }

    public void refresh() {
        int size = plugin.settings().root.sub("leaderboard").integer("board-lines", 5, 1, 15);
        for (Board b : boards.values()) {
            World w = b.loc.getWorld();
            if (!w.isChunkLoaded(b.loc.getBlockX() >> 4, b.loc.getBlockZ() >> 4)) continue;
            List<String> lines = new ArrayList<String>();
            String console = plugin.lang().code(Bukkit.getConsoleSender());
            lines.add(plugin.lang().get("top.board-title", "stat", plugin.lang().get("stat." + b.stat),
                    "period", b.all ? plugin.lang().get("top.all-time") : plugin.stats().periodKey()));
            List<StatsService.Row> top = plugin.stats().top(b.stat, b.all, size);
            for (int i = 0; i < size; i++) {
                if (i < top.size()) lines.add(plugin.lang().get("top.board-line", "rank", String.valueOf(i + 1), "name", top.get(i).name, "value", String.valueOf(top.get(i).value)));
                else lines.add(plugin.lang().get("top.board-empty", "rank", String.valueOf(i + 1)));
            }
            boolean rebuild = b.stands.size() != lines.size();
            for (ArmorStand s : b.stands) if (!s.isValid()) rebuild = true;
            if (rebuild) {
                clear(b);
                for (int i = 0; i < lines.size(); i++) {
                    ArmorStand s = w.spawn(b.loc.clone().add(0, -i * 0.27, 0), ArmorStand.class);
                    s.setVisible(false); s.setGravity(false); s.setMarker(true);
                    s.addScoreboardTag(TAG);
                    b.stands.add(s);
                }
            }
            for (int i = 0; i < lines.size(); i++) {
                b.stands.get(i).setCustomName(lines.get(i));
                b.stands.get(i).setCustomNameVisible(true);
            }
        }
    }

    public void shutdown() {
        if (timer != null) timer.cancel();
        timer = null;
        for (Board b : boards.values()) clear(b);
    }
}
