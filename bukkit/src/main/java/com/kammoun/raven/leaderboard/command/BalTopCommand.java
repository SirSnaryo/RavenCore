package com.kammoun.raven.leaderboard.command;

import com.kammoun.raven.leaderboard.LeaderboardModule;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BalTopCommand extends KCommand {
    private final LeaderboardModule module;

    public BalTopCommand(LeaderboardModule module) {
        super("baltop", "raven.leaderboard", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            module.openMenu(player);
            return;
        }
        module.openPage(player, LeaderboardType.MONEY, 1);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }

}
