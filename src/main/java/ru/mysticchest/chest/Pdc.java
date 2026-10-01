package ru.mysticchest.chest;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer tag (1.14+). Only touched after ChestManager confirmed the API exists. */
final class Pdc {
    private static NamespacedKey key;

    private Pdc() {}

    static void init(Plugin p) { key = new NamespacedKey(p, "tier"); }

    static void set(ItemMeta m, String tier) { m.getPersistentDataContainer().set(key, PersistentDataType.STRING, tier); }

    static String get(ItemMeta m) { return m.getPersistentDataContainer().get(key, PersistentDataType.STRING); }
}
