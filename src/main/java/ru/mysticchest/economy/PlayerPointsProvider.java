package ru.mysticchest.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/** PlayerPoints through reflection on its public API (look/take/give), so no compile-time dependency. */
public final class PlayerPointsProvider implements EconomyProvider {
    public String id() { return "playerpoints"; }

    public static boolean present() { return Bukkit.getPluginManager().getPlugin("PlayerPoints") != null; }

    public String describe() {
        Plugin p = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        return p == null ? "PlayerPoints: not installed" : "PlayerPoints v" + p.getDescription().getVersion();
    }

    private Object api() throws Exception {
        Plugin p = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (p == null) return null;
        return p.getClass().getMethod("getAPI").invoke(p);
    }

    private static Method m(Object api, String name, Class<?>... types) throws Exception {
        return api.getClass().getMethod(name, types);
    }

    public double balance(Player p, String currency) {
        try {
            Object api = api();
            if (api == null) return -1;
            return ((Number) m(api, "look", UUID.class).invoke(api, p.getUniqueId())).doubleValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    public boolean withdraw(Player p, String currency, long amount) {
        try {
            Object api = api();
            if (api == null || amount > Integer.MAX_VALUE) return false;
            Object r = m(api, "take", UUID.class, int.class).invoke(api, p.getUniqueId(), (int) amount);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    public void deposit(Player p, String currency, long amount) {
        try {
            Object api = api();
            if (api != null && amount <= Integer.MAX_VALUE) m(api, "give", UUID.class, int.class).invoke(api, p.getUniqueId(), (int) amount);
        } catch (Throwable ignored) {}
    }
}
