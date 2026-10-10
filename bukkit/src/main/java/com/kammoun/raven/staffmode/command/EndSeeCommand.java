package com.kammoun.raven.staffmode.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.staffmode.StaffModeService;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class EndSeeCommand extends KCommand {

    private final StaffModeService staffModeService;
    private final InvSeeService invSeeService;

    public EndSeeCommand(String name, StaffModeService staffModeService, InvSeeService invSeeService) {
        super(name, "raven.staffmode.enderchest.view", true);
        this.staffModeService = staffModeService;
        this.invSeeService = invSeeService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player viewer = (Player) sender;
        if (!staffModeService.isEnabled(viewer) && !viewer.hasPermission("raven.staffmode")) {
            viewer.sendMessage(ChatFormatter.color("&#D90429Enable Staff Mode first."));
            return;
        }
        if (args.length == 0) {
            viewer.sendMessage(ChatFormatter.color("&#7A7A7AUsage: &#FFFFFF/" + getCommandName() + " <player> [edit]"));
            return;
        }
        boolean edit = args.length >= 2 && args[1].equalsIgnoreCase("edit");
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null) {
            invSeeService.openEnderChest(viewer, target, edit);
            return;
        }
        invSeeService.openOfflineEnderChest(viewer, args[0], edit);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (!staffModeService.isEnabled(player) && !player.hasPermission("raven.staffmode")) {
            return List.of();
        }
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && sender.hasPermission("raven.staffmode.enderchest.edit")) {
            return List.of("edit");
        }
        return List.of();
    }
}
