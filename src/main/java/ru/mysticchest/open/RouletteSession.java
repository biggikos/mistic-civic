package ru.mysticchest.open;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.chest.LootEntry;
import ru.mysticchest.chest.Reward;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.config.Settings;
import ru.mysticchest.core.Animator;
import ru.mysticchest.gui.GuiHolder;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animated spin. SINGLE flips the middle slot; SCROLL slides a belt of items (middle slot is the pointer).
 * All rolls are decided before the GUI opens, so closing early only fast-forwards, never changes the result.
 */
final class RouletteSession implements GuiHolder, Animator.Animation {
    private static final int CENTER = 13, BELT_START = 9;

    private final MysticChestPlugin plugin;
    private final Player p;
    private final Tier t;
    private final List<Reward> rewards;
    private final Inventory inv;
    private final boolean scroll;
    private final int[] flips;
    private final ItemStack filler, pointer;

    private int idx, tick, flipIdx, beltPos, pauseLeft;
    private ItemStack[] belt;
    private boolean finished;

    RouletteSession(MysticChestPlugin plugin, Player p, Tier t, List<Reward> rewards) {
        this.plugin = plugin;
        this.p = p;
        this.t = t;
        this.rewards = rewards;
        Settings s = plugin.settings();
        this.scroll = s.rouletteStyle == Settings.Style.SCROLL
                || (s.rouletteStyle == Settings.Style.RANDOM && java.util.concurrent.ThreadLocalRandom.current().nextBoolean());
        this.flips = schedule(s.rouletteTicks);
        this.inv = Bukkit.createInventory(this, 27,
                Text.title(plugin.lang().get(p, "gui.roulette.title", "tier", t.name(plugin.lang().code(p)))));
        this.filler = named(XMaterial.PURPLE_STAINED_GLASS_PANE, " ");
        this.pointer = named(XMaterial.LIME_STAINED_GLASS_PANE, " ");
    }

    /** Flip moments in ticks; the gap grows quadratically so the wheel visibly slows down. */
    private static int[] schedule(int total) {
        List<Integer> l = new ArrayList<Integer>();
        int t = 0, d = 1;
        while (t < total) {
            t += d;
            l.add(t);
            double progress = (double) t / total;
            d = 1 + (int) (progress * progress * 7);
        }
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    private static ItemStack named(XMaterial m, String name) {
        ItemStack it = m.parseItem();
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }

    void start() {
        if (rewards.isEmpty()) return;
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        if (scroll) { inv.setItem(4, pointer); inv.setItem(22, pointer); }
        prepare();
        p.openInventory(inv);
        plugin.animator().add(this);
    }

    private ItemStack randomIcon() {
        LootEntry e = t.pool.pick(ThreadLocalRandom.current());
        return e == null ? filler : e.item;   // Inventory#setItem copies, so the shared template is safe to reuse
    }

    private void prepare() {
        tick = 0;
        flipIdx = 0;
        beltPos = 0;
        pauseLeft = 0;
        if (scroll) {
            int s = flips.length;
            belt = new ItemStack[s + 9];
            for (int i = 0; i < belt.length; i++) belt[i] = randomIcon();
            belt[s + 4] = rewards.get(idx).stack.clone();
            drawBelt();
        } else {
            inv.setItem(CENTER, randomIcon());
        }
    }

    private void drawBelt() {
        for (int i = 0; i < 9; i++) inv.setItem(BELT_START + i, belt[beltPos + i]);
    }

    public boolean tick() {
        if (finished) return false;
        if (!p.isOnline()) { finishNow(); return false; }
        if (pauseLeft > 0) {
            if (--pauseLeft == 0) {
                if (++idx >= rewards.size()) {
                    finished = true;
                    if (p.getOpenInventory().getTopInventory().getHolder() == this) p.closeInventory();
                    return false;
                }
                prepare();
            }
            return true;
        }
        tick++;
        while (flipIdx < flips.length && tick >= flips[flipIdx]) {
            flipIdx++;
            if (scroll) { beltPos++; drawBelt(); } else inv.setItem(CENTER, randomIcon());
            plugin.effects().play(plugin.settings().fxTick, p);
        }
        if (flipIdx >= flips.length) {
            Reward r = rewards.get(idx);
            if (!scroll) inv.setItem(CENTER, r.stack.clone());
            plugin.rewards().apply(p, t, r, true, true);
            boolean last = idx == rewards.size() - 1;
            pauseLeft = Math.max(1, last ? Math.max(25, plugin.settings().roulettePause) : plugin.settings().roulettePause);
        }
        return true;
    }

    /** Hands out everything that has not been awarded yet (early close, quit, reload). */
    private void finishNow() {
        if (finished) return;
        finished = true;
        for (Reward r : rewards) {
            if (!r.applied) plugin.rewards().apply(p, t, r, true, false);
        }
    }

    public void abort() {
        finishNow();
        if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == this) p.closeInventory();
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return false; }
    public void onClick(InventoryClickEvent e) {}
    public void onClose(InventoryCloseEvent e) { finishNow(); }
}
