package ru.mysticchest.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/** The start-up logo in the console (no ads, just the plugin). */
public final class Banner {
    private Banner() {}

    private static final String[] LOGO = {
            " __  __ _     _   _        ____ _               _   ",
            "|  \\/  |_   _ ___| |_(_) ___ / ___| |__   ___  ___| |_ ",
            "| |\\/| | | | / __| __| |/ __| |   | '_ \\ / _ \\/ __| __|",
            "| |  | | |_| \\__ \\ |_| | (__| |___| | | |  __/\\__ \\ |_ ",
            "|_|  |_|\\__, |___/\\__|_|\\___|\\____|_| |_|\\___||___/\\__|",
            "        |___/                                          "
    };

    public static void print(String version) {
        try {
            org.bukkit.command.CommandSender c = Bukkit.getConsoleSender();
            for (String l : LOGO) c.sendMessage(ChatColor.LIGHT_PURPLE + l);
            c.sendMessage(ChatColor.GRAY + "  Mystic chests  " + ChatColor.DARK_GRAY + "v" + version + "  " + ChatColor.GRAY + "by Biggiko");
        } catch (Throwable ignored) {}
    }
}
