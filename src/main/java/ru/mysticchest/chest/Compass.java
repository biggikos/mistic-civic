package ru.mysticchest.chest;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A compass item that points at the nearest standing chest when right clicked. Costs nothing while unused. */
public final class Compass {
    private static final String MARKER = "MC-COMPASS";
    private static final String[] DIRS = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};
    private final MysticChestPlugin plugin;

    public Compass(MysticChestPlugin plugin) { this.plugin = plugin; }

    public ItemStack item(Player forWho) {
        ItemStack it = XMaterial.COMPASS.parseItem();
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(plugin.lang().get(forWho, "compass.name"));
        List<String> lore = new ArrayList<String>(plugin.lang().list(forWho, "compass.lore"));
        lore.add(Text.color("&8" + MARKER));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    public boolean is(ItemStack it) {
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasLore()) return false;
        for (String l : it.getItemMeta().getLore()) if (org.bukkit.ChatColor.stripColor(l).equals(MARKER)) return true;
        return false;
    }

    public void use(Player p) {
        if (!plugin.settings().compassEnabled) return;
        long left = plugin.cooldowns().remaining(p.getUniqueId(), "compass");
        if (left > 0) return;
        plugin.cooldowns().start(p.getUniqueId(), "compass", plugin.settings().compassCooldown);
        ChestManager.Active a = plugin.chests().nearest(p.getLocation());
        if (a == null) { plugin.lang().send(p, "compass.none"); return; }
        Location l = a.loc;
        p.setCompassTarget(l);
        double dx = l.getX() + 0.5 - p.getLocation().getX(), dz = l.getZ() + 0.5 - p.getLocation().getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        int dir = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0);
        plugin.lang().send(p, "compass.found", "tier", a.tier.name(plugin.lang().code(p)), "distance", String.valueOf(dist),
                "dir", plugin.lang().get(p, "dir." + DIRS[((dir % 8) + 8) % 8]));
    }
}
