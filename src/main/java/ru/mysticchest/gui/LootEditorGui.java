package ru.mysticchest.gui;

import com.cryptomorin.xseries.XMaterial;
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
import ru.mysticchest.loot.LootTools;
import ru.mysticchest.util.ItemCodec;
import ru.mysticchest.util.Items;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full loot editor. Drop an item on the window to add it; clicks edit weight, commands and flags.
 * Built on demand, nothing is cached, nothing is kept after the window closes.
 */
public final class LootEditorGui implements GuiHolder {
    private static final int PER_PAGE = 45;
    /** Number keys 3-7 in the editor set the weight to: common, uncommon, rare, epic, legendary. */
    private static final int[] RARITY = {100, 40, 12, 4, 1};
    private final MysticChestPlugin plugin;
    private final Player p;
    private final Tier tier;
    private final Inventory inv;
    private int page;
    private int armedDelete = -1;
    private long armedAt;

    private LootEditorGui(MysticChestPlugin plugin, Player p, Tier tier, int page) {
        this.plugin = plugin;
        this.p = p;
        this.tier = tier;
        this.page = page;
        this.inv = Bukkit.createInventory(this, 54,
                Text.title(plugin.lang().get(p, "gui.editor.title", "tier", tier.name(plugin.lang().code(p)))));
    }

    public static void open(MysticChestPlugin plugin, Player p, Tier tier, int page) {
        LootEditorGui g = new LootEditorGui(plugin, p, tier, page);
        g.render();
        p.openInventory(g.inv);
    }

    private List<LootEntry> entries() { return plugin.loot().entries(tier); }

    private int pages() { return Math.max(1, (entries().size() + PER_PAGE - 1) / PER_PAGE); }

    private ItemStack button(String mat, String nameKey, String loreKey, String... kv) {
        ItemStack it = XMaterial.matchXMaterial(mat).get().parseItem();
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(plugin.lang().get(p, nameKey, kv));
        if (loreKey != null) m.setLore(plugin.lang().list(p, loreKey, kv));
        it.setItemMeta(m);
        return it;
    }

    private void render() {
        inv.clear();
        List<LootEntry> list = entries();
        page = Math.max(0, Math.min(page, pages() - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) break;
            inv.setItem(i, icon(list.get(idx), idx));
        }
        ItemStack fill = XMaterial.matchXMaterial(plugin.settings().filler).orElse(XMaterial.GRAY_STAINED_GLASS_PANE).parseItem();
        ItemMeta fm = fill.getItemMeta();
        fm.setDisplayName(" ");
        fill.setItemMeta(fm);
        for (int i = PER_PAGE; i < 54; i++) inv.setItem(i, fill.clone());
        if (page > 0) inv.setItem(45, button("ARROW", "gui.prev", null));
        inv.setItem(46, button("CHEST", "gui.editor.add-inventory", "gui.editor.add-inventory-lore"));
        inv.setItem(47, button("HOPPER", "gui.editor.add-hand", "gui.editor.add-hand-lore"));
        inv.setItem(49, button("BOOK", "gui.editor.info", "gui.editor.info-lore",
                "entries", String.valueOf(list.size()), "page", String.valueOf(page + 1), "pages", String.valueOf(pages())));
        inv.setItem(51, button("COMPASS", "gui.editor.next-tier", null));
        inv.setItem(52, button("BARRIER", "gui.close", null));
        if (page < pages() - 1) inv.setItem(53, button("ARROW", "gui.next", null));
    }

    private ItemStack icon(LootEntry e, int idx) {
        ItemStack it = e.item.clone();
        it.setAmount(Math.max(1, Math.min(e.max, it.getMaxStackSize())));
        ItemMeta m = it.getItemMeta();
        List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
        lore.add("");
        lore.add(plugin.lang().get(p, "gui.editor.line.weight", "weight", String.valueOf(e.weight),
                "chance", String.format(Locale.ROOT, "%.2f", e.chance)));
        lore.add(plugin.lang().get(p, "gui.editor.line.amount",
                "amount", e.min == e.max ? String.valueOf(e.min) : e.min + "-" + e.max));
        lore.add(plugin.lang().get(p, e.giveItem ? "gui.editor.line.gives" : "gui.editor.line.no-item"));
        if (e.broadcast) lore.add(plugin.lang().get(p, "gui.editor.line.broadcast"));
        if (e.rare) lore.add(plugin.lang().get(p, "gui.preview.rare"));
        if (!e.commands.isEmpty()) {
            lore.add(plugin.lang().get(p, "gui.editor.line.commands"));
            int n = 1;
            for (LootEntry.Cmd c : e.commands) {
                lore.add(plugin.lang().get(p, "gui.editor.line.cmd", "n", String.valueOf(n++),
                        "run", c.asPlayer ? "P" : "C", "chance", String.valueOf(c.chance), "command", c.text));
            }
        }
        lore.add("");
        for (String l : plugin.lang().list(p, idx == armedDelete ? "gui.editor.help-armed" : "gui.editor.help", "n", String.valueOf(idx + 1))) {
            lore.add(l);
        }
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return false; }
    public boolean allowBottom() { return true; }
    public void onClose(InventoryCloseEvent e) {}

