package com.kammoun.raven.staffmode.gui;

import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ClickAction;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class StaffMenu extends KMenu {

    private final InvSeeService invSeeService;

    public StaffMenu(JavaPlugin plugin, InvSeeService invSeeService) {
        super(plugin, "staffmode/staff-menu.yml");
        this.invSeeService = invSeeService;
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        ConfigurationSection items = config.getConfigurationSection("Items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) continue;
            MenuItem item = ItemReader.readMenuItem(section, plugin);
            if (item == null || !item.hasPermission(player)) continue;
            String action = section.getString("action", key).toLowerCase(Locale.ROOT);
            item.onClick(event -> handleAction(player, event, action, item.getAction()));
            setItem(item);
        }
    }

    private void handleAction(Player player, InventoryClickEvent event, String action, ClickAction clickAction) {
        switch (action) {
            case "close" -> player.closeInventory();
            case "players" -> new StaffPlayerMenu(plugin, invSeeService, StaffPlayerMenu.View.OVERVIEW).open(player);
            case "inventory" -> new StaffPlayerMenu(plugin, invSeeService, StaffPlayerMenu.View.INVENTORY).open(player);
            case "punishments" -> new StaffPlayerMenu(plugin, invSeeService, StaffPlayerMenu.View.PUNISHMENTS).open(player);
            case "history" -> new StaffPlayerMenu(plugin, invSeeService, StaffPlayerMenu.View.HISTORY).open(player);
            case "economy-logs" -> runPlayerCommand(player, "eco logs");
            case "leaderboard" -> runPlayerCommand(player, "leaderboard");
            case "stats" -> runPlayerCommand(player, "stats " + player.getName());
            case "spectator" -> runPlayerCommand(player, player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    ? "gamemode survival"
                    : "gamemode spectator");
            default -> runConfigured(player, clickAction);
        }
    }

    private void runConfigured(Player player, ClickAction action) {
        if (action == null || action.isEmpty()) return;
        player.closeInventory();
        for (String command : action.commands()) {
            String parsed = command.replace("%player%", player.getName());
            if (parsed.startsWith("/")) parsed = parsed.substring(1);
            if (action.type() == ClickAction.Type.CONSOLE) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } else {
                player.performCommand(parsed);
            }
        }
    }

    private void runPlayerCommand(Player player, String command) {
        player.closeInventory();
        player.performCommand(ChatFormatter.strip(command));
    }
}
