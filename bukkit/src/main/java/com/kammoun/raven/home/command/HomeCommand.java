package com.kammoun.raven.home.command;

import com.kammoun.raven.home.HomeModule;
import com.kammoun.raven.home.model.HomeRecord;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class HomeCommand implements CommandExecutor, TabCompleter {
    private final HomeModule module;

    public HomeCommand(HomeModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is only available to players.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "sethome" -> module.getService().set(player, args.length == 0 ? "home" : args[0]);
            case "delhome" -> {
                if (args.length == 0) module.getMessages().send(player, "delete-usage");
                else module.getService().delete(player, args[0]);
            }
            case "homes" -> module.openMenu(player);
            default -> {
                if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                    module.openMenu(player);
                } else if (args[0].equalsIgnoreCase("set")) {
                    module.getService().set(player, args.length > 1 ? args[1] : "home");
                } else if (args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("delete")) {
                    if (args.length < 2) module.getMessages().send(player, "delete-usage");
                    else module.getService().delete(player, args[1]);
                } else {
                    module.getService().teleport(player, args[0]);
                }
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || command.getName().equalsIgnoreCase("sethome")) return List.of();
        if (command.getName().equalsIgnoreCase("home") && args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> values = new java.util.ArrayList<>(List.of("set", "del", "list"));
            values.addAll(module.getSuggestions(player.getUniqueId()));
            module.getService().list(player, homes -> module.cacheSuggestions(player.getUniqueId(),
                    homes.stream().map(HomeRecord::name).toList()));
            return values.stream().filter(value -> value.startsWith(prefix)).distinct().toList();
        }
        if (args.length != 1 && !(command.getName().equalsIgnoreCase("home") && args.length == 2
                && (args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("delete")))) return List.of();
        String prefix = args[args.length - 1].toLowerCase();
        module.getService().list(player, homes -> module.cacheSuggestions(player.getUniqueId(),
                homes.stream().map(HomeRecord::name).toList()));
        return module.getSuggestions(player.getUniqueId()).stream().filter(name -> name.startsWith(prefix)).toList();
    }
}
