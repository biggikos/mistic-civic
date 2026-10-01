package ru.mysticchest.structure;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Selection tool for /mystic structure save: left click = corner 1, right click = corner 2. */
public final class Wand implements Listener {
    private static final String MARKER = "MC-WAND";
    private final MysticChestPlugin plugin;
    private final Map<UUID, Block[]> selections = new HashMap<UUID, Block[]>();

    public Wand(MysticChestPlugin plugin) { this.plugin = plugin; }

    public ItemStack item(Player p) {
        ItemStack it = XMaterial.BLAZE_ROD.parseItem();
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(plugin.lang().get(p, "structure.wand.name"));
        List<String> lore = new ArrayList<String>(plugin.lang().list(p, "structure.wand.lore"));
        lore.add(Text.color("&8" + MARKER));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private static boolean is(ItemStack it) {
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasLore()) return false;
        for (String l : it.getItemMeta().getLore()) if (org.bukkit.ChatColor.stripColor(l).equals(MARKER)) return true;
        return false;
    }

    public void set(Player p, int corner, Block b) {
        Block[] sel = selections.get(p.getUniqueId());
        if (sel == null) { sel = new Block[2]; selections.put(p.getUniqueId(), sel); }
        if (sel[0] != null && sel[0].getWorld() != b.getWorld()) sel[0] = null;
        if (sel[1] != null && sel[1].getWorld() != b.getWorld()) sel[1] = null;
        sel[corner] = b;
        String size = "?";
        if (sel[0] != null && sel[1] != null) {
            int dx = Math.abs(sel[0].getX() - sel[1].getX()) + 1, dy = Math.abs(sel[0].getY() - sel[1].getY()) + 1, dz = Math.abs(sel[0].getZ() - sel[1].getZ()) + 1;
            size = dx + "×" + dy + "×" + dz;
        }
        plugin.lang().send(p, "structure.corner", "n", String.valueOf(corner + 1), "x", String.valueOf(b.getX()),
                "y", String.valueOf(b.getY()), "z", String.valueOf(b.getZ()), "size", size);
    }

    public Block[] get(Player p) { return selections.get(p.getUniqueId()); }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        if (!is(p.getInventory().getItemInHand())) return;
        e.setCancelled(true);
        if (!p.hasPermission("mysticchest.admin.structure")) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) set(p, 0, e.getClickedBlock());
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) set(p, 1, e.getClickedBlock());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { selections.remove(e.getPlayer().getUniqueId()); }
}
