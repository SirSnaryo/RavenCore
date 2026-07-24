package com.kammoun.raven.team.command;

import com.kammoun.raven.team.TeamModule;
import com.kammoun.raven.team.model.TeamSnapshot;
import com.kammoun.raven.team.service.TeamService;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TeamCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("create", "invite", "accept", "deny",
            "leave", "kick", "disband", "home", "sethome", "deletehome", "chat", "invites", "pvp");
    private final TeamModule module;

    public TeamCommand(TeamModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is only available to players.");
            return true;
        }
        if (args.length == 0) {
            module.openMenu(player);
            return true;
        }
        TeamService service = module.getService();
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) module.getMessages().send(player, "usage-create");
                else service.create(player, args[1]);
            }
            case "invite" -> {
                if (args.length < 2) module.getMessages().send(player, "player-offline");
                else service.invite(player, args[1]);
            }
            case "accept" -> service.accept(player, args.length > 1 ? args[1] : null);
            case "deny" -> service.deny(player, args.length > 1 ? args[1] : null);
            case "leave" -> service.leave(player);
            case "kick" -> {
                if (args.length < 2) module.getMessages().send(player, "not-member");
                else service.kick(player, args[1]);
            }
            case "disband" -> module.openDisbandConfirmation(player);
            case "home" -> service.home(player);
            case "sethome" -> service.setHome(player);
            case "deletehome", "delhome" -> service.deleteHome(player);
            case "chat" -> service.toggleChat(player);
            case "invites" -> service.toggleInvites(player);
            case "pvp" -> service.togglePvp(player);
            default -> module.getMessages().send(player, "usage");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            if (args[0].equalsIgnoreCase("invite")) {
                return filter(new ArrayList<>(module.getApi().getNetworkPlayerManager().getNetworkPlayerNames()), args[1]);
            }
            if (sender instanceof Player player) {
                return filter(module.getService().cached(player.getUniqueId()).stream()
                        .flatMap(team -> team.members().stream()).map(TeamSnapshot.Member::lastName).toList(), args[1]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase().startsWith(lower)) result.add(value);
        return result;
    }
}
