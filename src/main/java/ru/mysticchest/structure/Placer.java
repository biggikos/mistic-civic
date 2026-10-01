package ru.mysticchest.structure;

import org.bukkit.block.Block;

/** Something that can put itself into a block: a themed material or a saved BlockData. */
interface Placer {
    void apply(Block b);
}
