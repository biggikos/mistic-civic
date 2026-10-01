package ru.mysticchest.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Tier;

/**
 * %mysticchest_cooldown_<tier>%            seconds left (0 = ready)
 * %mysticchest_cooldown_formatted_<tier>%  "1h 5m 3s" or "Ready"
 * %mysticchest_opens_today%                chests the player opened today
 * %mysticchest_pity_<tier>%                openings since the last rare reward
 * %mysticchest_active_chests%              chests standing in the world
 * Loaded only when PlaceholderAPI is installed.
 */
public final class PapiHook extends PlaceholderExpansion {
    private final MysticChestPlugin plugin;

    public PapiHook(MysticChestPlugin plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "mysticchest"; }
    @Override public String getAuthor() { return "Biggiko"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer off, String params) {
        if (params.equals("active_chests")) return String.valueOf(plugin.chests().count());
        if (off == null) return "";
        if (params.equals("opens_today")) return String.valueOf(plugin.cooldowns().opensToday(off.getUniqueId()));
        if (params.startsWith("pity_")) {
            Tier t = plugin.tiers().get(params.substring(5));
            return t == null ? "" : String.valueOf(plugin.cooldowns().pity(off.getUniqueId(), t.id));
        }
        boolean fmt = params.startsWith("cooldown_formatted_");
        if (fmt || params.startsWith("cooldown_")) {
            Tier t = plugin.tiers().get(params.substring(fmt ? 19 : 9));
            Player p = off.getPlayer();
            if (t == null || p == null) return "";
            long left = plugin.open().cooldownLeft(p, t);
            if (!fmt) return String.valueOf(left);
            return left <= 0 ? plugin.lang().get(p, "papi.ready") : plugin.lang().time(p, left);
        }
        return null;
    }
}
