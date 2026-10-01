package ru.mysticchest.loot;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.util.ItemCodec;

/** Shared by /mystic loot add and the editor's "add inventory" button. */
public final class LootTools {
    private LootTools() {}

    /**
     * Moves the player's inventory (or only the held item) into the reward pool of a tier.
     * @return number of stacks added
     */
    @SuppressWarnings("deprecation")
    public static int addFromInventory(MysticChestPlugin plugin, Player p, Tier t, int weight, boolean keep, boolean handOnly) {
        int added = 0;
        boolean merge = plugin.settings().mergeIdentical;
        if (handOnly) {
            ItemStack hand = p.getInventory().getItemInHand();
            if (!ItemCodec.isAir(hand)) {
                plugin.loot().addItem(t, hand, weight, merge);
                if (!keep) p.getInventory().setItemInHand(null);
                added++;
            }
        } else {
            ItemStack[] items = p.getInventory().getStorageContents();   // main inventory + hotbar, no armor / offhand
            for (int i = 0; i < items.length; i++) {
                ItemStack it = items[i];
                if (ItemCodec.isAir(it)) continue;
                plugin.loot().addItem(t, it, weight, merge);
                if (!keep) items[i] = null;
                added++;
            }
            if (!keep) p.getInventory().setStorageContents(items);
        }
        if (added > 0) plugin.loot().commit(t);
        return added;
    }

    public static int addOne(MysticChestPlugin plugin, Player p, Tier t, ItemStack stack, int weight) {
        plugin.loot().addItem(t, stack, weight, plugin.settings().mergeIdentical);
        plugin.loot().commit(t);
        return 1;
    }
}
