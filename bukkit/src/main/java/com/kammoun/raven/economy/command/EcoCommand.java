package com.kammoun.raven.economy.command;

import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.service.EconomyService;
import com.kammoun.raven.economy.RavenEconomy;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.command.KSubCommand;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class EcoCommand extends KCommand {

    private static final String PERMISSION = "raven.economy.admin";

    private final EconomyService service;
    private final RavenEconomy economy;
    private final NetworkPlayerManager networkPlayerManager;
    private final EconomyMessages messages;

    public EcoCommand(EconomyService service, RavenEconomy economy,
                      NetworkPlayerManager networkPlayerManager, EconomyMessages messages) {
        super("eco", null, false);
        this.service = service;
        this.economy = economy;
        this.networkPlayerManager = networkPlayerManager;
        this.messages = messages;

        addSubCommand(
                makeAmountSub("give"),
                makeAmountSub("take"),
                makeAmountSub("set"),
                makeResetSub()
        );
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(messages.get("eco.usage"));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }

    private List<String> tabPlayerNames(String prefix) {
        return networkPlayerManager.getNetworkPlayerNames().stream()
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private KSubCommand makeAmountSub(String action) {
        return new KSubCommand() {
            @Override public String getName() { return action; }
            @Override public String getPermission() { return PERMISSION; }

            @Override
            public void perform(CommandSender sender, String[] args) {
                if (!sender.hasPermission(PERMISSION)) {
                    sender.sendMessage(messages.get("eco.no-permission"));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("eco.usage-amount", "action", action));
                    return;
                }

                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if (!service.hasAccount(target.getUniqueId())) {
                    service.createAccount(target.getUniqueId());
                }

                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(messages.format("eco.invalid-amount", "amount", args[1]));
                    return;
                }
                if (amount < 0) {
                    sender.sendMessage(messages.get("eco.amount-negative"));
                    return;
                }

                boolean success = switch (action) {
                    case "give" -> service.deposit(target.getUniqueId(), amount);
                    case "take" -> service.withdraw(target.getUniqueId(), amount);
                    case "set"  -> service.setBalance(target.getUniqueId(), amount);
                    default -> false;
                };

                if (!success) {
                    sender.sendMessage(messages.get("eco.operation-failed"));
                    return;
                }

                String targetName = target.getName() != null ? target.getName() : args[0];
                String formattedAmount = economy.format(amount);
                String newBalance = economy.format(service.getBalance(target.getUniqueId()));
                String msgKey = "eco.admin-" + action;
                sender.sendMessage(messages.format(msgKey,
                        "player", targetName,
                        "amount", formattedAmount,
                        "balance", newBalance));

                if (target.isOnline()) {
                    ((Player) target).sendMessage(messages.format("eco.admin-notify", "balance", newBalance));
                }
            }

            @Override
            public List<String> getSubCommandTab(CommandSender sender, String[] args) {
                if (args.length == 1) return tabPlayerNames(args[0]);
                return List.of();
            }
        };
    }

    private KSubCommand makeResetSub() {
        return new KSubCommand() {
            @Override public String getName() { return "reset"; }
            @Override public String getPermission() { return PERMISSION; }

            @Override
            public void perform(CommandSender sender, String[] args) {
                if (!sender.hasPermission(PERMISSION)) {
                    sender.sendMessage(messages.get("eco.no-permission"));
                    return;
                }
                if (args.length < 1) {
                    sender.sendMessage(messages.get("eco.usage-reset"));
                    return;
                }

                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                String targetName = target.getName() != null ? target.getName() : args[0];

                service.setBalance(target.getUniqueId(), 0.0);
                sender.sendMessage(messages.format("eco.reset-success",
                        "player", targetName,
                        "balance", economy.format(0)));

                if (target.isOnline()) {
                    ((Player) target).sendMessage(messages.get("eco.reset-notify"));
                }
            }

            @Override
            public List<String> getSubCommandTab(CommandSender sender, String[] args) {
                if (args.length == 1) return tabPlayerNames(args[0]);
                return List.of();
            }
        };
    }
}
