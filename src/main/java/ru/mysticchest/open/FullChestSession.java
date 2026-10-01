package ru.mysticchest.open;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Reward;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.gui.GuiHolder;
import ru.mysticchest.util.ItemCodec;
import ru.mysticchest.util.Text;

import java.util.List;

/** "A full chest of loot": the player takes what they want, the rest is returned on close. */
final class FullChestSession implements GuiHolder {
    private final MysticChestPlugin plugin;
    private final Player p;
    private final Tier t;
    private final List<Reward> rewards;
    private final Inventory inv;

    FullChestSession(MysticChestPlugin plugin, Player p, Tier t, List<Reward> rewards) {
        this.plugin = plugin;
        this.p = p;
        this.t = t;
        this.rewards = rewards;
        int rows = plugin.settings().fullChestRows;
        this.inv = Bukkit.createInventory(this, rows * 9,
                Text.title(plugin.lang().get(p, "gui.chest.title", "tier", t.name(plugin.lang().code(p)))));
    }

    void start() {
        for (Reward r : rewards) {
            if (r.entry.giveItem) {
                for (ItemStack left : inv.addItem(r.stack.clone()).values()) plugin.rewards().give(p, left);
            }
            plugin.rewards().apply(p, t, r, false, false);   // commands + announcements, item stays in the GUI
        }
        plugin.effects().playTier(plugin.settings().fxWin, p, t, "player", p.getName(), "item", "");
        p.openInventory(inv);
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return true; }
    public void onClick(InventoryClickEvent e) {}

    public void onClose(InventoryCloseEvent e) {
        for (ItemStack it : inv.getContents()) {
            if (!ItemCodec.isAir(it)) plugin.rewards().give(p, it);
        }
        inv.clear();
    }
}
