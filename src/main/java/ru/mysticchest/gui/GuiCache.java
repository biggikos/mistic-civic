package ru.mysticchest.gui;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Ready-made GUI items keyed by "kind:language"; dropped on reload or loot changes. */
public final class GuiCache {
    private final Map<String, ItemStack[][]> cache = new HashMap<String, ItemStack[][]>();
    private boolean enabled = true;

    public void enabled(boolean e) { enabled = e; if (!e) cache.clear(); }
    public void clear() { cache.clear(); }
    public int size() { return cache.size(); }

    public ItemStack[][] get(String key) { return enabled ? cache.get(key) : null; }
    public void put(String key, ItemStack[][] pages) { if (enabled) cache.put(key, pages); }

    public static ItemStack[] copy(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) out[i] = src[i] == null ? null : src[i].clone();
        return out;
    }
}
