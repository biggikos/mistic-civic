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
        // %mysticchest_top_<stat>_<rank>_name% / _value  (current period)   %mysticchest_toptotal_...  (all time)
        if (params.startsWith("top_") || params.startsWith("toptotal_")) {
            boolean all = params.startsWith("toptotal_");
            String[] p = params.substring(all ? 9 : 4).split("_");
            if (p.length == 3) {
                try {
                    java.util.List<ru.mysticchest.stats.StatsService.Row> rows = plugin.stats().top(p[0], all, Integer.parseInt(p[1]));
                    int idx = Integer.parseInt(p[1]) - 1;
                    if (idx < 0 || idx >= rows.size()) return p[2].equals("name") ? "-" : "0";
                    return p[2].equals("name") ? rows.get(idx).name : String.valueOf(rows.get(idx).value);
                } catch (NumberFormatException e) { return null; }
            }
        }
        if (off != null && (params.startsWith("stat_") || params.startsWith("stattotal_"))) {
            boolean all = params.startsWith("stattotal_");
            return String.valueOf(plugin.stats().get(off.getUniqueId(), params.substring(all ? 10 : 5), all));
        }
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
