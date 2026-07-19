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
                sender.sendMessage(ChatFormatter.color("&cOnly players can toggle scoreboards."));
                return;
            }

            boolean enabled = module.getService().toggle(player);
            player.sendMessage(ChatFormatter.color(enabled
                    ? "&aScoreboard enabled."
                    : "&cScoreboard disabled."));
            return;
        }

        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("enable")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&cOnly players can toggle scoreboards."));
                return;
            }
            module.getService().setEnabled(player, true);
            player.sendMessage(ChatFormatter.color("&aScoreboard enabled."));
            return;
        }

        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("disable")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&cOnly players can toggle scoreboards."));
                return;
            }
            module.getService().setEnabled(player, false);
            player.sendMessage(ChatFormatter.color("&cScoreboard disabled."));
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatFormatter.color("&eScoreboards: &f" + String.join("&7, &f", module.getService().boardNames())));
            return;
        }

        if (args[0].equalsIgnoreCase("current")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&cOnly players have a current scoreboard."));
                return;
            }
            String current = module.getService().currentBoard(player).orElse("none");
            String forced = module.getService().forcedBoard(player).orElse("auto");
            player.sendMessage(ChatFormatter.color("&eCurrent: &f" + current + " &8| &eMode: &f" + forced));
            return;
        }

        if (args[0].equalsIgnoreCase("select")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&cOnly players can select a scoreboard."));
                return;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatFormatter.color("&cUsage: /scoreboard select <board>"));
                return;
            }
            setBoard(sender, player, args[1]);
            return;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatFormatter.color("&cOnly players can reset their scoreboard."));
                return;
            }
            module.getService().clearBoard(player);
            player.sendMessage(ChatFormatter.color("&aScoreboard selection reset to automatic."));
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

        if (args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("raven.scoreboard.admin")) {
                return;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatFormatter.color("&cUsage: /scoreboard set <player> <board>"));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatFormatter.color("&cPlayer is not online."));
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
                sender.sendMessage(ChatFormatter.color("&cUsage: /scoreboard clear <player>"));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatFormatter.color("&cPlayer is not online."));
                return;
            }
            module.getService().clearBoard(target);
            sender.sendMessage(ChatFormatter.color("&aScoreboard selection reset for &f" + target.getName() + "&a."));
            return;
        }

        sender.sendMessage(ChatFormatter.color("&e/scoreboard toggle &7- Toggle your scoreboard"));
        sender.sendMessage(ChatFormatter.color("&e/scoreboard on|off &7- Enable or disable your scoreboard"));
        sender.sendMessage(ChatFormatter.color("&e/scoreboard list &7- List configured boards"));
        sender.sendMessage(ChatFormatter.color("&e/scoreboard current &7- Show your active board"));
        sender.sendMessage(ChatFormatter.color("&e/scoreboard select <board> &7- Pin a board to yourself"));
        sender.sendMessage(ChatFormatter.color("&e/scoreboard reset &7- Return to automatic selection"));
        if (sender.hasPermission("raven.scoreboard.admin")) {
            sender.sendMessage(ChatFormatter.color("&e/scoreboard reload &7- Reload scoreboard config"));
            sender.sendMessage(ChatFormatter.color("&e/scoreboard set <player> <board> &7- Pin a board to a player"));
            sender.sendMessage(ChatFormatter.color("&e/scoreboard clear <player> &7- Clear a player's pinned board"));
        }
    }

    private void setBoard(CommandSender sender, Player target, String board) {
        if (!sender.hasPermission("raven.scoreboard.admin") && !module.getService().canUseBoard(target, board)) {
            sender.sendMessage(ChatFormatter.color("&cYou cannot use that scoreboard."));
            return;
        }

        if (!module.getService().setBoard(target, board)) {
            sender.sendMessage(ChatFormatter.color("&cUnknown scoreboard. Use /scoreboard list."));
            return;
        }

        sender.sendMessage(ChatFormatter.color("&aScoreboard set to &f" + board + "&a for &f" + target.getName() + "&a."));
        if (!sender.equals(target)) {
            target.sendMessage(ChatFormatter.color("&aYour scoreboard was set to &f" + board + "&a."));
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
