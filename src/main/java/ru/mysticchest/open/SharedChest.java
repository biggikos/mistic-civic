package ru.mysticchest.open;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.ChestManager;
import ru.mysticchest.chest.Reward;
import ru.mysticchest.util.ItemCodec;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SHARED mode (FunTime style): the standing chest becomes a real chest with the rolled loot inside. It stays in
 * the world, anybody can open it and take what is left (everyone sees the same inventory live). It disappears
 * when it is empty or its time is up.
 */
final class SharedChest implements ru.mysticchest.gui.GuiHolder {
    private final MysticChestPlugin plugin;
    final ChestManager.Active chest;
    final Inventory inv;
    final Set<UUID> opened = new HashSet<UUID>();

    SharedChest(MysticChestPlugin plugin, ChestManager.Active chest, List<Reward> rewards, Player first) {
        this.plugin = plugin;
        this.chest = chest;
        int items = 0;
        for (Reward r : rewards) if (r.entry.giveItem) items++;
        int rows = Math.max(3, Math.min(6, (items + 8) / 9));
        this.inv = Bukkit.createInventory(this, rows * 9, Text.title(plugin.lang().get(first, "gui.chest.title", "tier", chest.tier.name(plugin.lang().code(first)))));
        java.util.Random rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (Reward r : rewards) {
            if (r.entry.giveItem) {
                // scattered over the chest like a real loot chest, not packed into the first slots
                int slot = -1;
                for (int tries = 0; tries < 20 && slot < 0; tries++) { int s = rnd.nextInt(inv.getSize()); if (ItemCodec.isAir(inv.getItem(s))) slot = s; }
                if (slot >= 0) inv.setItem(slot, r.stack.clone());
                else for (ItemStack left : inv.addItem(r.stack.clone()).values()) plugin.rewards().give(first, left);
            }
            plugin.rewards().apply(first, chest.tier, r, false, false);   // commands + rare announcements go to the first opener
        }
    }

    void show(Player p) { p.openInventory(inv); }

    boolean empty() {
        for (ItemStack it : inv.getContents()) if (!ItemCodec.isAir(it)) return false;
        return true;
    }

    /** Closes the window for everyone still looking (the chest is gone). */
    void closeAll() {
        for (HumanEntity h : new ArrayList<HumanEntity>(inv.getViewers())) h.closeInventory();
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return true; }
    public void onClick(InventoryClickEvent e) {}

    public void onClose(InventoryCloseEvent e) {
        if (!empty()) return;
        // the last item was taken: the chest is done (structure collapses, guards go)
        plugin.open().forgetShared(chest);
        if (plugin.chests().remove(chest, true)) plugin.effects().playAt(plugin.settings().fxExpire, chest.loc);
    }
}
