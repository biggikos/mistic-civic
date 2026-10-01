package ru.mysticchest.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.mysticchest.MysticChestPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the provider for a tier currency. {@code economy.provider} sets the default
 * (AUTO = first installed of ExcellentEconomy, CoinsEngine, Vault, PlayerPoints); a tier can name another
 * one with "provider:currency", so gold may come from Vault and tokens from PlayerPoints.
 */
public final class Economies {
    public static final class Resolved {
        public final EconomyProvider provider;
        public final String currency, display;
        Resolved(EconomyProvider p, String c, String d) { provider = p; currency = c; display = d; }
    }

    private static final String[] AUTO_ORDER = {"excellenteconomy", "coinsengine", "vault", "playerpoints"};

    private final MysticChestPlugin plugin;
    private String defaultId = "auto";
    private ConfigurationSection cfg;

    public Economies(MysticChestPlugin plugin) { this.plugin = plugin; }

    public void reload(ConfigurationSection economy) {
        cfg = economy;
        String p = economy == null ? "AUTO" : economy.getString("provider", "AUTO");
        p = p.trim();
        String c = p.equalsIgnoreCase("AUTO") || p.equalsIgnoreCase("NONE") ? p.toLowerCase() : CurrencySpec.canonical(p);
        if (c == null) {
            plugin.getLogger().warning("economy.provider '" + p + "' is unknown (use AUTO, VAULT, EXCELLENT_ECONOMY, COINS_ENGINE, PLAYER_POINTS or NONE) -> AUTO");
            c = "auto";
        }
        defaultId = c;
        for (String line : describe()) plugin.getLogger().info("Economy: " + line);
    }

    private EconomyProvider create(String id) {
        if (id.equals("vault")) return VaultProvider.present() ? new VaultProvider() : null;
        if (id.equals("playerpoints")) return PlayerPointsProvider.present() ? new PlayerPointsProvider() : null;
        ConfigurationSection sec = cfg == null ? null : cfg.getConfigurationSection(id.equals("excellenteconomy") ? "excellent-economy" : "coins-engine");
        String plug = id.equals("excellenteconomy") ? "ExcellentEconomy" : "CoinsEngine";
        return CommandEconomyProvider.present(plug) ? new CommandEconomyProvider(id, plug, sec, plugin.getLogger()) : null;
    }

    /** @return the provider for this currency spec, or null when none is available. */
    public Resolved resolve(String rawCurrency) {
        CurrencySpec spec = CurrencySpec.parse(rawCurrency);
        String id = spec.provider != null ? spec.provider : defaultId;
        if (id.equals("none")) return null;
        EconomyProvider p = null;
        if (id.equals("auto")) {
            for (String a : AUTO_ORDER) { p = create(a); if (p != null) break; }
        } else {
            p = create(id);
        }
        return p == null ? null : new Resolved(p, spec.currency, spec.display);
    }

    public boolean available() { return !defaultId.equals("none"); }

    public String defaultId() { return defaultId; }

    /** Detected economy plugins + what the default resolves to; shown at startup and by /mystic economy. */
    public List<String> describe() {
        List<String> out = new ArrayList<String>();
        out.add("default provider: " + defaultId.toUpperCase());
        for (String id : CurrencySpec.PROVIDERS) {
            EconomyProvider p = create(id);
            out.add((p != null ? "[ok] " : "[--] ") + (p != null ? p.describe() : missing(id)));
        }
        return out;
    }

    private static String missing(String id) {
        if (id.equals("vault")) return VaultProvider.present() ? "Vault present" : "Vault: not installed";
        if (id.equals("playerpoints")) return "PlayerPoints: not installed";
        return (id.equals("excellenteconomy") ? "ExcellentEconomy" : "CoinsEngine") + ": not installed (or PlaceholderAPI missing)";
    }

    public boolean charge(Player p, String rawCurrency, long amount) {
        Resolved r = resolve(rawCurrency);
        if (r == null) return false;
        return r.provider.withdraw(p, r.currency, amount);
    }
}
