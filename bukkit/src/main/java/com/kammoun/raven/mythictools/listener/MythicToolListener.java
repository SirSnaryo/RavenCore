package com.kammoun.raven.mythictools.listener;

import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.mythictools.ability.MythicToolAbilities;
import com.kammoun.raven.mythictools.item.MythicItemData;
import com.kammoun.raven.mythictools.model.MythicToolDefinition;
import com.kammoun.raven.mythictools.model.MythicToolType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public final class MythicToolListener implements Listener {

    private final MythicToolsModule module;
    private final MythicToolAbilities abilities;

    public MythicToolListener(MythicToolsModule module) {
        this.module = module;
        this.abilities = new MythicToolAbilities(module);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer(), () -> module.expiry().discover(event.getPlayer(), event.getPlayer().getInventory()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.expiry().forget(event.getPlayer());
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!MythicItemData.timed(item)) return;
        if (MythicItemData.expired(item, System.currentTimeMillis())) {
            event.setCancelled(true);
            event.getItem().remove();
            module.send(event.getPlayer(), "tool.expired", "%tool%",
                    item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                            ? item.getItemMeta().getDisplayName() : item.getType().name());
            return;
        }
        module.expiry().register(event.getPlayer(), item);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        later(event.getPlayer(), () -> module.expiry().validate(
                event.getPlayer(), event.getPlayer().getInventory(), event.getNewSlot(), false));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        later(event.getPlayer(), () -> module.expiry().discover(event.getPlayer(), event.getPlayer().getInventory()));
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        module.expiry().discover(player, event.getInventory());
        module.expiry().discover(player, player.getInventory());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            org.bukkit.inventory.Inventory clicked = event.getClickedInventory();
            int slot = event.getSlot();
            later(player, () -> module.expiry().discoverSlot(player, clicked, slot));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
            org.bukkit.inventory.Inventory bottom = event.getView().getBottomInventory();
            int topSize = top.getSize();
            java.util.List<SlotRef> slots = event.getRawSlots().stream()
                    .map(raw -> raw < topSize
                            ? new SlotRef(top, raw)
                            : new SlotRef(bottom, event.getView().convertSlot(raw)))
                    .toList();
            later(player, () -> slots.forEach(ref ->
                    module.expiry().discoverSlot(player, ref.inventory(), ref.slot())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack item = player.getInventory().getItem(slot);
        MythicToolType type = MythicItemData.type(item);
        if (type == null || !module.expiry().validate(player, player.getInventory(), slot, true)) return;
        Block block = event.getBlock();
        switch (type) {
            case DRILL -> {
                if (module.config().mineable(block.getType())) abilities.drill(player, block);
            }
            case TREE_CHOPPER -> {
                if (module.config().log(block.getType())) abilities.treeChopper(player, block);
            }
            case MULTI_TOOL -> {
                if (module.config().log(block.getType())) abilities.treeChopper(player, block);
                else if (module.config().diggable(block.getType())) abilities.shovel(player, block);
                else if (module.config().mineable(block.getType())) abilities.drill(player, block);
            }
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        int slot = player.getInventory().getHeldItemSlot();
        MythicToolType type = MythicItemData.type(player.getInventory().getItem(slot));
        if (type == null) return;
        if (!module.expiry().validate(player, player.getInventory(), slot, true)) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && type == MythicToolType.MULTI_TOOL) {
            switchMainHandMaterial(player, abilities.appropriateMaterial(event.getClickedBlock()));
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && (type == MythicToolType.SELL_AXE || type == MythicToolType.MULTI_TOOL)
                && event.getClickedBlock().getState() instanceof org.bukkit.block.Chest) {
            event.setCancelled(true);
            abilities.sellChest(player, event.getClickedBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(PlayerItemDamageEvent event) {
        MythicToolType type = MythicItemData.type(event.getItem());
        MythicToolDefinition definition = type == null ? null : module.config().tool(type);
        if (definition != null && definition.unbreakable()) event.setCancelled(true);
    }

    private void switchMainHandMaterial(Player player, org.bukkit.Material material) {
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getType() == material) return;
        ItemStack replacement = current.withType(material);
        player.getInventory().setItemInMainHand(replacement);
    }

    private void later(Player player, Runnable runnable) {
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, runnable), 1L);
    }

    private record SlotRef(org.bukkit.inventory.Inventory inventory, int slot) {}
}
