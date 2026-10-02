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
import ru.mysticchest.structure.StructureCatalog;
import ru.mysticchest.structure.Theme;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game catalog of structures: weights with live chances, on/off, fixed theme, preview, delete.
 * The chance shown on every entry is recomputed on each click, so you see the effect of a change at once.
 */
public final class StructureEditorGui implements GuiHolder {
    private static final String[] THEMES = {"AUTO", "DESERT", "STONE", "NETHER", "END", "FROST", "OCEAN"};
    private final MysticChestPlugin plugin;
    private final Player p;
    private final Inventory inv;
    private final List<StructureCatalog.Entry> shown = new ArrayList<StructureCatalog.Entry>();
    private int armedDelete = -1;
    private long armedAt;

    private StructureEditorGui(MysticChestPlugin plugin, Player p) {
        this.plugin = plugin;
        this.p = p;
        this.inv = Bukkit.createInventory(this, 54, Text.title(plugin.lang().get(p, "structure.editor.title")));
    }

    public static void open(MysticChestPlugin plugin, Player p) {
        StructureEditorGui g = new StructureEditorGui(plugin, p);
        g.render();
        p.openInventory(g.inv);
    }

    private static String iconFor(StructureCatalog.Entry e) {
        if (e.custom()) return "BRICKS";
        switch (e.shape) {
            case PYRAMID: return "SANDSTONE";
            case TEMPLE: return "QUARTZ_BLOCK";
            case OBELISK: return "OBSIDIAN";
            case HENGE: return "MOSSY_COBBLESTONE";
            case TOWER: return "STONE_BRICKS";
            case COLOSSEUM: return "SMOOTH_SANDSTONE";
            case CRYSTALS: return "AMETHYST_BLOCK";
            case RUNES: return "CHISELED_STONE_BRICKS";
            case RUINED_PORTAL: return "CRYING_OBSIDIAN";
            case VOLCANO: return "MAGMA_BLOCK";
            case SHIPWRECK: return "DARK_OAK_PLANKS";
            case DRAGON_BONES: return "BONE_BLOCK";
            case CASTLE_RUIN: return "COBBLESTONE_WALL";
            case WITCH_HUT: return "CAULDRON";
            case GRAVEYARD: return "SOUL_SAND";
            case NETHER_OUTPOST: return "NETHER_BRICKS";
            default: return "PRISMARINE_BRICKS";
        }
    }

    private ItemStack icon(StructureCatalog.Entry e, int idx) {
        java.util.Optional<XMaterial> xm = XMaterial.matchXMaterial(iconFor(e));
        ItemStack it = xm.isPresent() && xm.get().parseItem() != null ? xm.get().parseItem() : new ItemStack(org.bukkit.Material.STONE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(plugin.lang().get(p, e.enabled ? "structure.editor.name-on" : "structure.editor.name-off", "name", e.id));
        List<String> lore = new ArrayList<String>();
        lore.add(plugin.lang().get(p, e.custom() ? "structure.editor.custom" : "structure.editor.builtin"));
        if (e.custom()) {
            lore.add(plugin.lang().get(p, "structure.editor.size", "blocks", String.valueOf(e.template.blocks()),
                    "w", String.valueOf(e.template.width()), "d", String.valueOf(e.template.depth()), "h", String.valueOf(e.template.height)));
        }
        lore.add(plugin.lang().get(p, "structure.editor.weight", "weight", String.valueOf(e.weight),
                "chance", String.format(Locale.ROOT, "%.1f", plugin.structures().catalog().chance(e))));
        lore.add(plugin.lang().get(p, "structure.editor.theme", "theme", e.custom() ? "-" : (e.theme == null ? "AUTO" : e.theme)));
        lore.add("");
        lore.addAll(plugin.lang().list(p, idx == armedDelete ? "structure.editor.help-armed" : (e.custom() ? "structure.editor.help-custom" : "structure.editor.help")));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private void render() {
        inv.clear();
        shown.clear();
        shown.addAll(plugin.structures().catalog().all());
        for (int i = 0; i < shown.size() && i < 45; i++) inv.setItem(i, icon(shown.get(i), i));
        ItemStack fill = XMaterial.matchXMaterial(plugin.settings().filler).orElse(XMaterial.GRAY_STAINED_GLASS_PANE).parseItem();
        ItemMeta fm = fill.getItemMeta();
        fm.setDisplayName(" ");
        fill.setItemMeta(fm);
        for (int i = 45; i < 54; i++) inv.setItem(i, fill.clone());
        ItemStack close = XMaterial.BARRIER.parseItem();
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName(plugin.lang().get(p, "gui.close"));
        close.setItemMeta(cm);
        inv.setItem(49, close);
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return false; }
    public void onClose(InventoryCloseEvent e) {}

    public void onClick(InventoryClickEvent e) {
        if (!p.hasPermission("mysticchest.admin.structure")) { p.closeInventory(); return; }
        if (!GuiHolder.top(e)) return;
        int s = e.getSlot();
        if (s == 49) { p.closeInventory(); return; }
        if (s < 0 || s >= shown.size() || s >= 45) return;
        StructureCatalog.Entry en = shown.get(s);
        String type = e.getClick().name();
        if (type.equals("LEFT")) en.weight += 1;
        else if (type.equals("SHIFT_LEFT")) en.weight += 5;
        else if (type.equals("RIGHT")) en.weight = Math.max(0, en.weight - 1);
        else if (type.equals("SHIFT_RIGHT")) en.weight = Math.max(0, en.weight - 5);
        else if (type.equals("NUMBER_KEY") && e.getHotbarButton() == 0) en.enabled = !en.enabled;
        else if (type.equals("NUMBER_KEY") && e.getHotbarButton() == 1 && !en.custom()) {
            int i = 0;
            String cur = en.theme == null ? "AUTO" : en.theme;
            for (int k = 0; k < THEMES.length; k++) if (THEMES[k].equals(cur)) i = k;
            String next = THEMES[(i + 1) % THEMES.length];
            en.theme = next.equals("AUTO") ? null : next;
        } else if (type.equals("SWAP_OFFHAND")) {
            p.closeInventory();
            plugin.structures().preview(p, en.id, null);
            return;
        } else if ((type.equals("DROP") || type.equals("CONTROL_DROP")) && en.custom()) {
            long now = System.currentTimeMillis();
            if (armedDelete == s && now - armedAt < 4000) {
                plugin.structures().catalog().remove(en.id);
                armedDelete = -1;
                render();
                return;
            }
            armedDelete = s;
            armedAt = now;
            render();
            return;
        } else return;
        armedDelete = -1;
        plugin.structures().catalog().save();
        render();
    }
}
