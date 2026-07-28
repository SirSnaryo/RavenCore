package com.kammoun.raven.mythictools.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.mythictools.model.MythicToolDefinition;
import com.kammoun.raven.mythictools.model.MythicToolType;
import com.kammoun.raven.mythictools.util.DurationParser;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MythicToolsCommand extends KCommand {

    private final MythicToolsModule module;

    public MythicToolsCommand(MythicToolsModule module) {
        super("mythictools", "raven.mythictools.admin", false);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "list" -> list(sender);
            case "reload" -> {
                module.reload();
                module.send(sender, "command.reload");
            }
            default -> help(sender);
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            module.send(sender, "command.give-usage");
            return;
        }
        Player target = module.getApi().getServer().getPlayerExact(args[1]);
        if (target == null) {
            module.send(sender, "command.player-not-found");
            return;
        }
        MythicToolType type;
        try {
            type = MythicToolType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            module.send(sender, "command.invalid-tool");
            return;
        }
        MythicToolDefinition definition = module.config().tool(type);
        if (definition == null) {
            module.send(sender, "command.invalid-tool");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                module.send(sender, "command.invalid-amount");
                return;
            }
        }
        for (int i = 0; i < amount; i++) {
            ItemStack item = module.items().create(definition, false);
            target.getInventory().addItem(item).values().forEach(leftover ->
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            module.expiry().register(target, item);
        }
        module.send(sender, "command.give-success",
                "%amount%", Integer.toString(amount), "%tool%", type.name(), "%player%", target.getName());
        module.send(target, "command.give-received",
                "%amount%", Integer.toString(amount), "%tool%", type.name());
    }



    private void list(CommandSender sender) {
        module.send(sender, "command.list-header");
        module.config().tools().forEach((type, definition) ->
                module.send(sender, "command.list-entry", "%tool%", type.name(),
                        "%duration%", DurationParser.format(definition.durationMillis())));
    }

    private void help(CommandSender sender) {
        module.send(sender, "command.help");
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "list", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(module.getApi().getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.stream(MythicToolType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList(),
                    args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
