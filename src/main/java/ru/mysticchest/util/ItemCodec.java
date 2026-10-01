package ru.mysticchest.util;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads/writes reward icons. Plain items use the readable "material:" shorthand (cross-version);
 * anything with meta (names, enchants, NBT) is stored as a full serialized "item:".
 */
public final class ItemCodec {
    private ItemCodec() {}

    public static ItemStack read(ConfigurationSection s) {
        Object full = s.get("item");
        if (full instanceof ItemStack) return ((ItemStack) full).clone();
        String mat = s.getString("material");
        if (mat == null) return null;
        java.util.Optional<XMaterial> xm = XMaterial.matchXMaterial(mat);
        if (!xm.isPresent()) return null;          // unknown on this server version
        ItemStack it = xm.get().parseItem();
        if (it == null) return null;
        String name = s.getString("name");
        List<String> lore = s.getStringList("lore");
        if (name != null || !lore.isEmpty()) {
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                if (name != null) m.setDisplayName(Text.color(name));
                if (!lore.isEmpty()) {
                    List<String> l = new ArrayList<String>();
                    for (String x : lore) l.add(Text.color(x));
                    m.setLore(l);
                }
                it.setItemMeta(m);
            }
        }
        return it;
    }

    @SuppressWarnings("deprecation")
    public static void write(ConfigurationSection s, ItemStack it) {
        boolean plain = !it.hasItemMeta() && it.getDurability() == 0;
        if (plain) {
            try {
                s.set("material", XMaterial.matchXMaterial(it).name());
                return;
            } catch (Throwable ignored) {
                // fall through to full serialization
            }
        }
        ItemStack one = it.clone();
        one.setAmount(1);
        s.set("item", one);
    }

    public static boolean similar(ItemStack a, ItemStack b) {
        ItemStack x = a.clone(), y = b.clone();
        x.setAmount(1);
        y.setAmount(1);
        return x.isSimilar(y);
    }

    public static boolean isAir(ItemStack it) {
        return it == null || it.getType() == Material.AIR || it.getAmount() <= 0;
    }
}
