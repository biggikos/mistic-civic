package ru.mysticchest.economy;

import org.bukkit.entity.Player;

public interface EconomyProvider {
    /** Short id used in tier currencies ("vault", "excellenteconomy", "coinsengine", "playerpoints"). */
    String id();
    /** Human readable "Name vX.Y" of the backing plugin, for diagnostics. */
    String describe();
    /** Balance, or a negative number when it cannot be read (unknown currency, plugin error). */
    double balance(Player p, String currency);
    boolean withdraw(Player p, String currency, long amount);
    void deposit(Player p, String currency, long amount);
}
