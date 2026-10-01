package ru.mysticchest.economy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Multi-currency plugins without a stable Java API (ExcellentEconomy, CoinsEngine): the balance is read
 * through a PlaceholderAPI placeholder, charging/refunding runs console commands. Both templates come
 * from config.yml because command and placeholder names differ between versions.
 */
public final class CommandEconomyProvider implements EconomyProvider {
    private final String id, pluginName;
    private final ConfigurationSection cfg;
    private final Logger log;
    private final Set<String> warned = new HashSet<String>();
    private Method setPlaceholders;

    public CommandEconomyProvider(String id, String pluginName, ConfigurationSection cfg, Logger log) {
        this.id = id;
        this.pluginName = pluginName;
        this.cfg = cfg;
        this.log = log;
    }

    public String id() { return id; }

    public static boolean present(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public String describe() {
        Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
        if (p == null) return pluginName + ": not installed";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return pluginName + " v" + p.getDescription().getVersion() + " (PlaceholderAPI is required to read balances)";
        return pluginName + " v" + p.getDescription().getVersion();
    }

    private Method papi() {
        if (setPlaceholders != null) return setPlaceholders;
        try {
            setPlaceholders = Class.forName("me.clip.placeholderapi.PlaceholderAPI").getMethod("setPlaceholders", Player.class, String.class);
        } catch (Throwable t) {
            setPlaceholders = null;
        }
        return setPlaceholders;
    }

    public double balance(Player p, String currency) {
        Method m = papi();
        String tpl = cfg == null ? null : cfg.getString("balance-placeholder");
        if (m == null || tpl == null) return -1;
        String raw = null;
        try {
            raw = (String) m.invoke(null, p, tpl.replace("{currency}", currency));
            return Double.parseDouble(raw.replace(" ", "").replace(",", "."));
        } catch (Exception e) {
            if (warned.add(currency)) {
                log.warning("[" + pluginName + "] cannot read the balance of currency '" + currency + "': the placeholder returned '"
                        + raw + "'. Does the currency exist, and is economy." + id + ".balance-placeholder right for your version?");
            }
            return -1;
        }
    }

    private void run(String key, Player p, String currency, long amount) {
        String tpl = cfg == null ? null : cfg.getString(key);
        if (tpl == null) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), tpl.replace("{player}", p.getName())
                .replace("{currency}", currency).replace("{amount}", String.valueOf(amount)));
    }

    public boolean withdraw(Player p, String currency, long amount) {
        if (balance(p, currency) < amount) return false;
        run("take-command", p, currency, amount);
        return true;
    }

    public void deposit(Player p, String currency, long amount) { run("give-command", p, currency, amount); }
}
