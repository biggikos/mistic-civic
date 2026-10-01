package ru.mysticchest.structure;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** A material resolved through XSeries, with the legacy data value that 1.12 needs. */
public final class Mat implements Placer {
    public static final boolean LEGACY = !classExists("org.bukkit.block.data.BlockData");
    private static final java.lang.reflect.Method SET_DATA = legacySetter();
    public final Material type;
    public final byte data;

    private Mat(Material t, byte d) { type = t; data = d; }

    private static java.lang.reflect.Method legacySetter() {
        try { return org.bukkit.block.Block.class.getMethod("setData", byte.class, boolean.class); } catch (Throwable t) { return null; }
    }

    /** 1.12 stores variants (mossy bricks, stone types...) in a data byte; the API for it is gone on modern versions. */
    public static void setData(Block b, byte d) {
        if (SET_DATA == null) return;
        try { SET_DATA.invoke(b, d, false); } catch (Throwable ignored) {}
    }

    private static boolean classExists(String n) {
        try { Class.forName(n); return true; } catch (Throwable t) { return false; }
    }

    /** First name that exists on this server version. */
    @SuppressWarnings("deprecation")
    public static Mat of(String... names) {
        for (String n : names) {
            java.util.Optional<XMaterial> x = XMaterial.matchXMaterial(n);
            if (x.isPresent() && x.get().isSupported()) {
                ItemStack it = x.get().parseItem();
                if (it != null && it.getType().isBlock()) return new Mat(it.getType(), (byte) it.getDurability());
            }
        }
        return new Mat(Material.STONE, (byte) 0);
    }

    public void apply(Block b) { place(b); }

    @SuppressWarnings("deprecation")
    public void place(Block b) {
        b.setType(type, false);
        if (LEGACY && data != 0) setData(b, data);
    }
}
