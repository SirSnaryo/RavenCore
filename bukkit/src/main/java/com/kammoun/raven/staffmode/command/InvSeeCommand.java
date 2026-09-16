package com.kammoun.raven.staffmode.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.staffmode.StaffModeService;
import com.kammoun.raven.staffmode.gui.StaffPlayerDetailMenu;
import com.kammoun.raven.staffmode.gui.StaffPlayerMenu;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class InvSeeCommand extends KCommand {

    private final StaffModeService staffModeService;
    private final InvSeeService invSeeService;
    private final JavaPlugin plugin;

    public InvSeeCommand(JavaPlugin plugin, StaffModeService staffModeService, InvSeeService invSeeService) {
        super("invsee", "raven.staffmode.invsee.view", true);
        this.plugin = plugin;
        this.staffModeService = staffModeService;
        this.invSeeService = invSeeService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player viewer = (Player) sender;
        if (!staffModeService.isEnabled(viewer) && !viewer.hasPermission("raven.staffmode.bypass-gate")) {
            staffModeService.deny(viewer);
            return;
        }
        if (args.length == 0) {
            viewer.sendMessage(ChatFormatter.color("&#7A7A7AUsage: &#FFFFFF/invsee <player> [edit]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        boolean edit = args.length >= 2 && args[1].equalsIgnoreCase("edit");
        if (target == null) {
            new StaffPlayerDetailMenu(plugin, invSeeService,
                    Bukkit.getOfflinePlayer(args[0]), args[0], StaffPlayerMenu.View.INVENTORY).open(viewer,
                    "%player%", args[0]);
            return;
        }
        invSeeService.open(viewer, target, edit);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (!staffModeService.isEnabled(player) && !sender.hasPermission("raven.staffmode.bypass-gate")) {
            return List.of();
        }
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && sender.hasPermission("raven.staffmode.invsee.edit")) {
            return List.of("edit");
        }
        return List.of();
    }
}
