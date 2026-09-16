package com.kammoun.raven.staffmode.gui;

import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public final class StaffPlayerDetailMenu extends KMenu {

    private final InvSeeService invSeeService;
    private final UUID targetId;
    private final String targetName;
    private final StaffPlayerMenu.View parentView;

    public StaffPlayerDetailMenu(JavaPlugin plugin, InvSeeService invSeeService, Player target) {
        this(plugin, invSeeService, target, StaffPlayerMenu.View.OVERVIEW);
    }

    public StaffPlayerDetailMenu(JavaPlugin plugin, InvSeeService invSeeService, Player target, StaffPlayerMenu.View parentView) {
        super(plugin, fileName(parentView));
        this.invSeeService = invSeeService;
        this.targetId = target.getUniqueId();
        this.targetName = target.getName();
        this.parentView = parentView == null ? StaffPlayerMenu.View.OVERVIEW : parentView;
    }

    public StaffPlayerDetailMenu(JavaPlugin plugin, InvSeeService invSeeService, OfflinePlayer target, StaffPlayerMenu.View parentView) {
        this(plugin, invSeeService, target, target.getName(), parentView);
    }

    public StaffPlayerDetailMenu(JavaPlugin plugin, InvSeeService invSeeService, OfflinePlayer target,
                                 String targetName, StaffPlayerMenu.View parentView) {
        super(plugin, fileName(parentView));
        this.invSeeService = invSeeService;
        this.targetId = target.getUniqueId();
        this.targetName = targetName == null || targetName.isBlank() ? "unknown" : targetName;
        this.parentView = parentView == null ? StaffPlayerMenu.View.OVERVIEW : parentView;
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        Player target = Bukkit.getPlayer(targetId);

        ConfigurationSection items = config.getConfigurationSection("Items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) continue;
            MenuItem raw = ItemReader.readMenuItem(section, plugin);
            if (raw == null || !raw.hasPermission(player)) continue;
            ItemStack rendered = PlaceholderHelper.parsePlaceholders(raw.getItemStack(), placeholders(target));
            MenuItem item = MenuItem.of(new ItemBuilder(rendered))
                    .slot(raw.getSlot())
                    .permission(raw.getPermission())
                    .onClick(event -> handle(player, target, section.getString("action", key), event));
            setItem(item);
        }
    }

    private void handle(Player viewer, Player target, String rawAction, InventoryClickEvent event) {
        String action = rawAction == null ? "" : rawAction.toLowerCase(Locale.ROOT);
        switch (action) {
            case "close" -> viewer.closeInventory();
            case "back" -> new StaffPlayerMenu(plugin, invSeeService, parentView).open(viewer);
            case "overview" -> new StaffPlayerDetailMenu(plugin, invSeeService,
                    Bukkit.getOfflinePlayer(targetId), targetName, StaffPlayerMenu.View.OVERVIEW).open(viewer,
                    "%player%", targetName);
            case "inventory-menu" -> new StaffPlayerDetailMenu(plugin, invSeeService,
                    Bukkit.getOfflinePlayer(targetId), targetName, StaffPlayerMenu.View.INVENTORY).open(viewer,
                    "%player%", targetName);
            case "punishments-menu" -> new StaffPlayerDetailMenu(plugin, invSeeService,
                    Bukkit.getOfflinePlayer(targetId), targetName, StaffPlayerMenu.View.PUNISHMENTS).open(viewer,
                    "%player%", targetName);
            case "history-menu" -> new StaffPlayerDetailMenu(plugin, invSeeService,
                    Bukkit.getOfflinePlayer(targetId), targetName, StaffPlayerMenu.View.HISTORY).open(viewer,
                    "%player%", targetName);
            case "invsee" -> {
                viewer.closeInventory();
                if (target == null) {
                    invSeeService.openOffline(viewer, targetName, true);
                } else {
                    invSeeService.open(viewer, target, true);
                }
            }
            case "enderchest" -> {
                viewer.closeInventory();
                if (target == null) {
                    invSeeService.openOfflineEnderChest(viewer, targetName, true);
                } else {
                    invSeeService.openEnderChest(viewer, target, true);
                }
            }
            case "snapshots" -> {
                viewer.closeInventory();
                invSeeService.openSnapshots(viewer, targetName);
            }
            case "ban", "mute", "kick", "warn" -> openPunishmentConfirm(viewer, target, action);
            case "history", "homes" -> runConfiguredCommand(viewer, target, action);
            default -> {
            }
        }
    }

    private void openPunishmentConfirm(Player viewer, Player target, String action) {
        String command = config.getString("Actions." + action, "");
        if (command.isBlank()) return;
        command = command.replace("%player%", targetName);
        new StaffPunishmentConfirmMenu(plugin, invSeeService, targetId, targetName, parentView, action, command).open(viewer,
                "%player%", targetName,
                "%action%", action);
    }

    private void runConfiguredCommand(Player viewer, Player target, String action) {
        String command = config.getString("Actions." + action, "");
        if (command.isBlank()) return;
        viewer.closeInventory();
        command = command.replace("%player%", targetName);
        if (command.startsWith("/")) command = command.substring(1);
        viewer.performCommand(command);
    }

    private String[] placeholders(Player target) {
        boolean online = target != null;
        return new String[]{
                "%player%", targetName,
                "%status%", online ? "Online" : "Offline",
                "%world%", online ? target.getWorld().getName() : "Saved data",
                "%ping%", online ? target.getPing() + "ms" : "n/a",
                "%gamemode%", online ? target.getGameMode().name() : "n/a",
                "%health%", online ? String.format("%.0f", target.getHealth()) : "n/a",
                "%food%", online ? String.valueOf(target.getFoodLevel()) : "n/a",
                "%x%", online ? String.format("%.0f", target.getLocation().getX()) : "-",
                "%y%", online ? String.format("%.0f", target.getLocation().getY()) : "-",
                "%z%", online ? String.format("%.0f", target.getLocation().getZ()) : "-"
        };
    }

    private static String fileName(StaffPlayerMenu.View view) {
        StaffPlayerMenu.View selected = view == null ? StaffPlayerMenu.View.OVERVIEW : view;
        return switch (selected) {
            case OVERVIEW -> "staffmode/player-menu.yml";
            case INVENTORY -> "staffmode/player-inventory-menu.yml";
            case PUNISHMENTS -> "staffmode/player-punishments-menu.yml";
            case HISTORY -> "staffmode/player-history-menu.yml";
        };
    }
}
