package ru.mysticchest.util;

import org.bukkit.Particle;
import ru.mysticchest.MysticChestPlugin;

/** Small helpers for choosing particles that exist on every server version. */
public final class Particles {
    private Particles() {}

    /** Dust (REDSTONE on old versions), or a flame if neither exists. */
    public static Particle dustOrFallback(MysticChestPlugin plugin) {
        Particle p = plugin.effects().particle("DUST");
        return p != null ? p : plugin.effects().particle("FLAME");
    }
}
