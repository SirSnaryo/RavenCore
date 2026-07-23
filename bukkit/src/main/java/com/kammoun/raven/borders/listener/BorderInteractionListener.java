package com.kammoun.raven.borders.listener;

import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.library.border.BorderInteractionPolicy;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class BorderInteractionListener implements Listener {

    private final BorderModule module;

    public BorderInteractionListener(BorderModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        protect(event.getBlock(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        protect(event.getBlockPlaced(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            protect(clickedBlock, event.getPlayer(), event);
        }
    }

    private void protect(Block block, Player player, Cancellable event) {
        GridCell cell = module.getBorderConfig().cellFor(block.getWorld().getEnvironment());
        if (cell == null || !BorderInteractionPolicy.isOutside(
                cell.minX(), cell.minZ(), cell.maxX(), cell.maxZ(), block.getX(), block.getZ())) {
            return;
        }

        event.setCancelled(true);
        String denyMessage = module.getBorderConfig().denyMessage();
        if (denyMessage != null && !denyMessage.isBlank()) {
            player.sendMessage(ChatFormatter.color(denyMessage));
        }
    }
}
