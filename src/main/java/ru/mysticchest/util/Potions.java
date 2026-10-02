package ru.mysticchest.util;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/** Potion effects by name, tolerant of the old/new naming (SLOW vs SLOWNESS ...). Spec: NAME:seconds[:level]. */
public final class Potions {
    private static final String[][] ALIASES = {
            {"SLOWNESS", "SLOW"}, {"NAUSEA", "CONFUSION"}, {"MINING_FATIGUE", "SLOW_DIGGING"}, {"RESISTANCE", "DAMAGE_RESISTANCE"},
            {"STRENGTH", "INCREASE_DAMAGE"}, {"JUMP_BOOST", "JUMP"}, {"INSTANT_DAMAGE", "HARM"}, {"INSTANT_HEALTH", "HEAL"}, {"HASTE", "FAST_DIGGING"}};

    private Potions() {}

    public static PotionEffectType type(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        PotionEffectType t = PotionEffectType.getByName(n);
        if (t != null) return t;
        for (String[] a : ALIASES) {
            if (a[0].equals(n)) t = PotionEffectType.getByName(a[1]);
            else if (a[1].equals(n)) t = PotionEffectType.getByName(a[0]);
            if (t != null) return t;
        }
        return null;
    }

    /** @return the effect, or null when the name is unknown on this server version. */
    public static PotionEffect parse(String spec, int defaultSeconds) {
        String[] a = spec.split(":");
        try {
            PotionEffectType t = type(a[0]);
            if (t == null) return null;
            int sec = a.length > 1 ? Integer.parseInt(a[1].trim()) : defaultSeconds, amp = a.length > 2 ? Integer.parseInt(a[2].trim()) : 0;
            return new PotionEffect(t, sec * 20, amp, true, false);
        } catch (Exception e) {
            return null;
        }
    }
}
