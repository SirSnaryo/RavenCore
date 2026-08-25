package com.kammoun.raven.economy.command;

import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.gui.EconomyLogsMenu;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.economy.model.EconomyReason;
import com.kammoun.raven.economy.model.EconomyTransactionResult;
import com.kammoun.raven.economy.model.EconomyTransactionType;
import com.kammoun.raven.economy.service.EconomyService;
import com.kammoun.raven.economy.util.EconomyAmountParser;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.command.KSubCommand;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class EcoCommand extends KCommand {

    private static final String PERMISSION = "raven.economy.admin";

    private final EconomyService service;
    private final RavenEconomy economy;
    private final NetworkPlayerManager networkPlayerManager;
    private final EconomyMessages messages;
    private final EconomyLogsMenu logsMenu;

    public EcoCommand(EconomyService service, RavenEconomy economy,
                      NetworkPlayerManager networkPlayerManager, EconomyMessages messages, EconomyLogsMenu logsMenu) {
        super("eco", null, false);
        this.service = service;
        this.economy = economy;
        this.networkPlayerManager = networkPlayerManager;
        this.messages = messages;
        this.logsMenu = logsMenu;

        addSubCommand(
                makeAmountSub("give"),
                makeAmountSub("take"),
                makeAmountSub("set"),
                makeResetSub(),
                makeLogsSub("log"),
                makeLogsSub("logs")
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
        return networkPlayerManager.completePlayerNames(prefix);
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
                    amount = EconomyAmountParser.parse(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(messages.format("eco.invalid-amount", "amount", args[1]));
                    return;
                }
                if (amount < 0) {
                    sender.sendMessage(messages.get("eco.amount-negative"));
                    return;
                }

                String targetName = target.getName() != null ? target.getName() : args[0];
                String reasonText = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Admin economy command";
                String rawCommand = "/eco " + action + " " + args[0] + " " + args[1]
                        + (args.length > 2 ? " " + reasonText : "");
                Player actorPlayer = sender instanceof Player player ? player : null;
                UUID actorUuid = actorPlayer == null ? null : actorPlayer.getUniqueId();
                String actorName = actorPlayer == null ? "Console" : actorPlayer.getName();

                SchedulerUtil.runAsync(() -> {
                    EconomyReason reason = EconomyReason.admin(type(action), reasonText, rawCommand);
                    EconomyTransactionResult result = switch (action) {
                        case "give" -> service.deposit(target.getUniqueId(), amount, reason, actorName, actorUuid, targetName);
                        case "take" -> service.withdraw(target.getUniqueId(), amount, reason, actorName, actorUuid, targetName);
                        case "set" -> service.setBalance(target.getUniqueId(), amount, reason, actorName, actorUuid, targetName);
                        default -> EconomyTransactionResult.failure(target.getUniqueId(), null, amount, "INVALID_ACTION");
                    };
                    reply(sender, () -> {
                        if (!result.success()) {
                            sender.sendMessage(messages.format("eco.operation-failed", "reason", result.failureReason()));
                            return;
                        }
                        double balance = service.getBalance(target.getUniqueId());
                        String formattedAmount = economy.format(amount);
                        String newBalance = economy.format(balance);
                        String msgKey = "eco.admin-" + action;
                        sender.sendMessage(messages.format(msgKey,
                                "player", targetName,
                                "amount", formattedAmount,
                                "amount_formatted", formattedAmount,
                                "amount_full", economy.formatFull(amount),
                                "amount_raw", amount,
                                "balance", newBalance,
                                "balance_formatted", newBalance,
                                "balance_full", economy.formatFull(balance),
                                "balance_raw", balance));

                        if (target.isOnline()) {
                            ((Player) target).sendMessage(messages.format("eco.admin-notify",
                                    "balance", newBalance,
                                    "balance_formatted", newBalance,
                                    "balance_full", economy.formatFull(balance),
                                    "balance_raw", balance));
                        }
                    });
                });
            }

            @Override
            public List<String> getSubCommandTab(CommandSender sender, String[] args) {
                if (args.length == 1) return tabPlayerNames(args[0]);
                if (args.length == 2) return startsWith(args[1], "1k", "10k", "100k", "1m");
                if (args.length == 3) return startsWith(args[2], "admin", "event", "refund", "shop", "rollback");
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
                Player actorPlayer = sender instanceof Player player ? player : null;
                UUID actorUuid = actorPlayer == null ? null : actorPlayer.getUniqueId();
                String actorName = actorPlayer == null ? "Console" : actorPlayer.getName();
                String rawCommand = "/eco reset " + args[0];

                SchedulerUtil.runAsync(() -> {
                    EconomyTransactionResult result = service.setBalance(target.getUniqueId(), 0.0,
                            EconomyReason.admin(EconomyTransactionType.ADMIN_RESET, "Admin reset", rawCommand),
                            actorName, actorUuid, targetName);
                    reply(sender, () -> {
                        if (!result.success()) {
                            sender.sendMessage(messages.format("eco.operation-failed", "reason", result.failureReason()));
                            return;
                        }
                        sender.sendMessage(messages.format("eco.reset-success",
                                "player", targetName,
                                "balance", economy.format(0),
                                "balance_formatted", economy.format(0),
                                "balance_full", economy.formatFull(0),
                                "balance_raw", 0));

                        if (target.isOnline()) {
                            ((Player) target).sendMessage(messages.get("eco.reset-notify"));
                        }
                    });
                });
            }

            @Override
            public List<String> getSubCommandTab(CommandSender sender, String[] args) {
                if (args.length == 1) return tabPlayerNames(args[0]);
                return List.of();
            }
        };
    }

    private KSubCommand makeLogsSub(String name) {
        return new KSubCommand() {
            @Override public String getName() { return name; }
            @Override public String getPermission() { return PERMISSION; }

            @Override
            public void perform(CommandSender sender, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.get("eco.logs-player-only"));
                    return;
                }
                if (logsMenu == null) {
                    sender.sendMessage(messages.get("eco.logs-disabled"));
                    return;
                }
                if (!sender.hasPermission(PERMISSION)) {
                    sender.sendMessage(messages.get("eco.no-permission"));
                    return;
                }
                if (args.length >= 2 && args[0].equalsIgnoreCase("player")) {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    logsMenu.open(player, target.getUniqueId());
                    return;
                }
                logsMenu.open(player, args.length >= 1 && args[0].equalsIgnoreCase("biggest"));
            }

            @Override
            public List<String> getSubCommandTab(CommandSender sender, String[] args) {
                if (args.length == 1) return startsWith(args[0], "biggest", "newest", "player");
                if (args.length == 2 && args[0].equalsIgnoreCase("player")) return tabPlayerNames(args[1]);
                return List.of();
            }
        };
    }

    private EconomyTransactionType type(String action) {
        return switch (action) {
            case "give" -> EconomyTransactionType.ADMIN_GIVE;
            case "take" -> EconomyTransactionType.ADMIN_TAKE;
            case "set" -> EconomyTransactionType.ADMIN_SET;
            default -> EconomyTransactionType.SYSTEM;
        };
    }

    private void reply(CommandSender sender, Runnable runnable) {
        if (sender instanceof Player player) {
            SchedulerUtil.execute(player, runnable);
        } else {
            runnable.run();
        }
    }

    private List<String> startsWith(String prefix, String... values) {
        String lower = prefix == null ? "" : prefix.toLowerCase();
        return Arrays.stream(values)
                .filter(value -> value.startsWith(lower))
                .toList();
    }
}
