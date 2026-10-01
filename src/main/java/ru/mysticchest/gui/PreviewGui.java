package ru.mysticchest.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.LootEntry;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only loot list with chances. Pages are cached per tier+language until the loot changes. */
public final class PreviewGui implements GuiHolder {
    private static final int PER_PAGE = 45;
    private final MysticChestPlugin plugin;
    private final Tier tier;
    private final boolean fromShop;
    private final Inventory inv;
    private final ItemStack[][] pages;
    private int page;

    private PreviewGui(MysticChestPlugin plugin, Player p, Tier t, ItemStack[][] pages, int page, boolean fromShop) {
        this.plugin = plugin;
        this.tier = t;
        this.pages = pages;
        this.page = Math.max(0, Math.min(page, pages.length - 1));
        this.fromShop = fromShop;
        this.inv = Bukkit.createInventory(this, 54,
                Text.title(plugin.lang().get(p, "gui.preview.title", "tier", t.name(plugin.lang().code(p)))));
    }

    public static void open(MysticChestPlugin plugin, Player p, Tier t, int page, boolean fromShop) {
        String key = "preview:" + t.id + ":" + plugin.lang().code(p);
        ItemStack[][] pages = plugin.guiCache().get(key);
        if (pages == null) {
            pages = build(plugin, p, t);
            plugin.guiCache().put(key, pages);
        }
        PreviewGui g = new PreviewGui(plugin, p, t, pages, page, fromShop);
        g.render(p);
        p.openInventory(g.inv);
    }

    private static ItemStack[][] build(MysticChestPlugin plugin, Player p, Tier t) {
        List<LootEntry> list = t.pool.all.all();
        int n = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        ItemStack[][] pages = new ItemStack[n][PER_PAGE];
        for (int i = 0; i < list.size(); i++) {
            LootEntry e = list.get(i);
            ItemStack it = e.item.clone();
            it.setAmount(Math.max(1, Math.min(e.max, it.getMaxStackSize())));
            ItemMeta m = it.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
            lore.add("");
            if (plugin.settings().showChances) {
                lore.add(plugin.lang().get(p, "gui.preview.chance", "chance", String.format(Locale.ROOT, "%.2f", e.chance)));
            }
            if (e.min != e.max) lore.add(plugin.lang().get(p, "gui.preview.amount", "min", String.valueOf(e.min), "max", String.valueOf(e.max)));
            if (e.rare) lore.add(plugin.lang().get(p, "gui.preview.rare"));
            m.setLore(lore);
            it.setItemMeta(m);
            pages[i / PER_PAGE][i % PER_PAGE] = it;
        }
        return pages;
    }

    private ItemStack nav(Player p, String key, String mat) {
        ItemStack it = com.cryptomorin.xseries.XMaterial.matchXMaterial(mat).get().parseItem();
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(plugin.lang().get(p, key));
        it.setItemMeta(m);
        return it;
    }

    private void render(Player p) {
        inv.clear();
        ItemStack[] src = GuiCache.copy(pages[page]);
        for (int i = 0; i < src.length; i++) inv.setItem(i, src[i]);
        if (page > 0) inv.setItem(45, nav(p, "gui.prev", "ARROW"));
        if (page < pages.length - 1) inv.setItem(53, nav(p, "gui.next", "ARROW"));
        if (fromShop) inv.setItem(49, nav(p, "gui.back", "BARRIER"));
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return false; }
    public void onClose(InventoryCloseEvent e) {}

    public void onClick(InventoryClickEvent e) {
        if (!GuiHolder.top(e) || !(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        int s = e.getSlot();
        if (s == 45 && page > 0) { page--; render(p); }
        else if (s == 53 && page < pages.length - 1) { page++; render(p); }
        else if (s == 49 && fromShop) ShopGui.open(plugin, p);
    }
}
