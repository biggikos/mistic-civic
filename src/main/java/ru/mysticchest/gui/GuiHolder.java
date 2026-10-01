package ru.mysticchest.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

/** Every plugin GUI is its own holder; the single listener just delegates. */
public interface GuiHolder extends InventoryHolder {
    void onClick(InventoryClickEvent e);
    void onClose(InventoryCloseEvent e);
    /** true = the player may freely move items in this GUI (loot chest). */
    boolean interactive();

    /**
     * true when the click landed in the GUI itself. Compared by raw slot because old servers (1.12)
     * hand out a wrapper object for the top inventory, so identity checks against our Inventory fail.
     */
    static boolean top(InventoryClickEvent e) {
        int r = e.getRawSlot();
        return r >= 0 && r < e.getView().getTopInventory().getSize();
    }

    /** true when the click landed in the player's own inventory below the GUI. */
    static boolean bottom(InventoryClickEvent e) {
        return e.getRawSlot() >= e.getView().getTopInventory().getSize();
    }
    /** true = plain clicks in the player's own inventory are allowed (needed to pick up an item and drop it on the window). */
    default boolean allowBottom() { return false; }
}
