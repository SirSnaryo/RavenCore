package com.kammoun.raven.staffmode.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.staffmode.StaffModeService;
import com.kammoun.raven.staffmode.gui.StaffMenu;
import com.kammoun.raven.staffmode.gui.StaffPlayerMenu;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class StaffCommand extends KCommand {

    private final StaffModeService service;
    private final StaffMenu menu;
    private final InvSeeService invSeeService;
    private final JavaPlugin plugin;

    public StaffCommand(StaffModeService service, StaffMenu menu, InvSeeService invSeeService, JavaPlugin plugin) {
        super("staff", "raven.staffmode", true);
        this.service = service;
        this.menu = menu;
        this.invSeeService = invSeeService;
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (!service.isEnabled(player) && !player.hasPermission("raven.staffmode.bypass-gate")) {
            service.deny(player);
            return;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("players") || args[0].equalsIgnoreCase("search"))) {
            String filter = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
            new StaffPlayerMenu(plugin, invSeeService, StaffPlayerMenu.View.OVERVIEW, filter).open(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("inventory")) {
            String filter = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
            new StaffPlayerMenu(plugin, invSeeService, StaffPlayerMenu.View.INVENTORY, filter).open(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("punishments")) {
            String filter = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
            new StaffPlayerMenu(plugin, invSeeService, StaffPlayerMenu.View.PUNISHMENTS, filter).open(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("history")) {
            String filter = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
            new StaffPlayerMenu(plugin, invSeeService, StaffPlayerMenu.View.HISTORY, filter).open(player);
            return;
        }
        menu.open(player);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("players", "inventory", "punishments", "history", "search").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("players") || args[0].equalsIgnoreCase("inventory")
                || args[0].equalsIgnoreCase("punishments") || args[0].equalsIgnoreCase("history")
                || args[0].equalsIgnoreCase("search"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
