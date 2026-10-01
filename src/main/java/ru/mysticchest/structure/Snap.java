package ru.mysticchest.structure;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** What a block looked like before the structure replaced it. Modern servers keep the full BlockData. */
abstract class Snap implements Placer {
    public abstract void apply(Block b);
    abstract String encode();
    /** Quarter turns clockwise; legacy blocks cannot be rotated, modern ones rotate their facing. */
    Snap rotate(int quarters) { return this; }

    static Snap of(Block b) { return Mat.LEGACY ? new Legacy(b.getType(), b.getData()) : new Modern(b.getBlockData()); }

    static Snap decode(String s) {
        if (Mat.LEGACY) {
            int i = s.lastIndexOf(':');
            Material m = Material.matchMaterial(s.substring(0, i));
            return new Legacy(m == null ? Material.AIR : m, Byte.parseByte(s.substring(i + 1)));
        }
        return new Modern(Bukkit.createBlockData(s));
    }

    private static final class Legacy extends Snap {
        final Material m;
        final byte d;
        Legacy(Material m, byte d) { this.m = m; this.d = d; }
        @SuppressWarnings("deprecation")
        public void apply(Block b) { b.setType(m, false); if (d != 0) Mat.setData(b, d); }
        String encode() { return m.name() + ":" + d; }
    }

    private static final class Modern extends Snap {
        final BlockData data;
        Modern(BlockData d) { data = d; }
        public void apply(Block b) { b.setBlockData(data, false); }
        @Override
        Snap rotate(int quarters) {
            if (quarters % 4 == 0) return this;
            try {
                Class<?> rot = Class.forName("org.bukkit.block.structure.StructureRotation");
                Object r = rot.getEnumConstants()[quarters % 4];   // NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90
                BlockData copy = data.clone();
                copy.getClass().getMethod("rotate", rot).invoke(copy, r);
                return new Modern(copy);
            } catch (Throwable t) {
                return this;
            }
        }
        String encode() { return data.getAsString(); }
    }
}
