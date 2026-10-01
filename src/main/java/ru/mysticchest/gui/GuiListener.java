package ru.mysticchest.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import ru.mysticchest.MysticChestPlugin;

/** The only inventory listener: every plugin GUI is its own GuiHolder. */
public final class GuiListener implements Listener {
    private final MysticChestPlugin plugin;

    public GuiListener(MysticChestPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (!(h instanceof GuiHolder)) return;
        GuiHolder g = (GuiHolder) h;
        if (plugin.settings().debug) {
            plugin.getLogger().info("[gui] " + h.getClass().getSimpleName() + " slot=" + e.getSlot() + " raw=" + e.getRawSlot()
                    + " click=" + e.getClick() + " area=" + (GuiHolder.top(e) ? "top" : GuiHolder.bottom(e) ? "bottom" : "outside"));
        }
        if (!g.interactive()) {
            boolean bottom = GuiHolder.bottom(e);
            if (g.allowBottom() && bottom) {
                String action = e.getAction().name();
                // plain pick-up/put-down in the own inventory is fine; anything that could reach the GUI is not
                if (e.isShiftClick() || action.equals("COLLECT_TO_CURSOR") || action.equals("MOVE_TO_OTHER_INVENTORY")) e.setCancelled(true);
            } else {
                e.setCancelled(true);
            }
        }
        g.onClick(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof GuiHolder && !((GuiHolder) h).interactive()) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof GuiHolder) ((GuiHolder) h).onClose(e);
    }
}
