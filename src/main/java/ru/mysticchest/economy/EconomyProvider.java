package ru.mysticchest.economy;

import org.bukkit.entity.Player;

public interface EconomyProvider {
    boolean has(Player p, String currency, long amount);
    boolean withdraw(Player p, String currency, long amount);
    /** Used when a purchase fails after payment (refund). */
    void deposit(Player p, String currency, long amount);
    String name();
}
