package com.kammoun.raven.spawner.listener;

import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.library.util.NBTHelper;
import com.kammoun.raven.library.util.NumberUtils;
import com.kammoun.raven.spawner.SpawnerModule;
import com.kammoun.raven.spawner.config.SpawnerConfig;
import com.kammoun.raven.spawner.config.SpawnerConfig.Msg;
import com.kammoun.raven.spawner.config.SpawnerConfig.Sfx;
import com.kammoun.raven.spawner.gui.SellHolder;
import com.kammoun.raven.spawner.gui.SpawnerSellMenu;
import com.kammoun.raven.spawner.gui.SpawnerStorageMenu;
import com.kammoun.raven.spawner.gui.StorageHolder;
import com.kammoun.raven.spawner.manager.SpawnerRegistry;
import com.kammoun.raven.spawner.manager.SpawnerStackManager;
import com.kammoun.raven.spawner.model.SpawnerDrop;
import com.kammoun.raven.spawner.model.SpawnerStack;
import com.kammoun.raven.spawner.model.SpawnerStorageGui;
import com.kammoun.raven.spawner.model.SpawnerType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class SpawnerListener implements Listener {

    private final SpawnerModule module;
    private final SpawnerConfig config;
    private final SpawnerRegistry registry;
    private final SpawnerStackManager stackManager;
    private final SpawnerStorageMenu storageMenu;
    private final SpawnerSellMenu sellMenu;

    // Maximum physical item-stack size when breaking/receiving spawner items
    private static final int MAX_ITEM_STACK = 64;

    public SpawnerListener(SpawnerModule module) {
        this.module       = module;
        this.config       = module.getSpawnerConfig();
        this.registry     = module.getRegistry();
        this.stackManager = module.getStackManager();
        this.storageMenu  = module.getStorageMenu();
        this.sellMenu     = module.getSellMenu();
    }

    // ─── Block place ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER) return;
        if (!NBTHelper.hasTag(item, "spawner-type")) return;

        String typeId     = NBTHelper.getString(item, "spawner-type");
        int stackPerItem  = NBTHelper.getInt(item, "spawner-stack");
        if (stackPerItem <= 0) stackPerItem = 1;

        SpawnerType type = registry.get(typeId);
        if (type == null) { event.setCancelled(true); return; }

        Player player = event.getPlayer();
        Block block   = event.getBlockPlaced();

        // Amount to add: when sneaking place all items in hand as one stack; otherwise just this item's stack count
        int toAdd = player.isSneaking() ? stackPerItem * item.getAmount() : stackPerItem;

        SpawnerStack existing = stackManager.get(block.getLocation());
        if (existing != null) {
            if (!canAddToStack(player, existing.getStackAmount(), toAdd)) {
                event.setCancelled(true);
                return;
            }
            existing.setStackAmount(existing.getStackAmount() + toAdd);
            existing.markDirty();
        } else {
            if (!canAddToStack(player, 0, toAdd)) {
                event.setCancelled(true);
                return;
            }
            setCreatureType(block, typeId);
            SpawnerStack stack = stackManager.addOrStack(block.getLocation(), type, toAdd,
                    config.isOwnershipEnabled() ? player.getUniqueId() : null);
        }

        // Consume all items from hand when sneaking (normal Bukkit handles non-sneaking single consume)
        if (player.isSneaking()) {
            item.setAmount(0);
        }

        if (config.isPlaceParticles()) {
            block.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(0.5, 0.5, 0.5), 20);
        }

        if (toAdd > 1) {
            config.sendActionBar(player, Msg.STACKED_BAR,
                    "%total%", String.valueOf(existing != null ? existing.getStackAmount() : toAdd),
                    "%type%",  type.getDisplayName());
        }
        module.saveDirtyNow();
    }

    // ─── Block break ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        SpawnerStack stack = stackManager.get(block.getLocation());

        // Untracked spawner — only stop it if convert-natural is off
        if (stack == null) {
            if (!config.isConvertNatural()) return;
            // Still apply silk-touch requirement for natural spawners
        }

        // Silk-touch check
        if (config.isSilkTouchRequired()) {
            boolean hasSilk = player.getInventory().getItemInMainHand()
                    .containsEnchantment(Enchantment.SILK_TOUCH);
            boolean bypassed = config.isSilkTouchPermission()
                    && player.hasPermission("raven.spawner.silktouch");
            if (!hasSilk && !bypassed) {
                event.setCancelled(true);
                config.sendMessage(player, Msg.NO_SILK);
                config.playSound(player, Sfx.NO_SILK);
                return;
            }
        }

        if (stack == null) return;

        // Ownership check
        if (config.isOwnershipEnabled() && stack.getOwnerUUID() != null) {
            if (!stack.getOwnerUUID().equals(player.getUniqueId())
                    && !player.hasPermission("raven.spawner.admin")) {
                event.setCancelled(true);
                config.sendMessage(player, Msg.NOT_OWNER);
                return;
            }
        }

        event.setExpToDrop(0);
        event.setDropItems(false);

        int currentStack = stack.getStackAmount();
        int amountToGive;
        int remaining;

        if (player.isSneaking()) {
            amountToGive = Math.min(currentStack, MAX_ITEM_STACK);
            remaining    = currentStack - amountToGive;
        } else {
            amountToGive = 1;
            remaining    = currentStack - 1;
        }

        stack.dropXp();

        ItemStack spawnerItem = stack.getType().createItem(1);
        spawnerItem.setAmount(amountToGive);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(spawnerItem);
        leftover.values().forEach(i -> block.getWorld().dropItemNaturally(block.getLocation(), i));

        if (remaining > 0) {
            stack.setStackAmount(remaining);
            stack.markDirty();
            module.saveDirtyNow();
            event.setCancelled(true);
        } else {
            String world = block.getWorld().getName();
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            stackManager.remove(block.getLocation());
            SchedulerUtil.runAsync(() -> module.getDatabase().delete(world, x, y, z));
        }
    }

    // ─── Right-click interact ─────────────────────────────────────────────────

    @EventHandler
    public void onSpawnerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        Player player    = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();

        // Holding a raven spawner item — try to add it to the placed stack
        if (inHand.getType() == Material.SPAWNER && NBTHelper.hasTag(inHand, "spawner-type")) {
            SpawnerStack existing = stackManager.get(block.getLocation());
            if (existing != null) {
                String typeId = NBTHelper.getString(inHand, "spawner-type");
                if (existing.getType().getId().equalsIgnoreCase(typeId)) {
                    event.setCancelled(true);
                    int perItem = Math.max(1, NBTHelper.getInt(inHand, "spawner-stack"));
                    int toAdd   = player.isSneaking() ? perItem * inHand.getAmount() : perItem;

                    if (!canAddToStack(player, existing.getStackAmount(), toAdd)) return;

                    existing.setStackAmount(existing.getStackAmount() + toAdd);
                    existing.markDirty();
                    inHand.setAmount(player.isSneaking() ? 0 : inHand.getAmount() - 1);

                    config.sendActionBar(player, Msg.STACKED_BAR,
                            "%total%", String.valueOf(existing.getStackAmount()),
                            "%type%",  existing.getType().getDisplayName());
                    module.saveDirtyNow();
                    return;
                }
            }
            // Different type or no existing stack — cancel to avoid normal block interaction
            event.setCancelled(true);
            return;
        }

        // Sneaking without a spawner in hand → do nothing (don't open GUI on sneak)
        if (player.isSneaking()) return;

        // Open storage GUI
        SpawnerStack stack = stackManager.get(block.getLocation());
        if (stack == null) return;

        event.setCancelled(true);

        if (stack.isInUse()) {
            config.sendMessage(player, Msg.STORAGE_IN_USE);
            return;
        }

        stack.setOpenedByUUID(player.getUniqueId());
        storageMenu.open(player, stack, 1);
    }

    // ─── Explosion protection ─────────────────────────────────────────────────

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!config.isExplosionDestroys()) {
            event.blockList().removeIf(b -> b.getType() == Material.SPAWNER);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!config.isExplosionDestroys()) {
            event.blockList().removeIf(b -> b.getType() == Material.SPAWNER);
        }
    }

    // ─── Cancel vanilla entity spawning ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (event.getSpawner() == null) return;
        SpawnerStack stack = stackManager.get(event.getSpawner().getLocation());
        if (stack != null || config.isConvertNatural()) {
            event.setCancelled(true);
        }
    }

    // ─── Storage GUI clicks ───────────────────────────────────────────────────

    @EventHandler
    public void onStorageClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        SpawnerStack stack = holder.getStack();
        int page    = holder.getPage();
        int maxPages = holder.getMaxPages();
        int slot    = event.getSlot();
        SpawnerStorageGui gui = stack.getType().getStorageGui();

        // Navigation buttons
        if (slot == gui.prevPageSlot() && page > 1) {
            config.playSound(player, Sfx.PAGE_SWITCH);
            storageMenu.open(player, stack, page - 1);
            return;
        }
        if (slot == gui.nextPageSlot() && page < maxPages) {
            config.playSound(player, Sfx.PAGE_SWITCH);
            storageMenu.open(player, stack, page + 1);
            return;
        }

        // Drop all (current page)
        if (slot == gui.dropAllSlot()) {
            dropPageItems(player, stack, page);
            config.playSound(player, Sfx.CLICK);
            storageMenu.open(player, stack, page);
            return;
        }

        // Sell all
        if (slot == gui.sellAllSlot()) {
            if (!stack.getStoredItems().isEmpty()) {
                double value = calculateValue(stack);
                sellMenu.open(player, stack, value);
            }
            return;
        }

        // Spawner icon slot — no action
        if (slot == gui.iconSlot()) return;

        // Content slots (0–44): collect single stack
        if (slot >= 0 && slot < 45) {
            collectItem(player, stack, page, slot);
        }
    }

    @EventHandler
    public void onStorageClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof StorageHolder holder) {
            holder.getStack().clearOpenedBy();
        }
    }

    // ─── Sell GUI clicks ──────────────────────────────────────────────────────

    @EventHandler
    public void onSellClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        SpawnerStack stack = holder.getStack();
        double totalValue  = holder.getTotalValue();
        int slot = event.getSlot();

        if (slot == sellMenu.confirmSlot()) {
            executeSell(player, stack, totalValue);
        } else if (slot == sellMenu.declineSlot()) {
            config.playSound(player, Sfx.CLICK);
            storageMenu.open(player, stack, 1);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private boolean canAddToStack(Player player, int current, int toAdd) {
        if (current + toAdd > config.getMaxStack()) {
            config.sendMessage(player, Msg.STACK_LIMIT, "%max%", String.valueOf(config.getMaxStack()));
            config.playSound(player, Sfx.LIMIT_REACHED);
            return false;
        }
        return true;
    }

    private void setCreatureType(Block block, String typeId) {
        if (!(block.getState() instanceof CreatureSpawner cs)) return;
        try {
            cs.setSpawnedType(EntityType.valueOf(typeId));
            cs.update();
        } catch (IllegalArgumentException ignored) {}
    }

    private void collectItem(Player player, SpawnerStack stack, int page, int slot) {
        List<ItemStack> displayItems = storageMenu.buildDisplayItems(stack);
        int index = (page - 1) * 45 + slot;
        if (index >= displayItems.size()) return;

        ItemStack displayed = displayItems.get(index);
        Material mat = displayed.getType();
        int amount   = displayed.getAmount();

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(mat, amount));
        int actuallyGiven = amount - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();

        if (actuallyGiven > 0) {
            stack.removeItems(mat, actuallyGiven);
            config.playSound(player, Sfx.CLICK);
            module.saveDirtyNow();
            storageMenu.open(player, stack, page);
        }
        if (!leftover.isEmpty()) {
            config.sendMessage(player, Msg.INVENTORY_FULL);
            config.playSound(player, Sfx.INVENTORY_FULL);
        }
    }

    private void dropPageItems(Player player, SpawnerStack stack, int page) {
        List<ItemStack> displayItems = storageMenu.buildDisplayItems(stack);
        int start = (page - 1) * 45;
        int end   = Math.min(start + 45, displayItems.size());

        for (int i = start; i < end; i++) {
            ItemStack item = displayItems.get(i);
            stack.removeItems(item.getType(), item.getAmount());
            int remaining = item.getAmount();
            while (remaining > 0) {
                int drop = Math.min(remaining, 64);
                player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(item.getType(), drop));
                remaining -= drop;
            }
        }
        module.saveDirtyNow();
    }

    private double calculateValue(SpawnerStack stack) {
        double total = 0;
        for (SpawnerDrop drop : stack.getType().getDrops()) {
            int stored = stack.getItemAmount(drop.getMaterial());
            if (stored <= 0) continue;
            total += drop.getSellPrice() * stored;
        }
        return total;
    }

    private void executeSell(Player player, SpawnerStack stack, double totalValue) {
        if (totalValue <= 0) return;

        stack.giveXp(player);
        stack.clearStorage();
        module.saveDirtyNow();

        RavenEconomy economy = module.getEconomy();
        if (economy != null) {
            economy.depositPlayer(player, totalValue);
        }

        config.sendActionBar(player, Msg.SELL_BAR, "%amount%", NumberUtils.formatDouble(totalValue));
        config.sendMessage(player,   Msg.SELL_CHAT, "%amount%", NumberUtils.formatDouble(totalValue));
        config.playSound(player, Sfx.CLICK);

        player.closeInventory();
    }
}
