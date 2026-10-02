package ru.mysticchest.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.LootEntry;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.core.Metrics;
import ru.mysticchest.gui.LootEditorGui;
import ru.mysticchest.gui.PreviewGui;
import ru.mysticchest.gui.ShopGui;
import ru.mysticchest.loot.LootTools;
import ru.mysticchest.spawn.SpawnProfile;
import ru.mysticchest.util.ItemCodec;
import ru.mysticchest.util.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MysticCommand implements TabExecutor {
    private static final List<String> ROOT = Arrays.asList("shop", "preview", "list", "give", "spawn", "reload",
            "perf", "loot", "point", "economy", "compass", "structure", "chests", "top", "stats", "board", "track", "mute", "event", "debug", "help");
    private static final List<String> LOOT = Arrays.asList("add", "addcmd", "cmd", "weight", "remove", "list", "edit", "clear", "fill", "preset", "copy", "check", "export", "import");

    private final MysticChestPlugin plugin;

    public MysticCommand(MysticChestPlugin plugin) { this.plugin = plugin; }

    private boolean need(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        plugin.lang().send(s, "deny.no-permission");
        return false;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player) return (Player) s;
        plugin.lang().send(s, "only-players");
        return null;
    }

    private Tier tier(CommandSender s, String id) {
        Tier t = plugin.tiers().get(id);
        if (t == null) plugin.lang().send(s, "unknown-tier", "tier", id);
        return t;
    }

    private String tn(CommandSender s, Tier t) { return t.name(plugin.lang().code(s)); }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            for (String l : plugin.lang().list(s, s.hasPermission("mysticchest.admin") ? "help.admin" : "help.player")) send(s, l);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "shop": {
                if (!need(s, "mysticchest.shop")) return true;
                Player p = player(s);
                if (p != null) ShopGui.open(plugin, p);
                return true;
            }
            case "preview": {
                if (!need(s, "mysticchest.preview")) return true;
                Player p = player(s);
                if (p == null) return true;
                Tier t = a.length > 1 ? tier(s, a[1]) : null;
                if (t != null) PreviewGui.open(plugin, p, t, 0, false);
                else if (a.length < 2) plugin.lang().send(s, "usage.preview");
                return true;
            }
            case "list":
                for (Tier t : plugin.tiers().all()) send(s, " [[&8-|run:/mystic preview " + t.id + "|&7Preview rewards]] [[&f" + t.id + "|run:/mystic preview " + t.id + "|&7Click: rewards and chances]] " + tn(s, t));
                return true;
            case "reload":
                if (!need(s, "mysticchest.admin")) return true;
                plugin.reloadAll();
                plugin.lang().send(s, "reloaded");
                return true;
            case "give": return give(s, a);
            case "spawn": return spawn(s, a);
            case "perf": return perf(s);
            case "economy": return economy(s, a);
            case "structure": return structure(s, a);
            case "chests": return chests(s);
            case "track": return track(s, a);
            case "mute": return mute(s);
            case "event": return event(s, a);
            case "debug": return debug(s, a);
            case "top": return top(s, a);
            case "stats": return stats(s, a);
            case "board": return board(s, a);
            case "compass": {
                if (!need(s, "mysticchest.compass")) return true;
                Player p = player(s);
                if (p == null) return true;
                plugin.rewards().give(p, plugin.compass().item(p));
                plugin.lang().send(p, "compass.given");
                return true;
            }
            case "loot": return loot(s, a);
            case "point": return point(s, a);
            default:
                plugin.lang().send(s, "usage.root");
                return true;
        }
    }

    // ---- give / spawn --------------------------------------------------

    private boolean give(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin")) return true;
        if (a.length < 3) { plugin.lang().send(s, "usage.give"); return true; }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) { plugin.lang().send(s, "unknown-player", "player", a[1]); return true; }
        Tier t = tier(s, a[2]);
        if (t == null) return true;
        int n = 1;
        if (a.length > 3) {
            try { n = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException e) { n = 1; }
        }
        plugin.rewards().give(target, plugin.chests().chestItem(t, target, n));
        plugin.lang().send(s, "given", "tier", tn(s, t), "player", target.getName(), "count", String.valueOf(n));
        return true;
    }

    private boolean spawn(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin")) return true;
        if (a.length < 2) { plugin.lang().send(s, "usage.spawn"); return true; }
        Tier t = tier(s, a[1]);
        if (t == null) return true;
        String where = a.length > 2 ? a[2] : "here";
        if (where.equalsIgnoreCase("here")) {
            Player p = player(s);
            if (p == null) return true;
            if (!plugin.spawner().spawnHere(t, p.getLocation(), a.length > 3 ? a[3] : null, a.length > 4 ? a[4] : null)) plugin.lang().send(s, "spawn.failed");
            return true;
        }
        SpawnProfile prof = plugin.spawner().profiles().get(where);
        if (prof == null) { plugin.lang().send(s, "unknown-profile", "profile", where); return true; }
        plugin.spawner().run(prof, t, s, true);
        return true;
    }

    private boolean point(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin")) return true;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        if (sub.equals("list")) {
            if (plugin.locators().points().isEmpty()) plugin.lang().send(s, "point.none");
            for (Map.Entry<String, Location> e : plugin.locators().points().entrySet()) {
                Location l = e.getValue();
                s.sendMessage(" - " + e.getKey() + "  " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
            }
        } else if (sub.equals("add") && a.length > 2) {
            Player p = player(s);
            if (p == null) return true;
            plugin.locators().addPoint(a[2], p.getLocation());
            plugin.lang().send(s, "point.added", "name", a[2]);
        } else if (sub.equals("remove") && a.length > 2) {
            plugin.lang().send(s, plugin.locators().removePoint(a[2]) ? "point.removed" : "point.unknown", "name", a[2]);
        } else {
            plugin.lang().send(s, "usage.point");
        }
        return true;
    }

    private boolean perf(CommandSender s) {
        if (!need(s, "mysticchest.admin")) return true;
        Runtime rt = Runtime.getRuntime();
        long last = Metrics.lastSaveAt;
        for (String l : plugin.lang().list(s, "perf",
                "chests", String.valueOf(plugin.chests().count()),
                "anims", String.valueOf(plugin.animator().size()),
                "timers", String.valueOf(plugin.scheduler().pending()),
                "tasks", String.valueOf(pluginTasks()),
                "awake", String.valueOf(plugin.scheduler().awake()),
                "gui", String.valueOf(plugin.guiCache().size()),
                "players", String.valueOf(plugin.cooldowns().size()),
                "pendingio", String.valueOf(plugin.io().pendingCount()),
                "saves", String.valueOf(Metrics.SAVES.get()),
                "lastsave", last == 0 ? "-" : ((System.currentTimeMillis() - last) / 1000) + "s",
                "roll", String.format(Locale.ROOT, "%.0f", Metrics.rollRecent / 1000.0),
                "rollmax", String.format(Locale.ROOT, "%.0f", Metrics.rollMax / 1000.0),
                "rolls", String.valueOf(Metrics.ROLLS.get()),
                "open", String.format(Locale.ROOT, "%.0f", Metrics.openRecent / 1000.0),
                "openmax", String.format(Locale.ROOT, "%.0f", Metrics.openMax / 1000.0),
                "opens", String.valueOf(Metrics.OPENS.get()),
                "mem", String.valueOf((rt.totalMemory() - rt.freeMemory()) / 1048576))) {
            s.sendMessage(l);
        }
        return true;
    }

    /** Bukkit tasks owned by this plugin: 0-1 while idle (the shared timer), more only during animations. */
    private int pluginTasks() {
        int n = 0;
        for (org.bukkit.scheduler.BukkitWorker w : Bukkit.getScheduler().getActiveWorkers()) if (w.getOwner() == plugin) n++;
        for (org.bukkit.scheduler.BukkitTask t : Bukkit.getScheduler().getPendingTasks()) if (t.getOwner() == plugin) n++;
        return n;
    }

    private boolean economy(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin")) return true;
        for (String l : plugin.economies().describe()) s.sendMessage("  " + l);
        if (a.length > 1 && s instanceof Player) {
            ru.mysticchest.economy.Economies.Resolved r = plugin.economies().resolve(a[1]);
            if (r == null) plugin.lang().send(s, "shop.economy-disabled");
            else s.sendMessage("  " + r.provider.id() + " / " + (r.currency.isEmpty() ? "-" : r.currency) + " balance: " + r.provider.balance((Player) s, r.currency));
        } else {
            plugin.lang().send(s, "economy.hint");
        }
        return true;
    }

    private void send(CommandSender s, String line) {
        if (s instanceof Player) ru.mysticchest.util.Chat.send((Player) s, ru.mysticchest.util.Text.color(line));
        else s.sendMessage(org.bukkit.ChatColor.stripColor(ru.mysticchest.util.Text.color(line.replaceAll("\\[\\[([^|\\]]*)[^\\]]*\\]\\]", "$1"))));
    }

    private boolean track(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.chests")) return true;
        Player p = player(s);
        if (p == null) return true;
        ru.mysticchest.chest.ChestManager.Active chest = null;
        if (a.length > 1) {
            try { chest = plugin.chests().byId(Integer.parseInt(a[1])); } catch (NumberFormatException ignored) {}
        }
        if (chest == null) chest = plugin.chests().nearest(p.getLocation());
        if (chest == null) { plugin.lang().send(s, "nav.none"); return true; }
        if (a.length > 1 && !plugin.settings().onSpawn.enabled) { plugin.lang().send(s, "nav.none"); return true; }
        if (!plugin.tracker().start(p, chest)) { plugin.lang().send(s, "nav.none"); return true; }
        plugin.lang().send(s, "nav.started", "tier", chest.tier.name(plugin.lang().code(s)));
        return true;
    }

    private boolean event(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin")) return true;
        String sub = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "status";
        if (a.length < 2 || !a[1].equalsIgnoreCase("beacon")) { plugin.lang().send(s, "event.usage"); return true; }
        if (sub.equals("start")) {
            plugin.lang().send(s, plugin.beacon().running() ? "event.already" : "event.starting");
            if (!plugin.beacon().running()) plugin.beacon().trigger(true);
        } else if (sub.equals("stop")) {
            plugin.beacon().stop();
            plugin.lang().send(s, "event.stopped");
        } else {
            s.sendMessage(plugin.lang().get(s, "prefix") + "beacon: " + plugin.beacon().status());
        }
        return true;
    }

    private boolean mute(CommandSender s) {
        Player p = player(s);
        if (p == null) return true;
        plugin.lang().send(s, plugin.prefs().toggleMute(p.getUniqueId()) ? "mute.hidden" : "mute.shown");
        return true;
    }

    // ---- leaderboard / stats / boards ------------------------------------

    private static boolean knownStat(String st) {
        for (String k : ru.mysticchest.stats.StatsService.STATS) if (k.equals(st)) return true;
        return false;
    }

    private boolean top(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.top")) return true;
        String stat = a.length > 1 && knownStat(a[1].toLowerCase(Locale.ROOT)) ? a[1].toLowerCase(Locale.ROOT) : "opens";
        boolean all = a.length > 2 && a[2].equalsIgnoreCase("all") || a.length > 1 && a[1].equalsIgnoreCase("all");
        int n = plugin.settings().root.sub("leaderboard").integer("top-size", 10, 1, 50);
        java.util.List<ru.mysticchest.stats.StatsService.Row> rows = plugin.stats().top(stat, all, n);
        plugin.lang().send(s, "top.header", "stat", plugin.lang().get(s, "stat." + stat),
                "period", all ? plugin.lang().get(s, "top.all-time") : plugin.stats().periodKey());
        StringBuilder chips = new StringBuilder();
        for (String st : ru.mysticchest.stats.StatsService.STATS) {
            chips.append(st.equals(stat) ? "&a&l" : "&7").append("[[").append(st.equals(stat) ? "&a&l" : "&e").append("[").append(plugin.lang().get(s, "stat." + st)).append("]|run:/mystic top ").append(st).append(all ? " all" : "").append("|&7").append(plugin.lang().get(s, "top.show", "stat", plugin.lang().get(s, "stat." + st))).append("]] ");
        }
        chips.append("&8| [[&b[").append(plugin.lang().get(s, all ? "top.nav-period" : "top.nav-all")).append("]|run:/mystic top ").append(stat).append(all ? "" : " all").append("|&7").append(plugin.lang().get(s, "top.switch")).append("]]");
        send(s, chips.toString());
        if (rows.isEmpty()) s.sendMessage(plugin.lang().get(s, "top.empty"));
        int i = 1;
        for (ru.mysticchest.stats.StatsService.Row r : rows) {
            s.sendMessage(plugin.lang().get(s, "top.line", "rank", String.valueOf(i++), "name", r.name, "value", String.valueOf(r.value)));
        }
        return true;
    }

    private boolean stats(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.top")) return true;
        Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player ? (Player) s : null);
        if (target == null) { plugin.lang().send(s, "unknown-player", "player", a.length > 1 ? a[1] : "?"); return true; }
        plugin.lang().send(s, "stats.header", "player", target.getName(), "period", plugin.stats().periodKey());
        for (String st : ru.mysticchest.stats.StatsService.STATS) {
            s.sendMessage(plugin.lang().get(s, "stats.line", "stat", plugin.lang().get(s, "stat." + st),
                    "value", String.valueOf(plugin.stats().get(target.getUniqueId(), st, false)),
                    "total", String.valueOf(plugin.stats().get(target.getUniqueId(), st, true))));
        }
        return true;
    }

    private boolean board(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin")) return true;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("create") && a.length >= 4) {
            Player p = player(s);
            if (p == null) return true;
            String stat = a[3].toLowerCase(Locale.ROOT);
            if (!knownStat(stat)) { plugin.lang().send(s, "board.unknown", "name", a[3]); return true; }
            boolean ok = plugin.boards().create(a[2], p.getLocation().add(0, 2.2, 0), stat, a.length > 4 && a[4].equalsIgnoreCase("all"));
            plugin.lang().send(s, ok ? "board.created" : "board.exists", "name", a[2]);
        } else if (sub.equals("remove") && a.length >= 3) {
            plugin.lang().send(s, plugin.boards().remove(a[2]) ? "board.removed" : "board.unknown", "name", a[2]);
        } else if (sub.equals("list")) {
            if (plugin.boards().names().isEmpty()) plugin.lang().send(s, "board.none");
            for (String n : plugin.boards().names()) s.sendMessage(" - " + n);
        } else plugin.lang().send(s, "board.usage");
        return true;
    }

    // ---- chests / structures ---------------------------------------------

    private static final String[] DIRS = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};

    private boolean chests(CommandSender s) {
        if (!need(s, "mysticchest.chests")) return true;
        java.util.List<ru.mysticchest.chest.ChestManager.Active> list = plugin.chests().snapshot();
        boolean coords = s.hasPermission("mysticchest.chests.coords");
        Player p = s instanceof Player ? (Player) s : null;
        int shown = 0;
        plugin.lang().send(s, "chests.header", "count", String.valueOf(list.size()));
        for (ru.mysticchest.chest.ChestManager.Active a : list) {
            String where;
            if (coords) where = a.loc.getWorld().getName() + " " + a.loc.getBlockX() + " " + a.loc.getBlockY() + " " + a.loc.getBlockZ();
            else if (p != null && p.getWorld() == a.loc.getWorld()) {
                double dx = a.loc.getX() - p.getLocation().getX(), dz = a.loc.getZ() - p.getLocation().getZ();
                int dir = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0);
                where = "~" + (Math.round(Math.sqrt(dx * dx + dz * dz) / 50.0) * 50) + " " + plugin.lang().get(s, "chests.blocks") + ", "
                        + plugin.lang().get(s, "dir." + DIRS[((dir % 8) + 8) % 8]);
            } else where = a.loc.getWorld().getName();
            send(s, plugin.lang().get(s, "chests.line", "tier", a.tier.name(plugin.lang().code(s)),
                    "time", plugin.lang().time(s, plugin.chests().secondsLeft(a)), "where", where,
                    "track", coords || p == null ? plugin.lang().get(s, "button.track-small", "id", String.valueOf(a.id)) : plugin.lang().get(s, "button.track-small", "id", String.valueOf(a.id))));
            if (++shown >= 25) break;
        }
        return true;
    }

    private boolean structure(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin.structure")) return true;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        ru.mysticchest.structure.StructureService sv = plugin.structures();
        switch (sub) {
            case "wand": {
                Player p = player(s);
                if (p == null) return true;
                plugin.rewards().give(p, sv.wand().item(p));
                plugin.lang().send(p, "structure.wand.given");
                return true;
            }
            case "pos1": case "pos2": {
                Player p = player(s);
                if (p != null) sv.wand().set(p, sub.equals("pos1") ? 0 : 1, p.getLocation().getBlock().getRelative(0, -1, 0));
                return true;
            }
            case "save": {
                Player p = player(s);
                if (p == null) return true;
                if (a.length < 3 || !ru.mysticchest.structure.StructureCatalog.validName(a[2].toLowerCase(Locale.ROOT))) { plugin.lang().send(s, "structure.bad-name"); return true; }
                org.bukkit.block.Block[] sel = sv.wand().get(p);
                if (sel == null || sel[0] == null || sel[1] == null) { plugin.lang().send(s, "structure.no-selection"); return true; }
                String[] err = new String[1];
                ru.mysticchest.structure.Template t = ru.mysticchest.structure.Template.capture(a[2].toLowerCase(Locale.ROOT), sel[0], sel[1], err);
                if (t == null) { plugin.lang().send(s, "structure.err." + err[0]); return true; }
                plugin.io().writeNow(new java.io.File(sv.catalog().folder(), t.name + ".yml"), t.serialize());
                sv.catalog().add(t);
                plugin.lang().send(s, "structure.saved", "name", t.name, "blocks", String.valueOf(t.blocks()),
                        "w", String.valueOf(t.width()), "d", String.valueOf(t.depth()), "h", String.valueOf(t.height));
                if (!t.marks.isEmpty()) plugin.lang().send(s, "structure.marks-found", "loot", String.valueOf(t.count("loot")), "guard", String.valueOf(t.count("guard")), "boss", String.valueOf(t.count("boss")));
                return true;
            }
            case "delete":
                if (a.length < 3) { plugin.lang().send(s, "structure.usage"); return true; }
                plugin.lang().send(s, sv.catalog().remove(a[2]) ? "structure.deleted" : "structure.unknown", "name", a[2]);
                return true;
            case "edit": {
                Player p = player(s);
                if (p != null) ru.mysticchest.gui.StructureEditorGui.open(plugin, p);
                return true;
            }
            case "preview": {
                Player p = player(s);
                if (p == null) return true;
                if (a.length < 3) { plugin.lang().send(s, "structure.usage"); return true; }
                sv.preview(p, a[2], a.length > 3 ? a[3] : null);
                return true;
            }
            case "reload":
                sv.catalog().load();
                plugin.lang().send(s, "reloaded");
                return true;
            case "export": case "import": return exchange(s, a, sub.equals("export"));
            case "info": return structureInfo(s, a);
            case "set": return structureSet(s, a);
            default: {
                plugin.lang().send(s, "structure.list-header");
                for (ru.mysticchest.structure.StructureCatalog.Entry e : sv.catalog().all()) {
                    send(s, plugin.lang().get(s, "structure.list-line", "name", "[[&e" + e.id + "|run:/mystic structure preview " + e.id + "|&7Preview here for 40 seconds]]",
                            "kind", plugin.lang().get(s, e.custom() ? "structure.editor.custom" : "structure.editor.builtin"),
                            "weight", String.valueOf(e.weight), "chance", String.format(Locale.ROOT, "%.1f", sv.catalog().chance(e)),
                            "on", ru.mysticchest.util.Text.color(e.enabled ? "&aon" : "&coff")));
                }
                return true;
            }
        }
    }

    private void structureRow(CommandSender s, String key, String value) {
        plugin.lang().send(s, "structure.info.row", "key", plugin.lang().get(s, "structure.info." + key), "value", value);
    }

    private static String listOrAny(java.util.List<String> l) { return l.isEmpty() ? "-" : String.join(", ", l); }

    /** /mystic structure info <name>: size, markers, tags and filters of one structure. */
    private boolean structureInfo(CommandSender s, String[] a) {
        if (a.length < 3) { plugin.lang().send(s, "structure.usage"); return true; }
        ru.mysticchest.structure.StructureCatalog cat = plugin.structures().catalog();
        ru.mysticchest.structure.StructureCatalog.Entry e = cat.get(a[2]);
        if (e == null) { plugin.lang().send(s, "structure.unknown", "name", a[2]); return true; }
        plugin.lang().send(s, "structure.info.header", "name", e.id, "kind", plugin.lang().get(s, e.custom() ? "structure.editor.custom" : "structure.editor.builtin"));
        if (e.custom()) {
            ru.mysticchest.structure.Template t = e.template;
            structureRow(s, "size", t.width() + "×" + t.depth() + "×" + t.height + ", " + t.blocks() + " blocks");
            structureRow(s, "chest", t.chestX + " " + t.chestY + " " + t.chestZ);
            structureRow(s, "marks", "[loot] " + t.count("loot") + ", [guard] " + t.count("guard") + ", [boss] " + t.count("boss"));
        }
        structureRow(s, "weight", e.weight + " (" + String.format(Locale.ROOT, "%.1f", cat.chance(e)) + "%), " + (e.enabled ? "on" : "off"));
        structureRow(s, "tags", listOrAny(e.tags));
        structureRow(s, "tiers", listOrAny(e.tiers));
        structureRow(s, "biomes", listOrAny(e.biomes));
        structureRow(s, "worlds", listOrAny(e.worlds));
        structureRow(s, "rotate", e.rotate == null ? "global" : String.valueOf(e.rotate));
        structureRow(s, "debris", String.valueOf(e.debrisScale));
        structureRow(s, "theme", e.theme == null ? "AUTO" : e.theme);
        return true;
    }

    private static final List<String> SET_KEYS = Arrays.asList("tags", "tiers", "biomes", "worlds", "rotate", "debris", "weight", "theme", "enabled");

    /** /mystic structure set <name> <key> <value>: edits structures.yml without opening the file ("-" clears a list). */
    private boolean structureSet(CommandSender s, String[] a) {
        if (a.length < 5) { plugin.lang().send(s, "structure.set-usage"); return true; }
        ru.mysticchest.structure.StructureCatalog cat = plugin.structures().catalog();
        ru.mysticchest.structure.StructureCatalog.Entry e = cat.get(a[2]);
        if (e == null) { plugin.lang().send(s, "structure.unknown", "name", a[2]); return true; }
        String key = a[3].toLowerCase(Locale.ROOT), val = join(a, 4).trim();
        List<String> items = new ArrayList<String>();
        if (!val.equals("-")) for (String x : val.split("[,\\s]+")) if (!x.isEmpty()) items.add(x);
        try {
            if (key.equals("tags")) { e.tags.clear(); for (String x : items) e.tags.add(x.toLowerCase(Locale.ROOT)); }
            else if (key.equals("tiers")) {
                e.tiers.clear();
                for (String x : items) {
                    if (plugin.tiers().get(x) == null) { plugin.lang().send(s, "unknown-tier", "tier", x); return true; }
                    e.tiers.add(x.toLowerCase(Locale.ROOT));
                }
            }
            else if (key.equals("biomes")) { e.biomes.clear(); for (String x : items) e.biomes.add(x.toLowerCase(Locale.ROOT)); }
            else if (key.equals("worlds")) { e.worlds.clear(); e.worlds.addAll(items); }
            else if (key.equals("rotate")) e.rotate = val.equals("-") || val.equalsIgnoreCase("global") ? null : Boolean.valueOf(val.equalsIgnoreCase("true") || val.equalsIgnoreCase("on") || val.equalsIgnoreCase("yes"));
            else if (key.equals("debris")) e.debrisScale = Math.max(0, Math.min(5, Double.parseDouble(val)));
            else if (key.equals("weight")) e.weight = Math.max(0, Integer.parseInt(val));
            else if (key.equals("enabled")) e.enabled = val.equalsIgnoreCase("true") || val.equalsIgnoreCase("on") || val.equalsIgnoreCase("yes");
            else if (key.equals("theme")) e.theme = val.equals("-") || val.equalsIgnoreCase("AUTO") ? null : val.toUpperCase(Locale.ROOT);
            else { plugin.lang().send(s, "structure.set-usage"); return true; }
        } catch (NumberFormatException ex) { plugin.lang().send(s, "structure.set-usage"); return true; }
        cat.save();
        plugin.lang().send(s, "structure.set-done", "name", e.id, "key", key, "value", val);
        return true;
    }

    /** /mystic structure export <name> and import <file> [name] [overwrite]: share files through plugins/MysticChest/exchange/. */
    private boolean exchange(CommandSender s, String[] a, boolean export) {
        ru.mysticchest.structure.StructureCatalog cat = plugin.structures().catalog();
        if (a.length < 3) { plugin.lang().send(s, "structure.usage"); return true; }
        String src = a[2].toLowerCase(Locale.ROOT);
        if (src.endsWith(".yml")) src = src.substring(0, src.length() - 4);
        if (!ru.mysticchest.structure.StructureCatalog.validName(src)) { plugin.lang().send(s, "structure.bad-name"); return true; }
        java.io.File dir = new java.io.File(plugin.getDataFolder(), "exchange");
        if (export) {
            ru.mysticchest.structure.StructureCatalog.Entry e = cat.get(src);
            if (e == null || !e.custom()) { plugin.lang().send(s, "structure.unknown", "name", a[2]); return true; }
            java.io.File out = new java.io.File(dir, src + ".yml");
            dir.mkdirs();
            plugin.io().writeNow(out, e.template.serialize());
            plugin.lang().send(s, "structure.exported", "name", src, "path", "plugins/MysticChest/exchange/" + src + ".yml");
            return true;
        }
        java.io.File in = new java.io.File(dir, src + ".yml");
        String name = a.length > 3 ? a[3].toLowerCase(Locale.ROOT) : src;
        if (!ru.mysticchest.structure.StructureCatalog.validName(name)) { plugin.lang().send(s, "structure.bad-name"); return true; }
        if (!in.isFile()) { plugin.lang().send(s, "structure.import-missing", "file", src + ".yml"); return true; }
        ru.mysticchest.structure.StructureCatalog.Entry old = cat.get(name);
        boolean overwrite = a.length > 4 && a[4].equalsIgnoreCase("overwrite");
        if (old != null && (!old.custom() || !overwrite)) {
            plugin.lang().send(s, old.custom() ? "structure.import-exists" : "structure.import-builtin", "name", name);
            return true;
        }
        ru.mysticchest.structure.Template t = ru.mysticchest.structure.Template.load(name, in, plugin.getLogger());
        if (t == null || t.blocks() < 4) { plugin.lang().send(s, "structure.import-bad", "file", src + ".yml"); return true; }
        plugin.io().writeNow(new java.io.File(cat.folder(), name + ".yml"), t.serialize());
        cat.add(t);
        plugin.lang().send(s, "structure.imported", "name", t.name, "blocks", String.valueOf(t.blocks()),
                "w", String.valueOf(t.width()), "d", String.valueOf(t.depth()), "h", String.valueOf(t.height));
        return true;
    }

    // ---- debug ---------------------------------------------------------

    private static String span(long ms) {
        long sec = Math.max(0, ms / 1000);
        if (sec >= 3600) return (sec / 3600) + "h " + (sec % 3600 / 60) + "m";
        if (sec >= 60) return (sec / 60) + "m " + (sec % 60) + "s";
        return sec + "s";
    }

    /** /mystic debug [clear]: why chests do (not) appear - live conditions per profile plus the recent decisions. */
    private boolean debug(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin")) return true;
        if (a.length > 1 && a[1].equalsIgnoreCase("clear")) { plugin.diag().clear(); plugin.lang().send(s, "debug.cleared"); return true; }
        long now = System.currentTimeMillis();
        int online = org.bukkit.Bukkit.getOnlinePlayers().size();
        long cd = plugin.settings().cdSpawn > 0 ? plugin.cooldowns().remaining(ru.mysticchest.cooldown.CooldownManager.GLOBAL, "spawn") : 0;
        plugin.lang().send(s, "debug.header", "online", String.valueOf(online), "chests", String.valueOf(plugin.chests().count()),
                "cooldown", cd > 0 ? span(cd) : "-");
        for (ru.mysticchest.spawn.SpawnProfile p : plugin.spawner().profiles().values()) {
            List<String> block = new ArrayList<String>();
            if (!p.enabled) block.add(plugin.lang().get(s, "debug.b-disabled"));
            else {
                if (online < p.minPlayers) block.add(plugin.lang().get(s, "debug.b-players", "online", String.valueOf(online), "min", String.valueOf(p.minPlayers)));
                int act = plugin.chests().countProfile(p.name);
                if (act >= p.maxActive) block.add(plugin.lang().get(s, "debug.b-active", "n", String.valueOf(act), "max", String.valueOf(p.maxActive)));
                if (cd > 0) block.add(plugin.lang().get(s, "debug.b-cooldown", "t", span(cd)));
                boolean anyTier = false;
                for (String id : p.tierWeights.keySet()) if (plugin.tiers().get(id) != null) anyTier = true;
                if (!anyTier) block.add(plugin.lang().get(s, "debug.b-tiers"));
                if (p.mode == ru.mysticchest.spawn.SpawnProfile.Mode.FIXED_POINTS && p.pointNames.isEmpty() && plugin.spawner().locators().points().isEmpty())
                    block.add(plugin.lang().get(s, "debug.b-points"));
            }
            long next = plugin.spawner().nextAt(p.name);
            plugin.lang().send(s, "debug.profile", "name", p.name, "trigger", p.trigger.name(), "mode", p.mode.name(),
                    "next", !p.enabled ? "-" : next > now ? span(next - now) : "?",
                    "state", block.isEmpty() ? plugin.lang().get(s, "debug.ready") : plugin.lang().get(s, "debug.blocked", "why", String.join("; ", block)),
                    "active", String.valueOf(plugin.chests().countProfile(p.name)), "max", String.valueOf(p.maxActive));
        }
        List<ru.mysticchest.core.Diag.Note> notes = plugin.diag().last(15);
        plugin.lang().send(s, notes.isEmpty() ? "debug.no-notes" : "debug.notes");
        for (ru.mysticchest.core.Diag.Note n : notes)
            plugin.lang().send(s, "debug.note", "ago", span(now - n.at), "src", n.source, "text", n.text);
        return true;
    }

    // ---- loot ----------------------------------------------------------

    private boolean loot(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin.loot")) return true;
        if (a.length < 3) { plugin.lang().send(s, "usage.loot"); return true; }
        String sub = a[1].toLowerCase(Locale.ROOT);
        if (sub.equals("preset")) return lootPreset(s, a);
        if (sub.equals("import")) return lootImport(s, a);
        Tier t = tier(s, a[2]);
        if (t == null) return true;
        String tn = tn(s, t);
        switch (sub) {
            case "fill": {
                Player p = player(s);
                if (p == null) return true;
                int weight = plugin.settings().defaultWeight;
                boolean keep = false;
                for (int i = 3; i < a.length; i++) {
                    if (a[i].equalsIgnoreCase("--keep")) keep = true;
                    else try { weight = Math.max(1, Integer.parseInt(a[i])); } catch (NumberFormatException e) { plugin.lang().send(s, "usage.loot-fill"); return true; }
                }
                ru.mysticchest.gui.LootFillGui.open(plugin, p, t, weight, keep);
                return true;
            }
            case "copy": {
                Tier to = a.length > 3 ? tier(s, a[3]) : null;
                if (to == null) { if (a.length <= 3) plugin.lang().send(s, "usage.loot-copy"); return true; }
                if (to == t) { plugin.lang().send(s, "usage.loot-copy"); return true; }
                boolean replace = a.length > 4 && a[4].equalsIgnoreCase("--replace");
                int n = plugin.loot().append(to, new ArrayList<LootEntry>(plugin.loot().entries(t)), 1.0, replace);
                plugin.lang().send(s, "loot.copied", "count", String.valueOf(n), "from", tn, "to", tn(s, to));
                return true;
            }
            case "check": return lootCheck(s, t);
            case "export": {
                String name = a.length > 3 ? a[3].toLowerCase(Locale.ROOT) : t.id;
                if (!ru.mysticchest.structure.StructureCatalog.validName(name)) { plugin.lang().send(s, "structure.bad-name"); return true; }
                java.io.File dir = new java.io.File(plugin.getDataFolder(), "exchange");
                dir.mkdirs();
                plugin.io().writeNow(new java.io.File(dir, "loot-" + name + ".yml"), plugin.loot().serialize(plugin.loot().entries(t), "## Loot exported by /mystic loot export. Import: /mystic loot import loot-" + name + " <tier> [--replace]\n"));
                plugin.lang().send(s, "loot.exported", "tier", tn, "path", "plugins/MysticChest/exchange/loot-" + name + ".yml", "file", "loot-" + name);
                return true;
            }
            case "add": {
                Player p = player(s);
                if (p == null) return true;
                int weight = plugin.settings().defaultWeight;
                boolean keep = false, hand = false;
                for (int i = 3; i < a.length; i++) {
                    if (a[i].equalsIgnoreCase("--keep")) keep = true;
                    else if (a[i].equalsIgnoreCase("--hand")) hand = true;
                    else {
                        try { weight = Math.max(1, Integer.parseInt(a[i])); }
                        catch (NumberFormatException e) { plugin.lang().send(s, "usage.loot-add"); return true; }
                    }
                }
                int n = LootTools.addFromInventory(plugin, p, t, weight, keep, hand);
                plugin.lang().send(s, n == 0 ? "loot.nothing" : "loot.added", "count", String.valueOf(n), "tier", tn, "weight", String.valueOf(weight));
                return true;
            }
            case "addcmd": {
                Player p = player(s);
                if (p == null) return true;
                if (a.length < 5) { plugin.lang().send(s, "usage.loot-addcmd"); return true; }
                int weight;
                try { weight = Math.max(1, Integer.parseInt(a[3])); }
                catch (NumberFormatException e) { plugin.lang().send(s, "usage.loot-addcmd"); return true; }
                String cmd = join(a, 4);
                ItemStack icon = held(p);
                plugin.loot().addCommand(t, icon, weight, cmd.startsWith("/") ? cmd.substring(1) : cmd);
                plugin.loot().commit(t);
                plugin.lang().send(s, "loot.added-cmd", "tier", tn, "weight", String.valueOf(weight));
                return true;
            }
            case "cmd": return lootCmd(s, a, t);
            case "weight": {
                LootEntry e = entry(s, t, a, 3);
                if (e == null) return true;
                if (a.length < 5) { plugin.lang().send(s, "usage.loot-weight"); return true; }
                try { e.weight = Math.max(1, Integer.parseInt(a[4])); }
                catch (NumberFormatException ex) { plugin.lang().send(s, "usage.loot-weight"); return true; }
                plugin.loot().commit(t);
                plugin.lang().send(s, "loot.weight-set", "n", a[3], "weight", String.valueOf(e.weight));
                return true;
            }
            case "remove": {
                LootEntry e = entry(s, t, a, 3);
                if (e == null) return true;
                plugin.loot().remove(t, Integer.parseInt(a[3]) - 1);
                plugin.loot().commit(t);
                if (s instanceof Player && a.length > 4 && a[4].equalsIgnoreCase("--return") && e.giveItem) {
                    plugin.rewards().give((Player) s, e.roll());
                }
                plugin.lang().send(s, "loot.removed", "n", a[3], "tier", tn);
                return true;
            }
            case "list": {
                List<LootEntry> list = plugin.loot().entries(t);
                plugin.lang().send(s, "loot.list-header", "tier", tn, "count", String.valueOf(list.size()));
                int i = 1;
                for (LootEntry e : list) {
                    s.sendMessage(plugin.lang().get(s, "loot.list-line", "n", String.valueOf(i++), "item", Items.name(e.item),
                            "amount", e.min == e.max ? String.valueOf(e.min) : e.min + "-" + e.max,
                            "weight", String.valueOf(e.weight), "chance", String.format(Locale.ROOT, "%.2f", e.chance),
                            "cmds", String.valueOf(e.commands.size())));
                }
                return true;
            }
            case "edit": {
                Player p = player(s);
                if (p != null) LootEditorGui.open(plugin, p, t, 0);
                return true;
            }
            case "clear": {
                if (a.length < 4 || !a[3].equalsIgnoreCase("confirm")) { plugin.lang().send(s, "loot.clear-confirm", "tier", tn); return true; }
                plugin.loot().clear(t);
                plugin.loot().commit(t);
                plugin.lang().send(s, "loot.cleared", "tier", tn);
                return true;
            }
            default:
                plugin.lang().send(s, "usage.loot");
                return true;
        }
    }

    /** /mystic loot check <tier>: total weight, rare share, duplicates, entries lost to unsupported items. */
    private boolean lootCheck(CommandSender s, Tier t) {
        List<LootEntry> list = plugin.loot().entries(t);
        long total = 0;
        int rare = 0, cmds = 0, dup = 0, noItem = 0;
        double rareChance = 0;
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (LootEntry e : list) {
            total += e.weight;
            if (e.rare) { rare++; rareChance += e.chance; }
            if (!e.commands.isEmpty()) cmds++;
            if (!e.giveItem) noItem++;
            ItemStack one = e.item.clone();
            one.setAmount(1);
            if (e.giveItem && !seen.add(one.toString())) dup++;
        }
        plugin.lang().send(s, "loot.check", "tier", tn(s, t), "entries", String.valueOf(list.size()), "weight", String.valueOf(total),
                "rare", String.valueOf(rare), "rarechance", String.format(Locale.ROOT, "%.1f", rareChance), "cmds", String.valueOf(cmds),
                "dup", String.valueOf(dup), "skipped", String.valueOf(plugin.loot().skipped(t)));
        if (list.isEmpty()) plugin.lang().send(s, "loot.check-empty");
        else if (dup > 0) plugin.lang().send(s, "loot.check-dup");
        if (plugin.loot().skipped(t) > 0) plugin.lang().send(s, "loot.check-skipped");
        return true;
    }

    private static final List<String> PRESETS = Arrays.asList("food", "diamond-gear", "resources", "redstone", "nether");

    private java.io.File presetFile(String name) {
        String res = "presets/" + name + ".yml";
        if (PRESETS.contains(name)) return plugin.configs().ensure(res);
        return new java.io.File(plugin.getDataFolder(), res);
    }

    private List<String> presetNames() {
        List<String> out = new ArrayList<String>(PRESETS);
        java.io.File[] fs = new java.io.File(plugin.getDataFolder(), "presets").listFiles();
        if (fs != null) for (java.io.File f : fs) {
            String n = f.getName();
            if (n.endsWith(".yml") && !out.contains(n.substring(0, n.length() - 4))) out.add(n.substring(0, n.length() - 4));
        }
        return out;
    }

    /** /mystic loot preset list | save <tier> <name> | <tier> <name> [x<multiplier>] [--replace] */
    private boolean lootPreset(CommandSender s, String[] a) {
        String first = a[2].toLowerCase(Locale.ROOT);
        if (first.equals("list")) {
            plugin.lang().send(s, "loot.preset-list", "names", String.join(", ", presetNames()));
            return true;
        }
        if (first.equals("save")) {
            Tier t = a.length > 3 ? tier(s, a[3]) : null;
            if (t == null) { if (a.length <= 3) plugin.lang().send(s, "usage.loot-preset"); return true; }
            String name = a.length > 4 ? a[4].toLowerCase(Locale.ROOT) : t.id;
            if (!ru.mysticchest.structure.StructureCatalog.validName(name)) { plugin.lang().send(s, "structure.bad-name"); return true; }
            java.io.File f = new java.io.File(plugin.getDataFolder(), "presets/" + name + ".yml");
            f.getParentFile().mkdirs();
            plugin.io().writeNow(f, plugin.loot().serialize(plugin.loot().entries(t), "## Preset saved by /mystic loot preset save. Load: /mystic loot preset <tier> " + name + "\n"));
            plugin.lang().send(s, "loot.preset-saved", "name", name, "count", String.valueOf(plugin.loot().entries(t).size()));
            return true;
        }
        Tier t = tier(s, a[2]);
        if (t == null) return true;
        if (a.length < 4) { plugin.lang().send(s, "usage.loot-preset"); return true; }
        String name = a[3].toLowerCase(Locale.ROOT);
        java.io.File f = presetFile(name);
        if (!f.isFile()) { plugin.lang().send(s, "loot.preset-missing", "name", name, "names", String.join(", ", presetNames())); return true; }
        double mult = 1.0;
        boolean replace = false;
        for (int i = 4; i < a.length; i++) {
            if (a[i].equalsIgnoreCase("--replace")) replace = true;
            else if (a[i].matches("(?i)x\\d+(\\.\\d+)?")) mult = Double.parseDouble(a[i].substring(1));
        }
        int[] skip = new int[1];
        List<LootEntry> src = plugin.loot().read(f, "presets/" + name + ".yml", skip);
        int n = plugin.loot().append(t, src, mult, replace);
        plugin.lang().send(s, "loot.preset-loaded", "name", name, "count", String.valueOf(n), "tier", tn(s, t), "skipped", String.valueOf(skip[0]));
        return true;
    }

    /** /mystic loot import <file> <tier> [--replace]: a file from plugins/MysticChest/exchange/. */
    private boolean lootImport(CommandSender s, String[] a) {
        if (a.length < 4) { plugin.lang().send(s, "usage.loot-import"); return true; }
        String file = a[2].toLowerCase(Locale.ROOT);
        if (file.endsWith(".yml")) file = file.substring(0, file.length() - 4);
        if (!ru.mysticchest.structure.StructureCatalog.validName(file)) { plugin.lang().send(s, "structure.bad-name"); return true; }
        Tier t = tier(s, a[3]);
        if (t == null) return true;
        java.io.File f = new java.io.File(new java.io.File(plugin.getDataFolder(), "exchange"), file + ".yml");
        if (!f.isFile()) { plugin.lang().send(s, "structure.import-missing", "file", file + ".yml"); return true; }
        int[] skip = new int[1];
        List<LootEntry> src = plugin.loot().read(f, "exchange/" + file + ".yml", skip);
        if (src.isEmpty()) { plugin.lang().send(s, "loot.import-empty", "file", file + ".yml"); return true; }
        int n = plugin.loot().append(t, src, 1.0, a.length > 4 && a[4].equalsIgnoreCase("--replace"));
        plugin.lang().send(s, "loot.preset-loaded", "name", file, "count", String.valueOf(n), "tier", tn(s, t), "skipped", String.valueOf(skip[0]));
        return true;
    }

    private boolean lootCmd(CommandSender s, String[] a, Tier t) {
        LootEntry e = entry(s, t, a, 3);
        if (e == null) return true;
        String op = a.length > 4 ? a[4].toLowerCase(Locale.ROOT) : "list";
        if (op.equals("list")) {
            int n = 1;
            if (e.commands.isEmpty()) plugin.lang().send(s, "loot.no-commands");
            for (LootEntry.Cmd c : e.commands) {
                s.sendMessage(plugin.lang().get(s, "loot.cmd-line", "n", String.valueOf(n++), "run", c.asPlayer ? "PLAYER" : "CONSOLE",
                        "chance", String.valueOf(c.chance), "command", c.text));
            }
        } else if (op.equals("add") && a.length > 5) {
            int i = 5;
            boolean asPlayer = false;
            int chance = 100;
            while (i < a.length - 1) {
                if (a[i].equalsIgnoreCase("player:") || a[i].equalsIgnoreCase("p:")) { asPlayer = true; i++; }
                else if (a[i].matches("\\d{1,3}%")) { chance = Integer.parseInt(a[i].substring(0, a[i].length() - 1)); i++; }
                else break;
            }
            String cmd = join(a, i);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            e.commands.add(new LootEntry.Cmd(asPlayer, cmd, chance));
            plugin.loot().commit(t);
            plugin.lang().send(s, "loot.cmd-added", "n", a[3]);
        } else if (op.equals("remove") && a.length > 5) {
            try {
                int n = Integer.parseInt(a[5]);
                if (n < 1 || n > e.commands.size()) throw new NumberFormatException();
                e.commands.remove(n - 1);
                plugin.loot().commit(t);
                plugin.lang().send(s, "loot.cmd-removed", "n", a[5]);
            } catch (NumberFormatException ex) {
                plugin.lang().send(s, "usage.loot-cmd");
            }
        } else if (op.equals("clear")) {
            e.commands.clear();
            plugin.loot().commit(t);
            plugin.lang().send(s, "loot.cmd-cleared", "n", a[3]);
        } else {
            plugin.lang().send(s, "usage.loot-cmd");
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private ItemStack held(Player p) {
        ItemStack h = p.getInventory().getItemInHand();
        if (ItemCodec.isAir(h)) return new ItemStack(org.bukkit.Material.PAPER);
        ItemStack c = h.clone();
        c.setAmount(1);
        return c;
    }

    private LootEntry entry(CommandSender s, Tier t, String[] a, int idx) {
        if (a.length <= idx) { plugin.lang().send(s, "usage.loot"); return null; }
        try {
            int n = Integer.parseInt(a[idx]);
            List<LootEntry> l = plugin.loot().entries(t);
            if (n >= 1 && n <= l.size()) return l.get(n - 1);
        } catch (NumberFormatException ignored) {}
        plugin.lang().send(s, "loot.bad-index", "n", a[idx]);
        return null;
    }

    private static String join(String[] a, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < a.length; i++) sb.append(i > from ? " " : "").append(a[i]);
        return sb.toString();
    }

    // ---- tab complete --------------------------------------------------

    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<String>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String r : ROOT) if (perm(s, r)) out.add(r);
        } else {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") && a.length == 2) return null;
            if ((sub.equals("give") && a.length == 3) || (sub.equals("preview") && a.length == 2)
                    || (sub.equals("spawn") && a.length == 2)) tiers(out);
            else if (sub.equals("spawn") && a.length == 3) { out.add("here"); out.addAll(plugin.spawner().profiles().keySet()); }
            else if (sub.equals("spawn") && a.length == 4) { for (ru.mysticchest.structure.StructureCatalog.Entry en : plugin.structures().catalog().all()) out.add(en.id); }
            else if (sub.equals("event") && a.length == 2) out.add("beacon");
            else if (sub.equals("event") && a.length == 3) out.addAll(Arrays.asList("start", "stop", "status"));
            else if (sub.equals("top") && a.length == 2) { out.addAll(Arrays.asList(ru.mysticchest.stats.StatsService.STATS)); out.add("all"); }
            else if (sub.equals("top") && a.length == 3) out.add("all");
            else if (sub.equals("board") && a.length == 2) out.addAll(Arrays.asList("create", "remove", "list"));
            else if (sub.equals("board") && a.length == 4 && a[1].equalsIgnoreCase("create")) out.addAll(Arrays.asList(ru.mysticchest.stats.StatsService.STATS));
            else if (sub.equals("board") && a.length == 3 && a[1].equalsIgnoreCase("remove")) out.addAll(plugin.boards().names());
            else if (sub.equals("structure") && a.length == 2) out.addAll(Arrays.asList("wand", "pos1", "pos2", "save", "delete", "edit", "list", "preview", "reload", "export", "import", "info", "set"));
            else if (sub.equals("structure") && (a.length == 3) && (a[1].equalsIgnoreCase("preview") || a[1].equalsIgnoreCase("delete") || a[1].equalsIgnoreCase("export") || a[1].equalsIgnoreCase("info") || a[1].equalsIgnoreCase("set"))) { for (ru.mysticchest.structure.StructureCatalog.Entry en : plugin.structures().catalog().all()) out.add(en.id); }
            else if (sub.equals("structure") && a.length == 4 && a[1].equalsIgnoreCase("set")) out.addAll(SET_KEYS);
            else if (sub.equals("structure") && a.length == 4 && a[1].equalsIgnoreCase("preview")) out.addAll(Arrays.asList("r0", "r1", "r2", "r3"));
            else if (sub.equals("spawn") && a.length == 5) { for (ru.mysticchest.structure.Theme th : ru.mysticchest.structure.Theme.values()) out.add(th.name().toLowerCase()); }
            else if (sub.equals("loot")) {
                if (a.length == 2) out.addAll(LOOT);
                else if (a.length == 3 && a[1].equalsIgnoreCase("preset")) { out.add("list"); out.add("save"); tiers(out); }
                else if (a.length == 3 && a[1].equalsIgnoreCase("import")) {
                    java.io.File[] fs = new java.io.File(plugin.getDataFolder(), "exchange").listFiles();
                    if (fs != null) for (java.io.File f : fs) if (f.getName().endsWith(".yml")) out.add(f.getName().substring(0, f.getName().length() - 4));
                }
                else if (a.length == 3) tiers(out);
                else if (a.length == 4 && a[1].equalsIgnoreCase("preset")) { if (a[2].equalsIgnoreCase("save")) tiers(out); else out.addAll(presetNames()); }
                else if (a.length == 4 && (a[1].equalsIgnoreCase("copy") || a[1].equalsIgnoreCase("import"))) tiers(out);
                else if (a[1].equalsIgnoreCase("fill")) out.add("--keep");
                else if (a[1].equalsIgnoreCase("copy") || a[1].equalsIgnoreCase("import")) out.add("--replace");
                else if (a[1].equalsIgnoreCase("preset") && a.length >= 5) { out.add("--replace"); out.add("x2"); out.add("x0.5"); }
                else if (a.length == 5 && a[1].equalsIgnoreCase("cmd")) out.addAll(Arrays.asList("add", "remove", "list", "clear"));
                else if (a[1].equalsIgnoreCase("add")) out.addAll(Arrays.asList("--keep", "--hand"));
                else if (a[1].equalsIgnoreCase("remove") && a.length == 5) out.add("--return");
                else if (a[1].equalsIgnoreCase("clear") && a.length == 4) out.add("confirm");
            } else if (sub.equals("point") && a.length == 2) out.addAll(Arrays.asList("add", "remove", "list"));
            else if (sub.equals("point") && a.length == 3 && a[1].equalsIgnoreCase("remove")) out.addAll(plugin.locators().points().keySet());
        }
        List<String> res = new ArrayList<String>();
        for (String o : out) if (o.toLowerCase(Locale.ROOT).startsWith(last)) res.add(o);
        return res;
    }

    private boolean perm(CommandSender s, String sub) {
        switch (sub) {
            case "give": case "spawn": case "reload": case "perf": case "debug": case "point": case "economy": return s.hasPermission("mysticchest.admin");
            case "loot": return s.hasPermission("mysticchest.admin.loot");
            case "structure": return s.hasPermission("mysticchest.admin.structure");
            case "event": return s.hasPermission("mysticchest.admin");
            case "chests": case "track": return s.hasPermission("mysticchest.chests");
            case "mute": return true;
            case "top": case "stats": return s.hasPermission("mysticchest.top");
            case "board": return s.hasPermission("mysticchest.admin");
            case "shop": return s.hasPermission("mysticchest.shop");
            case "compass": return s.hasPermission("mysticchest.compass");
            case "preview": return s.hasPermission("mysticchest.preview");
            default: return true;
        }
    }

    private void tiers(List<String> out) { for (Tier t : plugin.tiers().all()) out.add(t.id); }
}
