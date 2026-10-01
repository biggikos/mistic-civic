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
import ru.mysticchest.chest.Reward;
import ru.mysticchest.chest.Tier;
import ru.mysticchest.core.Animator;
import ru.mysticchest.gui.GuiHolder;
import ru.mysticchest.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Mystery cards: face-down slots, the player picks N; the rest is revealed (dimmed) afterwards. */
final class PickSession implements GuiHolder, Animator.Animation {
    private static final int IDLE_TIMEOUT_TICKS = 20 * 60;

    private final MysticChestPlugin plugin;
    private final Player p;
    private final Tier t;
    private final List<Reward> rewards;
    private final Inventory inv;
    private final int[] slots;
    private final boolean[] revealed;
    private int picksLeft, idle, closeIn = -1;
    private boolean finished;

    PickSession(MysticChestPlugin plugin, Player p, Tier t, List<Reward> rewards) {
        this.plugin = plugin;
        this.p = p;
        this.t = t;
        this.rewards = rewards;
        this.picksLeft = Math.min(plugin.settings().pickPicks, rewards.size());
        this.slots = layout(rewards.size());
        this.revealed = new boolean[rewards.size()];
        this.inv = Bukkit.createInventory(this, 27,
                Text.title(plugin.lang().get(p, "gui.pick.title", "tier", t.name(plugin.lang().code(p)),
                        "picks", String.valueOf(picksLeft))));
    }

    private static int[] layout(int n) {
        int[] s = new int[n];
        for (int i = 0; i < n; i++) s[i] = n <= 9 ? (i / 3) * 9 + 3 + (i % 3) : i;
        return s;
    }

    private ItemStack card() {
        ItemStack it = XMaterial.ENDER_CHEST.parseItem();
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(plugin.lang().get(p, "gui.pick.card"));
        m.setLore(plugin.lang().list(p, "gui.pick.card-lore"));
        it.setItemMeta(m);
        return it;
    }

    void start() {
        if (rewards.isEmpty()) return;
        ItemStack c = card();
        for (int s : slots) inv.setItem(s, c.clone());
        p.openInventory(inv);
        plugin.animator().add(this);
    }

    public boolean tick() {
        if (finished) return false;
        if (!p.isOnline()) { finishNow(); return false; }
        if (closeIn >= 0) {
            if (--closeIn <= 0) {
                finished = true;
                if (p.getOpenInventory().getTopInventory().getHolder() == this) p.closeInventory();
                return false;
            }
        } else if (++idle > IDLE_TIMEOUT_TICKS) {
            finishNow();
            if (p.getOpenInventory().getTopInventory().getHolder() == this) p.closeInventory();
            return false;
        }
        return true;
    }

    public void onClick(InventoryClickEvent e) {
        if (finished || picksLeft <= 0 || !GuiHolder.top(e)) return;
        int i = indexOf(e.getSlot());
        if (i < 0 || revealed[i]) return;
        pick(i);
        idle = 0;
    }

    private int indexOf(int slot) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i;
        return -1;
    }

    private void pick(int i) {
        revealed[i] = true;
        Reward r = rewards.get(i);
        inv.setItem(slots[i], r.stack.clone());
        plugin.rewards().apply(p, t, r, true, true);
        if (--picksLeft <= 0) revealRest();
    }

    private void revealRest() {
        for (int i = 0; i < slots.length; i++) {
            if (revealed[i]) continue;
            ItemStack it = rewards.get(i).stack.clone();
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName(plugin.lang().get(p, "gui.pick.missed"));
                it.setItemMeta(m);
            }
            inv.setItem(slots[i], it);
        }
        closeIn = Math.max(1, plugin.settings().pickCloseDelay);
    }

    private void finishNow() {
        if (finished) return;
        finished = true;
        List<Integer> free = new ArrayList<Integer>();
        for (int i = 0; i < revealed.length; i++) if (!revealed[i]) free.add(i);
        while (picksLeft > 0 && !free.isEmpty()) {
            int i = free.remove(ThreadLocalRandom.current().nextInt(free.size()));
            revealed[i] = true;
            plugin.rewards().apply(p, t, rewards.get(i), true, false);
            picksLeft--;
        }
    }

    public void abort() {
        finishNow();
        if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == this) p.closeInventory();
    }

    public Inventory getInventory() { return inv; }
    public boolean interactive() { return false; }
    public void onClose(InventoryCloseEvent e) { finishNow(); }
}
