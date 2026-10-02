package ru.mysticchest.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.util.ItemCodec;
import ru.mysticchest.util.Text;

/**
 * "Put it in the chest": an empty 54-slot window, you drop everything that should become a reward, close it, and
 * every stack goes into the tier's pool. With {@code keep} the items come back to your inventory afterwards.
 */
public final class LootFillGui implements GuiHolder {
    private final MysticChestPlugin plugin;
    private final Tier tier;
    private final int weight;
    private final boolean keep;
    private final Inventory inv;
    private boolean done;

    private LootFillGui(MysticChestPlugin plugin, Player p, Tier tier, int weight, boolean keep) {
        this.plugin = plugin; this.tier = tier; this.weight = weight; this.keep = keep;
        this.inv = Bukkit.createInventory(this, 54, Text.title(plugin.lang().get(p, "gui.fill.title", "tier", tier.name(plugin.lang().code(p)))));
    }

    public static void open(MysticChestPlugin plugin, Player p, Tier tier, int weight, boolean keep) {
        LootFillGui g = new LootFillGui(plugin, p, tier, weight, keep);
        p.openInventory(g.inv);
        plugin.lang().send(p, "loot.fill-hint", "weight", String.valueOf(weight));
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return true; }
    public void onClick(InventoryClickEvent e) {}

    public void onClose(InventoryCloseEvent e) {
        if (done || !(e.getPlayer() instanceof Player)) return;
        done = true;
        Player p = (Player) e.getPlayer();
        if (!p.hasPermission("mysticchest.admin.loot")) { give(p); return; }
        int n = 0;
        boolean merge = plugin.settings().mergeIdentical;
        for (ItemStack it : inv.getContents()) {
            if (ItemCodec.isAir(it)) continue;
            plugin.loot().addItem(tier, it, weight, merge);
            n++;
        }
        if (n > 0) plugin.loot().commit(tier);
        if (keep) give(p);
        plugin.lang().send(p, n == 0 ? "loot.nothing" : "loot.added", "count", String.valueOf(n), "tier", tier.name(plugin.lang().code(p)), "weight", String.valueOf(weight));
    }

    private void give(Player p) {
        for (ItemStack it : inv.getContents()) if (!ItemCodec.isAir(it)) plugin.rewards().give(p, it.clone());
    }
}
