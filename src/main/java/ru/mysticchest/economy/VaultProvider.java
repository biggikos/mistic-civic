package ru.mysticchest.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultProvider implements EconomyProvider {
    private final Economy eco;

    private VaultProvider(Economy eco) { this.eco = eco; }

    /** @return provider or null when Vault/economy is missing. */
    public static VaultProvider create() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultProvider(rsp.getProvider());
    }

    public boolean has(Player p, String c, long a) { return eco.has(p, a); }
    public boolean withdraw(Player p, String c, long a) { return eco.withdrawPlayer(p, a).transactionSuccess(); }
    public void deposit(Player p, String c, long a) { eco.depositPlayer(p, a); }
    public String name() { return "VAULT"; }
}
