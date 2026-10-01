package ru.mysticchest.chest;

import org.bukkit.inventory.ItemStack;

/** One rolled prize: the entry plus the concrete stack (amount already rolled). */
public final class Reward {
    public final LootEntry entry;
    public final ItemStack stack;
    public boolean applied;

    public Reward(LootEntry entry, ItemStack stack) {
        this.entry = entry;
        this.stack = stack;
    }
}
