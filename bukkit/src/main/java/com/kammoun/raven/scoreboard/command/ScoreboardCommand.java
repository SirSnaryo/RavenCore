package com.kammoun.raven.scoreboard.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.scoreboard.ScoreboardModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardCommand extends KCommand {

    private final ScoreboardModule module;

    public ScoreboardCommand(ScoreboardModule module) {
        super("scoreboard", "raven.scoreboard", false);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&cOnly players can toggle scoreboards."));
                return;
            }

            boolean enabled = module.getService().toggle(player);
            player.sendMessage(ChatFormatter.color(enabled
                    ? "&aScoreboard enabled."
                    : "&cScoreboard disabled."));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("raven.scoreboard.admin")) {
                return;
            }
            module.reload();
            sender.sendMessage(ChatFormatter.color("&aScoreboard configuration reloaded."));
            return;
        }

        sender.sendMessage(ChatFormatter.color("&e/scoreboard toggle &7- Toggle your scoreboard"));
        if (sender.hasPermission("raven.scoreboard.admin")) {
            sender.sendMessage(ChatFormatter.color("&e/scoreboard reload &7- Reload scoreboard config"));
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            values.add("toggle");
            if (sender.hasPermission("raven.scoreboard.admin")) {
                values.add("reload");
            }
            return values.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
