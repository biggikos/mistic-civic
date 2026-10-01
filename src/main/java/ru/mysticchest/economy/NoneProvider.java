package ru.mysticchest.economy;

import org.bukkit.entity.Player;

public final class NoneProvider implements EconomyProvider {
    public boolean has(Player p, String c, long a) { return false; }
    public boolean withdraw(Player p, String c, long a) { return false; }
    public void deposit(Player p, String c, long a) {}
    public String name() { return "NONE"; }
}
