package com.kammoun.raven.stats.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.stats.model.StatsType;
import com.kammoun.raven.stats.service.StatsService;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class StatsManagerCommand extends KCommand {
    private final StatsService service;
    private final Runnable reload;

    public StatsManagerCommand(StatsService service, Runnable reload) {
        super("statsmanager", "raven.stats.admin", false);
        this.service = service;
        this.reload = reload;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reload.run();
            sender.sendMessage(ChatFormatter.color("&aStats configuration reloaded."));
            return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            complete(sender, service.reset(args[1]), "reset");
            return;
        }
        if (args.length != 4) {
            sender.sendMessage(ChatFormatter.color("&cUsage: /statsmanager <set|add|remove> <player> <stat> <amount>"));
            sender.sendMessage(ChatFormatter.color("&cUsage: /statsmanager <reset|reload> [player]"));
            return;
        }
        String operation = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("set", "add", "remove").contains(operation)) {
            sender.sendMessage(ChatFormatter.color("&cUnknown operation."));
            return;
        }
        StatsType type = StatsType.parse(args[2]);
        if (type == null) {
            sender.sendMessage(ChatFormatter.color("&cUnknown stat."));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException error) {
            sender.sendMessage(ChatFormatter.color("&cAmount must be a number."));
            return;
        }
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            sender.sendMessage(ChatFormatter.color("&cAmount must be a finite positive number."));
            return;
        }
        complete(sender, service.mutate(args[1], type, operation, amount), operation);
    }

    private void complete(CommandSender sender, java.util.concurrent.CompletableFuture<Boolean> future, String action) {
        future.whenComplete((changed, error) -> SchedulerUtil.run(() -> {
            if (error != null) sender.sendMessage(ChatFormatter.color("&cStats database operation failed."));
            else if (!changed) sender.sendMessage(ChatFormatter.color("&cPlayer stats were not found."));
            else sender.sendMessage(ChatFormatter.color("&aPlayer stats " + action + " completed."));
        }));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("set", "add", "remove", "reset", "reload");
        if (args.length == 3 && List.of("set", "add", "remove").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Arrays.stream(StatsType.values()).map(StatsType::column).toList();
        }
        return List.of();
    }
}
