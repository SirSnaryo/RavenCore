package com.kammoun.raven.staffmode.listener;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.staffmode.StaffModeService;
import com.kammoun.raven.staffmode.invsee.InvSeeHolder;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class StaffModeListener implements Listener {

    private final StaffModeService service;
    private final InvSeeService invSeeService;

    public StaffModeListener(StaffModeService service, InvSeeService invSeeService) {
        this.service = service;
        this.invSeeService = invSeeService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InvSeeHolder holder) {
            if (holder.access().shouldCancelClick(true)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage(ChatFormatter.color("&#D90429You do not have permission to edit this inventory."));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvSeeHolder holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (holder.access().shouldCancelDrag(touchesTop)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof InvSeeHolder holder) {
            invSeeService.applyAndClose(holder);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (service.isEnabled(event.getPlayer())) {
            service.forget(event.getPlayer());
        }
    }
}
