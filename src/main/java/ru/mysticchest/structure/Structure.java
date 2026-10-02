package ru.mysticchest.structure;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** A built structure: its bounding box and the originals of every block it replaced (so it can be undone). */
public final class Structure {
    public final World world;
    public final int minX, maxX, minY, maxY, minZ, maxZ;
    public final Location chest;
    /** Other chest cells of the same structure (beacon platform) and its centre on the ground plane. */
    public final java.util.List<Location> extraChests = new java.util.ArrayList<Location>();
    public Location center;
    /** Where guards and the boss stand (sign markers of a saved structure) and extra chests with their tier ("" = the main one). */
    public final java.util.List<Location> guardPoints = new java.util.ArrayList<Location>();
    public final java.util.List<Location> bossPoints = new java.util.ArrayList<Location>();
    public final java.util.List<Location> lootPoints = new java.util.ArrayList<Location>();
    public final java.util.List<String> lootTiers = new java.util.ArrayList<String>();
    /** Standing chests that belong to this structure: it collapses when the last one is gone. */
    public int refs;
    final List<int[]> positions = new ArrayList<int[]>();
    final List<Snap> originals = new ArrayList<Snap>();
    final List<int[]> chunks = new ArrayList<int[]>();
    public String name = "";
    File file;
    boolean restoring, restored;

    Structure(World w, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Location chest) {
        this.world = w;
        this.minX = minX; this.maxX = maxX; this.minY = minY; this.maxY = maxY; this.minZ = minZ; this.maxZ = maxZ;
        this.chest = chest;
    }

    public boolean contains(Block b) {
        return b.getWorld() == world && b.getX() >= minX && b.getX() <= maxX && b.getY() >= minY && b.getY() <= maxY
                && b.getZ() >= minZ && b.getZ() <= maxZ;
    }

    public int blocks() { return positions.size(); }
}
