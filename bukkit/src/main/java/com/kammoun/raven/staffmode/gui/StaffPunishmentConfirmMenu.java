package com.kammoun.raven.staffmode.gui;

import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public final class StaffPunishmentConfirmMenu extends KMenu {

    private final InvSeeService invSeeService;
    private final UUID targetId;
    private final String targetName;
    private final StaffPlayerMenu.View parentView;
    private final String action;
    private final String command;

    public StaffPunishmentConfirmMenu(JavaPlugin plugin, InvSeeService invSeeService,
                                      Player target, String action, String command) {
        this(plugin, invSeeService, target.getUniqueId(), target.getName(), StaffPlayerMenu.View.OVERVIEW, action, command);
    }

    public StaffPunishmentConfirmMenu(JavaPlugin plugin, InvSeeService invSeeService,
                                      UUID targetId, String targetName, StaffPlayerMenu.View parentView,
                                      String action, String command) {
        super(plugin, "staffmode/punishment-confirm.yml");
        this.invSeeService = invSeeService;
        this.targetId = targetId;
        this.targetName = targetName;
        this.parentView = parentView == null ? StaffPlayerMenu.View.OVERVIEW : parentView;
        this.action = action;
        this.command = command;
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        ConfigurationSection items = config.getConfigurationSection("Items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) continue;
            MenuItem raw = ItemReader.readMenuItem(section, plugin);
            if (raw == null || !raw.hasPermission(player)) continue;
            ItemStack rendered = PlaceholderHelper.parsePlaceholders(raw.getItemStack(),
                    "%player%", targetName,
                    "%action%", action,
                    "%command%", "/" + command);
            MenuItem item = MenuItem.of(new ItemBuilder(rendered))
                    .slot(raw.getSlot())
                    .permission(raw.getPermission())
                    .onClick(event -> handle(player, section.getString("action", key)));
            setItem(item);
        }
    }

    private void handle(Player viewer, String rawAction) {
        String selected = rawAction == null ? "" : rawAction.toLowerCase(Locale.ROOT);
        switch (selected) {
            case "confirm" -> {
                viewer.closeInventory();
                viewer.performCommand(command.startsWith("/") ? command.substring(1) : command);
            }
            case "back" -> {
                Player target = Bukkit.getPlayer(targetId);
                if (target == null) {
                    new StaffPlayerDetailMenu(plugin, invSeeService, Bukkit.getOfflinePlayer(targetId), targetName, parentView).open(viewer,
                            "%player%", targetName);
                    return;
                }
                new StaffPlayerDetailMenu(plugin, invSeeService, target, parentView).open(viewer,
                        "%player%", target.getName());
            }
            case "cancel", "close" -> viewer.closeInventory();
            default -> {
            }
        }
    }
}
