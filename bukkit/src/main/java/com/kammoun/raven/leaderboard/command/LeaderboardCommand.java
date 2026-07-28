package com.kammoun.raven.leaderboard.command;

import com.kammoun.raven.leaderboard.LeaderboardModule;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public final class LeaderboardCommand extends KCommand {
    private final LeaderboardModule module;

    public LeaderboardCommand(LeaderboardModule module) {
        super("leaderboard", "raven.leaderboard", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            module.openMenu(player);
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("refresh")
                && player.hasPermission("raven.leaderboard.admin")) {
            module.getService().refresh();
            player.sendMessage(ChatFormatter.color("&aLeaderboard refresh queued."));
            return;
        }
        LeaderboardType type = LeaderboardType.parse(args[0]);
        if (type == null || !module.getService().enabled(type)) {
            player.sendMessage(ChatFormatter.color("&cUnknown leaderboard type."));
            return;
        }
        module.openPage(player, type, 1);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> types = Arrays.stream(LeaderboardType.values()).map(LeaderboardType::key).toList();
            if (sender.hasPermission("raven.leaderboard.admin")) {
                return java.util.stream.Stream.concat(types.stream(), java.util.stream.Stream.of("refresh")).toList();
            }
            return types;
        }
        return List.of();
    }
}
