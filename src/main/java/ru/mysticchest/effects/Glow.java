package ru.mysticchest.effects;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Coloured glowing outline (Entity#setGlowing + a scoreboard team that carries the colour). A player's own
 * team is remembered and put back when the glow ends. Everything is best effort: on versions without the
 * API the calls do nothing.
 */
public final class Glow {
    private static final Map<String, String> PREVIOUS = new HashMap<String, String>();   // entry -> team it had before ("" none)

    private Glow() {}

    private static String entry(Entity e) { return e instanceof Player ? e.getName() : e.getUniqueId().toString(); }

    /** @param color a ChatColor name (RED, GOLD...), null/empty/unknown = plain white glow. */
    public static void on(Entity e, String color) {
        try {
            e.setGlowing(true);
            ChatColor c = parse(color);
            if (c == null) return;
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            String name = "mcg_" + c.name();
            Team t = sb.getTeam(name);
            if (t == null) { t = sb.registerNewTeam(name); t.setColor(c); }
            String entry = entry(e);
            Team old = sb.getEntryTeam(entry);
            if (old != null && old.equals(t)) return;
            if (!PREVIOUS.containsKey(entry)) PREVIOUS.put(entry, old == null ? "" : old.getName());
            t.addEntry(entry);
        } catch (Throwable ignored) {}
    }

    public static void off(Entity e) {
        try { e.setGlowing(false); } catch (Throwable ignored) {}
        release(entry(e));
    }

    /** For entities that are already gone (dead guards): only the team bookkeeping. */
    public static void off(UUID id) { release(id.toString()); }

    private static void release(String entry) {
        String prev = PREVIOUS.remove(entry);
        if (prev == null) return;
        try {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team cur = sb.getEntryTeam(entry);
            if (cur != null && cur.getName().startsWith("mcg_")) cur.removeEntry(entry);
            if (!prev.isEmpty()) { Team p = sb.getTeam(prev); if (p != null) p.addEntry(entry); }
        } catch (Throwable ignored) {}
    }

    public static void clearAll() {
        for (String e : new ArrayList<String>(PREVIOUS.keySet())) release(e);
    }

    private static ChatColor parse(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            ChatColor c = ChatColor.valueOf(s.trim().toUpperCase(Locale.ROOT));
            return c.isColor() ? c : null;
        } catch (IllegalArgumentException ex) { return null; }
    }
}
