package com.kammoun.raven.economy.command;

import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.service.EconomyService;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BalanceCommand extends KCommand {

    private final EconomyService service;
    private final RavenEconomy economy;
    private final NetworkPlayerManager networkPlayerManager;
    private final EconomyMessages messages;

    public BalanceCommand(EconomyService service, RavenEconomy economy,
                          NetworkPlayerManager networkPlayerManager, EconomyMessages messages) {
        super("balance", null, false);
        this.service = service;
        this.economy = economy;
        this.networkPlayerManager = networkPlayerManager;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.get("balance.usage-console"));
                return;
            }
            player.sendMessage(messages.format("balance.own",
                    "balance", economy.format(service.getBalance(player.getUniqueId()))));
            return;
        }

        if (!sender.hasPermission("raven.economy.balance.others")) {
            sender.sendMessage(messages.get("balance.no-permission"));
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!service.hasAccount(target.getUniqueId())) {
            sender.sendMessage(messages.format("balance.no-account", "player", args[0]));
            return;
        }

        String name = target.getName() != null ? target.getName() : args[0];
        sender.sendMessage(messages.format("balance.other",
                "player", name,
                "balance", economy.format(service.getBalance(target.getUniqueId()))));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("raven.economy.balance.others")) {
            return networkPlayerManager.completePlayerNames(args[0]);
        }
        return List.of();
    }
}
