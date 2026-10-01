package ru.mysticchest.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault / VaultUnlocked. The Economy service is looked up on every call, so an economy plugin that
 * registers after us (or is reloaded) keeps working. Old Vault versions (1.5/1.6) only know the
 * String overloads - those are used as a fallback.
 */
public final class VaultProvider implements EconomyProvider {
    public String id() { return "vault"; }

    private static Economy eco() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public static boolean present() { return Bukkit.getPluginManager().getPlugin("Vault") != null; }

    public String describe() {
        Plugin v = Bukkit.getPluginManager().getPlugin("Vault");
        if (v == null) return "Vault: not installed";
        Economy e = eco();
        return "Vault v" + v.getDescription().getVersion() + (e == null ? " (no economy plugin registered)" : " -> " + e.getName());
    }

    public double balance(Player p, String currency) {
        Economy e = eco();
        if (e == null) return -1;
        try {
            return e.getBalance((OfflinePlayer) p);
        } catch (NoSuchMethodError old) {
            return e.getBalance(p.getName());
        }
    }

    @SuppressWarnings("deprecation")
    public boolean withdraw(Player p, String currency, long amount) {
        Economy e = eco();
        if (e == null) return false;
        try {
            return e.withdrawPlayer((OfflinePlayer) p, amount).transactionSuccess();
        } catch (NoSuchMethodError old) {
            return e.withdrawPlayer(p.getName(), amount).transactionSuccess();
        }
    }

    @SuppressWarnings("deprecation")
    public void deposit(Player p, String currency, long amount) {
        Economy e = eco();
        if (e == null) return;
        try {
            e.depositPlayer((OfflinePlayer) p, amount);
        } catch (NoSuchMethodError old) {
            e.depositPlayer(p.getName(), amount);
        }
    }
}
