package ru.mysticchest.economy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Multi-currency mode (gold / tokens). Balance is read through PlaceholderAPI,
 * withdraw/refund run as console commands - both templates are configurable
 * because ExcellentEconomy command/placeholder names differ between versions.
 */
public final class ExcellentEconomyProvider implements EconomyProvider {
    private final String balancePlaceholder, takeCmd, giveCmd;
    private final Method setPlaceholders;

    private ExcellentEconomyProvider(ConfigurationSection c, Method m) {
        this.balancePlaceholder = c.getString("balance-placeholder");
        this.takeCmd = c.getString("take-command");
        this.giveCmd = c.getString("give-command");
        this.setPlaceholders = m;
    }

    public static ExcellentEconomyProvider create(ConfigurationSection c) {
        if (Bukkit.getPluginManager().getPlugin("ExcellentEconomy") == null) return null;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return null;
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return new ExcellentEconomyProvider(c, papi.getMethod("setPlaceholders", Player.class, String.class));
        } catch (Exception e) {
            return null;
        }
    }

    private double balance(Player p, String currency) {
        try {
            String raw = (String) setPlaceholders.invoke(null, p, balancePlaceholder.replace("{currency}", currency));
            return Double.parseDouble(raw.replace(" ", "").replace(",", "."));
        } catch (Exception e) {
            return -1;
        }
    }

    private void run(String tpl, Player p, String currency, long amount) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), tpl.replace("{player}", p.getName())
                .replace("{currency}", currency).replace("{amount}", String.valueOf(amount)));
    }

    public boolean has(Player p, String currency, long amount) { return balance(p, currency) >= amount; }

    public boolean withdraw(Player p, String currency, long amount) {
        if (!has(p, currency, amount)) return false;
        run(takeCmd, p, currency, amount);
        return true;
    }

    public void deposit(Player p, String currency, long amount) { run(giveCmd, p, currency, amount); }
    public String name() { return "EXCELLENT_ECONOMY"; }
}
