package ru.mysticchest.effects;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.config.Settings;
import ru.mysticchest.util.Text;

import java.util.List;

/** One boss bar per standing chest: tier, time left, place. Updated once a second by the chest ticker. */
public final class BossBars {
    private final MysticChestPlugin plugin;

    public BossBars(MysticChestPlugin plugin) { this.plugin = plugin; }

    private static BarColor colorOf(Color c) {
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
        if (r > 200 && g > 200 && b > 200) return BarColor.WHITE;
        if (r >= g && r >= b) return g > 140 ? BarColor.YELLOW : (b > 140 ? BarColor.PINK : BarColor.RED);
        if (g >= r && g >= b) return BarColor.GREEN;
        return r > 120 ? BarColor.PURPLE : BarColor.BLUE;
    }

    private BarColor color(ChestManager.Active a) {
        Settings s = plugin.settings();
        if (s.bossAutoColor) return colorOf(a.tier.color);
        try { return BarColor.valueOf(s.bossColor.toUpperCase()); } catch (IllegalArgumentException e) { return BarColor.PURPLE; }
    }

    private BarStyle style() {
        try { return BarStyle.valueOf(plugin.settings().bossStyle.toUpperCase()); } catch (IllegalArgumentException e) { return BarStyle.SOLID; }
    }

    public void add(ChestManager.Active a) {
        if (!plugin.settings().bossEnabled) return;
        try {
            a.bar = Bukkit.createBossBar(title(a), color(a), style());
            a.bar.setProgress(1.0);
            sync(a);
        } catch (Throwable t) {
            a.bar = null;   // servers without the boss bar API simply go without it
        }
    }

    public void remove(ChestManager.Active a) {
        if (a.bar == null) return;
        try { a.bar.removeAll(); a.bar.setVisible(false); } catch (Throwable ignored) {}
        a.bar = null;
    }

    private String title(ChestManager.Active a) {
        org.bukkit.Location l = a.loc;
        String code = plugin.lang().code(Bukkit.getConsoleSender());
        boolean asleep = a.sleeping();
        return plugin.lang().get(asleep ? "bossbar.title-sleeping" : "bossbar.title", "tier", a.tier.name(code),
                "time", plugin.lang().time(Bukkit.getConsoleSender(), asleep ? plugin.chests().wakeIn(a) : plugin.chests().secondsLeft(a)),
                "x", String.valueOf(l.getBlockX()), "y", String.valueOf(l.getBlockY()), "z", String.valueOf(l.getBlockZ()),
                "world", l.getWorld().getName(),
                "mode", a.mode == null ? "?" : plugin.lang().get("mode." + a.mode.name().toLowerCase()));
    }

    private boolean sees(Settings s, Player p, ChestManager.Active a) {
        if (s.bossViewers == Settings.Viewers.ALL) return true;
        if (p.getWorld() != a.loc.getWorld()) return false;
        if (s.bossViewers == Settings.Viewers.WORLD) return true;
        return p.getLocation().distanceSquared(a.loc) <= (double) s.bossRadius * s.bossRadius;
    }

    private void sync(ChestManager.Active a) {
        Settings s = plugin.settings();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean should = sees(s, p, a), has = a.bar.getPlayers().contains(p);
            if (should && !has) a.bar.addPlayer(p);
            else if (!should && has) a.bar.removePlayer(p);
        }
    }

    /** Once a second: time in the title, progress = time left / total, viewers follow the players. */
    public void update(List<ChestManager.Active> chests) {
        for (ChestManager.Active a : chests) {
            if (a.bar == null) continue;
            try {
                boolean asleep = a.sleeping();
                long total = asleep ? Math.max(1, a.activationMs / 1000) : Math.max(1, a.tier.ttlSeconds(plugin.settings()));
                long left = asleep ? plugin.chests().wakeIn(a) : plugin.chests().secondsLeft(a);
                a.bar.setTitle(title(a));
                a.bar.setProgress(Math.max(0, Math.min(1, left / (double) total)));
                sync(a);
            } catch (Throwable ignored) {}
        }
    }

    public void shutdown(List<ChestManager.Active> chests) {
        for (ChestManager.Active a : chests) remove(a);
    }
}
