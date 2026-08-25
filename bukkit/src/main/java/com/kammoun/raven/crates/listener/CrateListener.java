package com.kammoun.raven.crates.listener;

import com.kammoun.raven.crates.config.CratesConfig;
import com.kammoun.raven.crates.gui.ConfirmCrateMenu;
import com.kammoun.raven.crates.gui.CrateEditorMenu;
import com.kammoun.raven.crates.gui.CrateMenu;
import com.kammoun.raven.crates.model.CrateDefinition;
import com.kammoun.raven.crates.service.CrateService;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CrateListener implements Listener {
    private final CratesConfig config;
    private final CrateService service;

    public CrateListener(CratesConfig config, CrateService service) {
        this.config = config;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.players().load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.players().unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        if (event.getItemInHand().getType() == Material.AIR) return;
        NBTItem nbt = new NBTItem(event.getItemInHand());
        if (!nbt.hasTag("crate_id")) return;
        CrateDefinition crate = config.crate(nbt.getString("crate_id"));
        if (crate == null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatFormatter.color(config.invalidCrateMessage()));
            return;
        }
        service.placeCrate(event.getPlayer(), event.getBlock().getLocation(), crate);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !event.getAction().isRightClick()) return;
        Location location = event.getClickedBlock().getLocation();
        if (!service.isCrate(location)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!config.openingEnabled()) {
            player.sendMessage(ChatFormatter.color(config.openingDisabledMessage()));
            return;
        }
        CrateDefinition crate = service.crateAt(location);
        if (crate == null) {
            player.sendMessage(ChatFormatter.color(config.invalidCrateMessage()));
            return;
        }
        new CrateMenu(crate, service).open(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Location location = event.getBlock().getLocation();
        if (!service.isCrate(location)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("raven.crates.admin")) return;
        if (player.getGameMode() != GameMode.CREATIVE || !player.isSneaking()) return;
        event.setDropItems(false);
        service.removeCrate(player, location);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CrateMenu menu) {
            menu.handleClick(event);
        } else if (event.getInventory().getHolder() instanceof ConfirmCrateMenu menu) {
            menu.handleClick(event);
        } else if (event.getInventory().getHolder() instanceof CrateEditorMenu menu) {
            menu.handleClick(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CrateEditorMenu menu) {
            menu.handleDrag(event);
        }
    }
}
