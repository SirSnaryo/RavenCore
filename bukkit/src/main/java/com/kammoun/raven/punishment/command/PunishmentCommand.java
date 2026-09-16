package com.kammoun.raven.punishment.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.punishment.PunishmentDurationParser;
import com.kammoun.raven.punishment.PunishmentService;
import com.kammoun.raven.punishment.model.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

public final class PunishmentCommand extends KCommand {

    private final PunishmentService service;
    private final Mode mode;

    public PunishmentCommand(String name, String permission, PunishmentService service, Mode mode) {
        super(name, permission, false);
        this.service = service;
        this.mode = mode;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        switch (mode) {
            case BAN -> punish(sender, args, PunishmentType.BAN);
            case MUTE -> punish(sender, args, PunishmentType.MUTE);
            case UNBAN -> pardon(sender, args, PunishmentType.BAN);
            case UNMUTE -> pardon(sender, args, PunishmentType.MUTE);
            case KICK -> kick(sender, args);
            case WARN -> punish(sender, args, PunishmentType.WARN);
            case HISTORY -> history(sender, args);
            case PUNISH -> punishRouter(sender, args);
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (mode == Mode.PUNISH && args.length == 2) {
            return List.of("ban", "mute", "kick", "warn", "history").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if ((mode == Mode.BAN || mode == Mode.MUTE || mode == Mode.PUNISH) && args.length == (mode == Mode.PUNISH ? 3 : 2)) {
            return List.of("1h", "1d", "7d", "30d", "perm");
        }
        return List.of();
    }

    private void punishRouter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/punish <player> <ban|mute|kick> [duration] [reason]");
            return;
        }
        String[] rest = new String[args.length - 1];
        rest[0] = args[0];
        if (args.length > 2) {
            System.arraycopy(args, 2, rest, 1, args.length - 2);
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "ban" -> punish(sender, rest, PunishmentType.BAN);
            case "mute" -> punish(sender, rest, PunishmentType.MUTE);
            case "kick" -> kick(sender, rest);
            case "warn" -> punish(sender, rest, PunishmentType.WARN);
            case "history" -> history(sender, rest);
            default -> usage(sender, "/punish <player> <ban|mute|kick> [duration] [reason]");
        }
    }

    private void punish(CommandSender sender, String[] args, PunishmentType type) {
        if (args.length < 1) {
            usage(sender, "/" + getCommandName() + " <player> [duration] [reason]");
            return;
        }
        ParsedPunishment parsed = parse(args);
        service.punish(sender, args[0], type, parsed.durationMillis(), parsed.reason());
    }

    private void pardon(CommandSender sender, String[] args, PunishmentType type) {
        if (args.length < 1) {
            usage(sender, "/" + getCommandName() + " <player>");
            return;
        }
        service.pardon(sender, args[0], type);
    }

    private void kick(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "/kick <player> [reason]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatFormatter.color("&#D90429Player is not online on this server."));
            return;
        }
        String reason = args.length <= 1 ? "No reason specified." : String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        service.kick(sender, target, reason);
    }

    private void history(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "/history <player>");
            return;
        }
        service.history(sender, args[0]);
    }

    private ParsedPunishment parse(String[] args) {
        Long duration = null;
        int reasonStart = 1;
        if (args.length >= 2) {
            OptionalLong parsed = PunishmentDurationParser.parseMillis(args[1]);
            if (parsed.isPresent()) {
                duration = parsed.getAsLong();
                reasonStart = 2;
            } else if (isPermanentWord(args[1])) {
                reasonStart = 2;
            }
        }
        String reason = args.length <= reasonStart
                ? "No reason specified."
                : String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length));
        return new ParsedPunishment(duration, reason);
    }

    private boolean isPermanentWord(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("perm") || lower.equals("permanent") || lower.equals("forever");
    }

    private void usage(CommandSender sender, String usage) {
        sender.sendMessage(ChatFormatter.color("&#7A7A7AUsage: &#FFFFFF" + usage));
    }

    public enum Mode {
        BAN,
        MUTE,
        UNBAN,
        UNMUTE,
        KICK,
        WARN,
        HISTORY,
        PUNISH
    }

    private record ParsedPunishment(Long durationMillis, String reason) {
    }
}
