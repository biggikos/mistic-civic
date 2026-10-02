package ru.mysticchest.event;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.chest.Reward;
import ru.mysticchest.config.Layered;
import ru.mysticchest.util.Potions;

import java.util.List;

/**
 * "Death chest": a bloody zone around a chest of a tier with deathzone enabled. Players inside glow (or get any
 * other effects), a death in the zone drops extra loot from the tier's pool for the killer to grab, the kill is
 * announced and can pay commands. Pair it with a FIXED_POINTS profile on your PvP arena and capture: ALWAYS.
 */
public final class DeathZone implements Listener {
    private final MysticChestPlugin plugin;

    public DeathZone(MysticChestPlugin plugin) { this.plugin = plugin; }

    /** Once a second from the chest ticker. */
    public void update(List<ChestManager.Active> chests) {
        for (ChestManager.Active a : chests) {
            Layered l = a.tier.layered("deathzone", plugin.settings());
            if (!l.bool("enabled", false)) continue;
            double r = l.integer("radius", 25, 3, 200);
            List<String> effects = l.strings("effects");
            if (effects.isEmpty()) effects.add("GLOWING:3:0");
            for (Player p : a.loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(a.loc) > r * r || p.isDead()) continue;
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                for (String s : effects) {
                    PotionEffect pe = Potions.parse(s, 3);
                    if (pe != null) p.addPotionEffect(pe, true);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Location at = victim.getLocation();
        for (ChestManager.Active a : plugin.chests().snapshot()) {
            if (a.loc.getWorld() != at.getWorld()) continue;
            Layered l = a.tier.layered("deathzone", plugin.settings());
            if (!l.bool("enabled", false)) continue;
            double r = l.integer("radius", 25, 3, 200);
            if (at.distanceSquared(a.loc) > r * r) continue;
            Player killer = victim.getKiller();
            plugin.effects().burst("DAMAGE_INDICATOR", at.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
            plugin.effects().playSound(victim, at, "ENTITY_WITHER_SPAWN", 0.4f, 1.6f);
            int rolls = l.integer("drop-rolls", 1, 0, 20);
            if (rolls > 0 && !a.tier.pool.isEmpty()) {
                for (Reward rw : plugin.rewards().roll(killer != null ? killer : victim, a.tier, rolls)) {
                    if (rw.entry.giveItem) at.getWorld().dropItemNaturally(at, rw.stack.clone());
                    if (killer != null) plugin.rewards().apply(killer, a.tier, rw, false, false);
                }
            }
            if (killer != null) {
                plugin.stats().add(killer, "zonekills");
                for (String c : l.strings("kill-commands")) {
                    String cmd = c.replace("{player}", killer.getName()).replace("{victim}", victim.getName());
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
                }
            }
            if (l.bool("announce", true)) {
                int ar = l.integer("announce-radius", -1, -1, 100000);
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (ar >= 0 && (p.getWorld() != at.getWorld() || p.getLocation().distanceSquared(at) > (double) ar * ar)) continue;
                    plugin.lang().send(p, killer == null ? "deathzone.died" : "deathzone.kill", "killer", killer == null ? "" : killer.getName(),
                            "victim", victim.getName(), "tier", a.tier.name(plugin.lang().code(p)));
                }
            }
            return;
        }
    }
}
