package com.kammoun.raven.mythictools.expiry;

import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.mythictools.item.MythicItemData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

public final class MythicExpiryCoordinator {

    private final MythicToolsModule module;
    private final PriorityBlockingQueue<Entry> queue = new PriorityBlockingQueue<>();
    private final Map<Key, Long> scheduled = new ConcurrentHashMap<>();
    private SchedulerUtil.Task task;

    public MythicExpiryCoordinator(MythicToolsModule module) {
        this.module = module;
    }

    public void start() {
        task = SchedulerUtil.runTimer(this::processDue, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        queue.clear();
        scheduled.clear();
    }

    public void discover(Player player, Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            discoverSlot(player, inventory, slot);
        }
    }

    public void discoverSlot(Player player, Inventory inventory, int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) return;
        ItemStack item = inventory.getItem(slot);
        if (!MythicItemData.timed(item)) return;
        if (MythicItemData.expired(item, System.currentTimeMillis())) {
            inventory.setItem(slot, null);
            module.send(player, "tool.expired", "%tool%", displayName(item));
            return;
        }
        register(player, item);
    }

    public boolean validate(Player player, Inventory inventory, int slot, boolean startDelayed) {
        ItemStack item = inventory.getItem(slot);
        if (!MythicItemData.timed(item)) return true;
        long now = System.currentTimeMillis();
        if (MythicItemData.expired(item, now)) {
            inventory.setItem(slot, null);
            module.send(player, "tool.expired", "%tool%", displayName(item));
            return false;
        }
        if (startDelayed && !MythicItemData.timerStarted(item)) {
            item = module.items().startDelayedTimer(item, now);
            inventory.setItem(slot, item);
        }
        register(player, item);
        return true;
    }

    public void register(Player player, ItemStack item) {
        if (!MythicItemData.timed(item) || !MythicItemData.timerStarted(item)) return;
        String instance = MythicItemData.instanceId(item);
        long expiry = MythicItemData.expiry(item);
        if (instance == null || expiry <= 0L) return;
        Key key = new Key(player.getUniqueId(), instance);
        if (scheduled.getOrDefault(key, Long.MIN_VALUE) == expiry) return;
        scheduled.put(key, expiry);
        queue.add(new Entry(key, expiry));
    }

    public void forget(Player player) {
        scheduled.keySet().removeIf(key -> key.playerId().equals(player.getUniqueId()));
    }

    private void processDue() {
        long now = System.currentTimeMillis();
        while (!queue.isEmpty() && queue.peek().expiry() <= now) {
            Entry entry = queue.poll();
            if (!scheduled.remove(entry.key(), entry.expiry())) continue;
            Player player = Bukkit.getPlayer(entry.key().playerId());
            if (player == null) continue;
            SchedulerUtil.execute(player, () -> removeInstance(player, entry.key().instanceId(), now));
        }
    }

    private void removeInstance(Player player, String instanceId, long now) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !instanceId.equals(MythicItemData.instanceId(item))) continue;
            if (!MythicItemData.expired(item, now)) {
                register(player, item);
                return;
            }
            inventory.setItem(slot, null);
            module.send(player, "tool.expired", "%tool%", displayName(item));
        }
    }

    private String displayName(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item == null || item.getType() == Material.AIR ? "item" : item.getType().name();
    }

    private record Key(UUID playerId, String instanceId) {}

    private record Entry(Key key, long expiry) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return Long.compare(expiry, other.expiry);
        }
    }
}
