package ru.mysticchest.chest;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.config.Settings;

import java.util.Iterator;

public final class WorldListener implements Listener {
    private final MysticChestPlugin plugin;

    public WorldListener(MysticChestPlugin plugin) { this.plugin = plugin; }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action act = e.getAction();
        boolean block = act == Action.RIGHT_CLICK_BLOCK;
        if (!block && act != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer();

        // 1) a chest standing in the world
        if (block) {
            ChestManager.Active a = plugin.chests().at(e.getClickedBlock());
            if (a != null) {
                e.setCancelled(true);
                openWorld(p, a);
                return;
            }
        }

        // 2) a chest item in hand (cheap checks first)
        ItemStack hand = p.getInventory().getItemInHand();
        if (hand == null || !hand.hasItemMeta()) return;
        Tier t = plugin.chests().tierOf(hand);
        if (t == null) {
            if (plugin.compass().is(hand)) { e.setCancelled(true); plugin.compass().use(p); }
            return;
        }
        boolean denied = e.isCancelled() || e.useInteractedBlock() == Event.Result.DENY;   // read BEFORE we cancel
        e.setCancelled(true);
        if (!p.hasPermission("mysticchest.use")) { plugin.lang().send(p, "deny.no-permission"); return; }

        Settings s = plugin.settings();
        if (block ? s.clickBlock == Settings.ClickBlock.NONE : s.clickAir == Settings.ClickAir.NONE) return;
        boolean place = block && s.clickBlock == Settings.ClickBlock.PLACE;
        if (place) {
            if (denied && s.respectRegions) return;
            Block target = e.getClickedBlock().getRelative(e.getBlockFace());
            if (!plugin.chests().place(target.getLocation(), t, "player", p.getUniqueId())) {
                plugin.lang().send(p, "deny.cannot-place");
                return;
            }
            consume(p);
            plugin.lang().send(p, "world.placed", "tier", t.name(plugin.lang().code(p)));
        } else {
            String[] deny = plugin.open().check(p, t);
            if (deny != null) { sendDeny(p, deny); return; }
            consume(p);
            plugin.open().open(p, t);
        }
    }

    private void openWorld(Player p, ChestManager.Active a) {
        if (!p.hasPermission("mysticchest.use")) { plugin.lang().send(p, "deny.no-permission"); return; }
        long now = System.currentTimeMillis();
        if (a.owner != null && now < a.claimUntil && !a.owner.equals(p.getUniqueId())
                && !p.hasPermission("mysticchest.bypass.cooldown")) {
            plugin.lang().send(p, "deny.claimed", "time", plugin.lang().time(p, (a.claimUntil - now + 999) / 1000));
            return;
        }
        if (plugin.open().pinataActive(a)) { plugin.open().openWorldChest(p, a); return; }   // keep hitting the pinata
        if (a.sleeping()) {
            plugin.lang().send(p, "activation.sleeping", "time", plugin.lang().time(p, plugin.chests().wakeIn(a)), "tier", a.tier.name(plugin.lang().code(p)));
            plugin.effects().soundTo(p, "BLOCK_NOTE_BLOCK_BASS", 0.7f, 0.7f);
            return;
        }
        if (plugin.guards().locked(a)) {
            plugin.lang().send(p, "guards.locked", "count", String.valueOf(plugin.guards().left(a)));
            return;
        }
        if (plugin.open().openedShared(p, a)) { plugin.open().openWorldChest(p, a); return; }   // back into the shared chest
        String[] deny = plugin.open().check(p, a.tier);
        if (deny != null) { sendDeny(p, deny); return; }
        if (plugin.captures().intercept(p, a)) return;
        plugin.open().openWorldChest(p, a);
    }

    private void sendDeny(Player p, String[] deny) {
        String[] kv = new String[deny.length - 1];
        System.arraycopy(deny, 1, kv, 0, kv.length);
        plugin.lang().send(p, deny[0], kv);
    }

    @SuppressWarnings("deprecation")
    private void consume(Player p) {
        ItemStack hand = p.getInventory().getItemInHand();
        if (hand.getAmount() <= 1) p.getInventory().setItemInHand(null);
        else hand.setAmount(hand.getAmount() - 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!e.getItemInHand().hasItemMeta()) return;
        if (plugin.chests().tierOf(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.settings().protBreak && (plugin.chests().isActive(e.getBlock()) || plugin.structures().protects(e.getBlock()))) e.setCancelled(true);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (plugin.settings().protExplosions) strip(e.blockList().iterator());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (plugin.settings().protExplosions) strip(e.blockList().iterator());
    }

    private void strip(Iterator<Block> it) {
        if (plugin.chests().count() == 0 && plugin.structures().count() == 0) return;
        while (it.hasNext()) { Block b = it.next(); if (plugin.chests().isActive(b) || plugin.structures().protects(b)) it.remove(); }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!plugin.settings().protPistons || plugin.chests().count() == 0) return;
        for (Block b : e.getBlocks()) if (plugin.chests().isActive(b)) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!plugin.settings().protPistons || plugin.chests().count() == 0) return;
        for (Block b : e.getBlocks()) if (plugin.chests().isActive(b)) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        // Servers with plugin chunk tickets keep chest chunks loaded on their own; older ones need the unload vetoed.
        if (ChestManager.ticketsSupported() || plugin.chests().count() == 0) return;
        if (e instanceof org.bukkit.event.Cancellable && plugin.chests().hasChestIn(e.getChunk())) {
            ((org.bukkit.event.Cancellable) e).setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { plugin.prompts().cancel(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!plugin.prompts().isWaiting(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        plugin.prompts().answer(e.getPlayer(), e.getMessage());
    }
}
