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
            "perf", "loot", "point", "help");
    private static final List<String> LOOT = Arrays.asList("add", "addcmd", "cmd", "weight", "remove", "list", "edit", "clear");

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
            for (String l : plugin.lang().list(s, s.hasPermission("mysticchest.admin") ? "help.admin" : "help.player")) s.sendMessage(l);
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
                for (Tier t : plugin.tiers().all()) s.sendMessage(" - " + t.id + "  " + tn(s, t));
                return true;
            case "reload":
                if (!need(s, "mysticchest.admin")) return true;
                plugin.reloadAll();
                plugin.lang().send(s, "reloaded");
                return true;
            case "give": return give(s, a);
            case "spawn": return spawn(s, a);
            case "perf": return perf(s);
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
            if (!plugin.spawner().spawnHere(t, p.getLocation())) plugin.lang().send(s, "spawn.failed");
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

    // ---- loot ----------------------------------------------------------

    private boolean loot(CommandSender s, String[] a) {
        if (!need(s, "mysticchest.admin.loot")) return true;
        if (a.length < 3) { plugin.lang().send(s, "usage.loot"); return true; }
        String sub = a[1].toLowerCase(Locale.ROOT);
        Tier t = tier(s, a[2]);
        if (t == null) return true;
        String tn = tn(s, t);
        switch (sub) {
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
            else if (sub.equals("loot")) {
                if (a.length == 2) out.addAll(LOOT);
                else if (a.length == 3) tiers(out);
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
            case "give": case "spawn": case "reload": case "perf": case "point": return s.hasPermission("mysticchest.admin");
            case "loot": return s.hasPermission("mysticchest.admin.loot");
            case "shop": return s.hasPermission("mysticchest.shop");
            case "preview": return s.hasPermission("mysticchest.preview");
            default: return true;
        }
    }

    private void tiers(List<String> out) { for (Tier t : plugin.tiers().all()) out.add(t.id); }
}
