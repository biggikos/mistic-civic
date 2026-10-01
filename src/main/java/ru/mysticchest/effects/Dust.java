package ru.mysticchest.effects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Coloured dust particles (1.13+). Only loaded after Effects verified that Particle.DustOptions exists. */
final class Dust {
    private Dust() {}

    static void spawn(Player p, Particle pt, Location l, int count, Color c) {
        p.spawnParticle(pt, l, count, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(c, 1.2f));
    }
}
