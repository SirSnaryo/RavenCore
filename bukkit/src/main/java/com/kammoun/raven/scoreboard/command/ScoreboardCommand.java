package com.kammoun.raven.scoreboard.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.scoreboard.ScoreboardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                sender.sendMessage(ChatFormatter.color("&#D90429Only players can toggle scoreboards."));
                return;
            }

            boolean enabled = module.getService().toggle(player);
            player.sendMessage(ChatFormatter.color(enabled
                    ? "&#33FF66Scoreboard enabled."
                    : "&#D90429Scoreboard disabled."));
            return;
        }

        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("enable")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&#D90429Only players can toggle scoreboards."));
                return;
            }
            module.getService().setEnabled(player, true);
            player.sendMessage(ChatFormatter.color("&#33FF66Scoreboard enabled."));
            return;
        }

        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("disable")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&#D90429Only players can toggle scoreboards."));
                return;
            }
            module.getService().setEnabled(player, false);
            player.sendMessage(ChatFormatter.color("&#D90429Scoreboard disabled."));
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatFormatter.color("&#FFD447Scoreboards: &#FFFFFF" + String.join("&#7A7A7A, &#FFFFFF", module.getService().boardNames())));
            return;
        }

        if (args[0].equalsIgnoreCase("current")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&#D90429Only players have a current scoreboard."));
                return;
            }
            String current = module.getService().currentBoard(player).orElse("none");
            String forced = module.getService().forcedBoard(player).orElse("auto");
            player.sendMessage(ChatFormatter.color("&#FFD447Current: &#FFFFFF" + current + " &#7A7A7A| &#FFD447Mode: &#FFFFFF" + forced));
            return;
        }

        if (args[0].equalsIgnoreCase("select")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&#D90429Only players can select a scoreboard."));
                return;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatFormatter.color("&#D90429Usage: /scoreboard select <board>"));
                return;
            }
            setBoard(sender, player, args[1]);
            return;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&#D90429Only players can reset their scoreboard."));
                return;
            }
            module.getService().clearBoard(player);
            player.sendMessage(ChatFormatter.color("&#33FF66Scoreboard selection reset to automatic."));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("raven.scoreboard.admin")) {
                return;
            }
            module.reload();
            sender.sendMessage(ChatFormatter.color("&#33FF66Scoreboard configuration reloaded."));
            return;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("raven.scoreboard.admin")) {
                return;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatFormatter.color("&#D90429Usage: /scoreboard set <player> <board>"));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatFormatter.color("&#D90429Player is not online."));
                return;
            }
            setBoard(sender, target, args[2]);
            return;
        }

        if (args[0].equalsIgnoreCase("clear")) {
            if (!sender.hasPermission("raven.scoreboard.admin")) {
                return;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatFormatter.color("&#D90429Usage: /scoreboard clear <player>"));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatFormatter.color("&#D90429Player is not online."));
                return;
            }
            module.getService().clearBoard(target);
            sender.sendMessage(ChatFormatter.color("&#33FF66Scoreboard selection reset for &#FFFFFF" + target.getName() + "&#33FF66."));
            return;
        }

        sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard toggle &#7A7A7A- Toggle your scoreboard"));
        sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard on|off &#7A7A7A- Enable or disable your scoreboard"));
        sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard list &#7A7A7A- List configured boards"));
        sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard current &#7A7A7A- Show your active board"));
        sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard select <board> &#7A7A7A- Pin a board to yourself"));
        sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard reset &#7A7A7A- Return to automatic selection"));
        if (sender.hasPermission("raven.scoreboard.admin")) {
            sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard reload &#7A7A7A- Reload scoreboard config"));
            sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard set <player> <board> &#7A7A7A- Pin a board to a player"));
            sender.sendMessage(ChatFormatter.color("&#FFD447/scoreboard clear <player> &#7A7A7A- Clear a player's pinned board"));
        }
    }

    private void setBoard(CommandSender sender, Player target, String board) {
        if (!sender.hasPermission("raven.scoreboard.admin") && !module.getService().canUseBoard(target, board)) {
            sender.sendMessage(ChatFormatter.color("&#D90429You cannot use that scoreboard."));
            return;
        }

        if (!module.getService().setBoard(target, board)) {
            sender.sendMessage(ChatFormatter.color("&#D90429Unknown scoreboard. Use /scoreboard list."));
            return;
        }

        sender.sendMessage(ChatFormatter.color("&#33FF66Scoreboard set to &#FFFFFF" + board + "&#33FF66 for &#FFFFFF" + target.getName() + "&#33FF66."));
        if (!sender.equals(target)) {
            target.sendMessage(ChatFormatter.color("&#33FF66Your scoreboard was set to &#FFFFFF" + board + "&#33FF66."));
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            values.add("toggle");
            values.add("on");
            values.add("off");
            values.add("list");
            values.add("current");
            values.add("select");
            values.add("reset");
            if (sender.hasPermission("raven.scoreboard.admin")) {
                values.add("reload");
                values.add("set");
                values.add("clear");
            }
            return values.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            return boards(args[1]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("clear"))
                && sender.hasPermission("raven.scoreboard.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender.hasPermission("raven.scoreboard.admin")) {
            return boards(args[2]);
        }
        return List.of();
    }

    private List<String> boards(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return module.getService().boardNames().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
