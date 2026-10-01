package ru.mysticchest.chest;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LootEntry {
    public static final class Cmd {
        public final boolean asPlayer;
        public final String text;
        public final int chance;

        public Cmd(boolean asPlayer, String text, int chance) {
            this.asPlayer = asPlayer;
            this.text = text;
            this.chance = Math.max(1, Math.min(100, chance));
        }
    }

    public final String id;
    public ItemStack item;           // template; amount is taken from min/max
    public int min = 1, max = 1, weight = 10;
    public boolean giveItem = true, broadcast;
    public String permission = "";
    /** Max times one player can win this entry (0 = unlimited). */
    public int limitPerPlayer;
    public final List<Cmd> commands = new ArrayList<Cmd>();
    /** Marks entries below the "rare" threshold; recomputed whenever the pool is rebuilt. */
    public boolean rare;
    public double chance;

    public LootEntry(String id, ItemStack item) {
        this.id = id;
        this.item = item;
    }

    public ItemStack roll() {
        ItemStack it = item.clone();
        int amount = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        it.setAmount(Math.max(1, Math.min(amount, it.getMaxStackSize())));
        return it;
    }
}
