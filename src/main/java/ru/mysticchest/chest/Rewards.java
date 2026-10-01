package ru.mysticchest.chest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.mysticchest.MysticChestPlugin;
import ru.mysticchest.core.Metrics;
import ru.mysticchest.util.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Rolling (with pity), command execution, announcements and item delivery. */
public final class Rewards {
    private final MysticChestPlugin plugin;

    public Rewards(MysticChestPlugin plugin) { this.plugin = plugin; }

    public List<Reward> roll(Player p, Tier t, int count) {
        long start = System.nanoTime();
        List<Reward> out = new ArrayList<Reward>(count);
        LootPool pool = t.pool;
        if (pool.isEmpty()) return out;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean hadRare = false;
        java.util.Map<String, Integer> batch = new java.util.HashMap<String, Integer>();   // wins inside this opening count against limit-per-player
        for (int i = 0; i < count; i++) {
            LootEntry e = pickAllowed(p, pool, r, batch);
            if (e == null) continue;
            if (e.limitPerPlayer > 0) batch.put(e.id, (batch.containsKey(e.id) ? batch.get(e.id) : 0) + 1);
            hadRare |= e.rare;
            out.add(new Reward(e, e.roll()));
        }
        if (t.pityAfter > 0 && !out.isEmpty()) {
            UUID id = p.getUniqueId();
            if (hadRare) {
                plugin.cooldowns().setPity(id, t.id, 0);
            } else {
                int c = plugin.cooldowns().pity(id, t.id) + 1;
                if (c >= t.pityAfter && pool.hasRare()) {
                    LootEntry e = pool.pickRare(r);
                    out.set(out.size() - 1, new Reward(e, e.roll()));
                    c = 0;
                }
                plugin.cooldowns().setPity(id, t.id, c);
            }
        }
        Metrics.roll(start);
        return out;
    }

    private LootEntry pickAllowed(Player p, LootPool pool, ThreadLocalRandom r, java.util.Map<String, Integer> batch) {
        for (int i = 0; i < 6; i++) {
            LootEntry e = pool.pick(r);
            if (e == null) return null;
            boolean permOk = e.permission == null || e.permission.isEmpty() || p.hasPermission(e.permission);
            boolean limitOk = e.limitPerPlayer <= 0 || plugin.cooldowns().wins(p.getUniqueId(), e.id) + (batch.containsKey(e.id) ? batch.get(e.id) : 0) < e.limitPerPlayer;
            if (permOk && limitOk) return e;
        }
        return null;
    }

    /** Runs commands, announcements and effects; hands the item over when deliverItem is set. */
    public void apply(Player p, Tier t, Reward r, boolean deliverItem, boolean winFx) {
        if (r.applied) return;
        r.applied = true;
        LootEntry e = r.entry;
        if (e.limitPerPlayer > 0) plugin.cooldowns().addWin(p.getUniqueId(), e.id);
        for (LootEntry.Cmd c : e.commands) {
            if (c.chance < 100 && ThreadLocalRandom.current().nextInt(100) >= c.chance) continue;
            String cmd = c.text.replace("{player}", p.getName());
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (c.asPlayer) p.performCommand(cmd);
            else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        if (deliverItem && e.giveItem) give(p, r.stack);
        String item = Items.name(r.stack);
        if (e.rare || e.broadcast) {
            plugin.announcer().send(plugin.settings().onRare, p.getLocation(), "announce.rare", t,
                    "player", p.getName(), "item", item, "amount", String.valueOf(r.stack.getAmount()));
            if (plugin.settings().fwOnRare) plugin.fireworks().launch(p.getLocation(), t.color);
            plugin.effects().playTier(plugin.settings().fxRare, p, t, "player", p.getName(), "item", item);
        } else if (winFx) {
            plugin.effects().playTier(plugin.settings().fxWin, p, t, "player", p.getName(), "item", item);
        }
    }

    public void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it.clone()).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    public void give(Player p, List<ItemStack> items) {
        for (ItemStack it : items) give(p, it);
    }
}
