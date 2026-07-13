package com.kammoun.raven.shop.command;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.shop.ShopModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ShopCommand implements CommandExecutor {

    private final ShopModule module;

    public ShopCommand(ShopModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /shop.");
            return true;
        }

        if (args.length == 0) {
            module.getConfigManager().openMainMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload") && player.hasPermission("raven.shop.admin")) {
            module.reload();
            player.sendMessage(ChatFormatter.color("&aShop reloaded!"));
            return true;
        }

        String input = args[0].toLowerCase().replace(".yml", "");

        if (module.getCategoryManager().isValid(input)) {
            module.getCategoryManager().open(player, input);
            return true;
        }

        String match = module.getCategoryManager().getCategories().keySet().stream()
                .filter(k -> k.startsWith(input) || k.contains(input))
                .findFirst().orElse(null);

        if (match != null) {
            module.getCategoryManager().open(player, match);
            return true;
        }

        player.sendMessage(ChatFormatter.color("&cUnknown category: &f" + input));
        module.getCategoryManager().getCategories().keySet()
                .forEach(k -> player.sendMessage(ChatFormatter.color("&7- &f" + k)));
        return true;
    }
}