    public void onClick(InventoryClickEvent e) {
        if (!p.hasPermission("mysticchest.admin.loot")) { p.closeInventory(); return; }
        // 1) an item on the cursor dropped onto the window = add it to the pool
        ItemStack cursor = e.getCursor();
        if (GuiHolder.top(e) && !ItemCodec.isAir(cursor) && e.getSlot() < PER_PAGE) {
            LootTools.addOne(plugin, p, tier, cursor.clone(), plugin.settings().defaultWeight);
            e.setCursor(null);
            render();
            return;
        }
        // 2) shift-click an item in your own inventory = add it too (and take it)
        if (!GuiHolder.top(e)) {
            if (e.isShiftClick() && !ItemCodec.isAir(e.getCurrentItem())) {
                LootTools.addOne(plugin, p, tier, e.getCurrentItem().clone(), plugin.settings().defaultWeight);
                e.setCurrentItem(null);
                render();
            }
            return;
        }
        int s = e.getSlot();
        if (s >= PER_PAGE) { toolbar(s); return; }
        int idx = page * PER_PAGE + s;
        List<LootEntry> list = entries();
        if (idx >= list.size()) return;
        entryClick(e, list.get(idx), idx);
    }

    private void toolbar(int s) {
        if (s == 45 && page > 0) { page--; render(); }
        else if (s == 53 && page < pages() - 1) { page++; render(); }
        else if (s == 46) {
            int n = LootTools.addFromInventory(plugin, p, tier, plugin.settings().defaultWeight, false, false);
            plugin.lang().send(p, "loot.added", "count", String.valueOf(n), "tier", tier.name(plugin.lang().code(p)),
                    "weight", String.valueOf(plugin.settings().defaultWeight));
            render();
        } else if (s == 47) {
            int n = LootTools.addFromInventory(plugin, p, tier, plugin.settings().defaultWeight, false, true);
            plugin.lang().send(p, "loot.added", "count", String.valueOf(n), "tier", tier.name(plugin.lang().code(p)),
                    "weight", String.valueOf(plugin.settings().defaultWeight));
            render();
        } else if (s == 51) {
            List<Tier> all = new ArrayList<Tier>(plugin.tiers().all());
            int i = all.indexOf(tier);
            open(plugin, p, all.get((i + 1) % all.size()), 0);
        } else if (s == 52) {
            p.closeInventory();
        }
    }

    private void entryClick(InventoryClickEvent e, LootEntry en, int idx) {
        String type = e.getClick().name();
        int w = en.weight;
        if (type.equals("LEFT")) w += 1;
        else if (type.equals("SHIFT_LEFT")) w += 10;
        else if (type.equals("RIGHT")) w -= 1;
        else if (type.equals("SHIFT_RIGHT")) w -= 10;
        else if (type.equals("MIDDLE")) { en.broadcast = !en.broadcast; changed(); return; }
        else if (type.equals("NUMBER_KEY")) {
            int b = e.getHotbarButton();
            if (b == 0) en.broadcast = !en.broadcast;
            else if (b == 1) en.giveItem = !en.giveItem;
            else if (b >= 2 && b <= 6) en.weight = RARITY[b - 2];            // keys 3-7: common ... legendary
            else return;
            changed();
            return;
        } else if (type.equals("SWAP_OFFHAND")) { commandPrompt(en); return; }
        else if (type.equals("DROP") || type.equals("CONTROL_DROP")) {
            long now = System.currentTimeMillis();
            if (armedDelete == idx && now - armedAt < 4000) {
                plugin.loot().remove(tier, idx);
                armedDelete = -1;
                changed();
            } else {
                armedDelete = idx;
                armedAt = now;
                render();
            }
            return;
        } else return;
        en.weight = Math.max(1, w);
        changed();
    }

    private void changed() {
        armedDelete = -1;
        plugin.loot().commit(tier);
        render();
    }

    private void commandPrompt(final LootEntry en) {
        final int keepPage = page;
        p.closeInventory();
        plugin.prompts().ask(p, "prompt.commands", new ChatPrompt.Callback() {
            public void answered(Player pl, String text) {
                applyCommandInput(en, text.trim());
                plugin.loot().commit(tier);
                open(plugin, pl, tier, keepPage);
            }
        });
    }

    /** "cancel" | "clear" | "-N" (remove command N) | "[p:] [NN%] command". */
    private void applyCommandInput(LootEntry en, String in) {
        if (in.equalsIgnoreCase("cancel")) return;
        if (in.equalsIgnoreCase("clear")) { en.commands.clear(); return; }
        if (in.matches("-\\d+")) {
            int n = Integer.parseInt(in.substring(1));
            if (n >= 1 && n <= en.commands.size()) en.commands.remove(n - 1);
            return;
        }
        boolean asPlayer = false;
        if (in.regionMatches(true, 0, "p:", 0, 2)) { asPlayer = true; in = in.substring(2).trim(); }
        int chance = 100;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,3})%\\s+(.*)$").matcher(in);
        if (m.matches()) { chance = Integer.parseInt(m.group(1)); in = m.group(2); }
        if (in.startsWith("/")) in = in.substring(1);
        if (!in.isEmpty()) en.commands.add(new LootEntry.Cmd(asPlayer, in, chance));
    }
}
