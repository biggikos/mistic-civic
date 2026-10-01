package ru.mysticchest.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.List;

public final class ShopGui implements GuiHolder {
    private final MysticChestPlugin plugin;
    private final List<Tier> slots = new ArrayList<Tier>();
    private final Inventory inv;

    private ShopGui(MysticChestPlugin plugin, Player p) {
        this.plugin = plugin;
        for (Tier t : plugin.tiers().all()) if (t.purchasable) slots.add(t);
        int size = Math.max(9, ((slots.size() + 8) / 9) * 9);
        this.inv = Bukkit.createInventory(this, Math.min(54, size), Text.title(plugin.lang().get(p, "gui.shop.title")));
    }

    public static void open(MysticChestPlugin plugin, Player p) {
        ShopGui g = new ShopGui(plugin, p);
        String key = "shop:" + plugin.lang().code(p);
        ItemStack[][] cached = plugin.guiCache().get(key);
        if (cached == null) {
            ItemStack[] items = new ItemStack[g.slots.size()];
            for (int i = 0; i < items.length; i++) items[i] = g.build(g.slots.get(i), p);
            cached = new ItemStack[][]{items};
            plugin.guiCache().put(key, cached);
        }
        ItemStack[] items = GuiCache.copy(cached[0]);
        for (int i = 0; i < items.length && i < g.inv.getSize(); i++) g.inv.setItem(i, items[i]);
        p.openInventory(g.inv);
    }

    private ItemStack build(Tier t, Player p) {
        ItemStack it = plugin.chests().icon(t);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(t.name(plugin.lang().code(p)));
        List<String> lore = new ArrayList<String>();
        lore.add(plugin.lang().get(p, "gui.shop.price", "price", Text.format(t.price), "currency", t.currency));
        int cd = t.cooldownBuy(plugin.settings());
        if (cd > 0) lore.add(plugin.lang().get(p, "gui.shop.cooldown", "time", plugin.lang().time(p, cd)));
        lore.addAll(plugin.lang().list(p, plugin.settings().previewEnabled ? "gui.shop.lore-preview" : "gui.shop.lore"));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return false; }
    public void onClose(InventoryCloseEvent e) {}

    public void onClick(InventoryClickEvent e) {
        if (!GuiHolder.top(e) || !(e.getWhoClicked() instanceof Player)) return;
        int slot = e.getSlot();
        if (slot < 0 || slot >= slots.size()) return;
        Player p = (Player) e.getWhoClicked();
        Tier t = slots.get(slot);
        if (e.isRightClick() && plugin.settings().previewEnabled) {
            PreviewGui.open(plugin, p, t, 0, true);
        } else {
            p.closeInventory();
            plugin.buy(p, t);
        }
    }
}
