package com.kammoun.raven.economy.command;

import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.service.EconomyService;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.redis.event.PaymentNotificationEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PayCommand extends KCommand {

    private final JavaPlugin plugin;
    private final EconomyService service;
    private final RavenEconomy economy;
    private final NetworkPlayerManager networkPlayerManager;
    private final NetworkEventBus networkEventBus;
    private final EconomyMessages messages;

    public PayCommand(JavaPlugin plugin, EconomyService service, RavenEconomy economy,
                      NetworkPlayerManager networkPlayerManager, NetworkEventBus networkEventBus,
                      EconomyMessages messages) {
        super("pay", null, true);
        this.plugin = plugin;
        this.service = service;
        this.economy = economy;
        this.networkPlayerManager = networkPlayerManager;
        this.networkEventBus = networkEventBus;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(messages.get("pay.usage"));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(messages.format("pay.invalid-amount", "amount", args[1]));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(messages.get("pay.amount-positive"));
            return;
        }

        String targetName = args[0];

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<NetworkPlayerInfo> infoOpt = networkPlayerManager.findPlayer(targetName);
            if (infoOpt.isEmpty()) {
                player.sendMessage(messages.format("pay.player-not-found", "player", targetName));
                return;
            }

            NetworkPlayerInfo info = infoOpt.get();
            UUID targetUuid = info.getUuid();

            if (targetUuid.equals(player.getUniqueId())) {
                player.sendMessage(messages.get("pay.self-pay"));
                return;
            }

            if (!service.withdraw(player.getUniqueId(), amount)) {
                player.sendMessage(messages.format("pay.insufficient-funds",
                        "balance", economy.format(service.getBalance(player.getUniqueId()))));
                return;
            }

            service.deposit(targetUuid, amount);

            String formattedAmount = economy.format(amount);
            player.sendMessage(messages.format("pay.sent", "amount", formattedAmount, "player", info.getPlayerName()));

            String receivedMsg = messages.format("pay.received", "amount", formattedAmount, "player", player.getName());

            Player localTarget = Bukkit.getPlayer(targetUuid);
            if (localTarget != null) {
                localTarget.sendMessage(receivedMsg);
            } else {
                PaymentNotificationEvent notif = new PaymentNotificationEvent();
                notif.setRecipientUuid(targetUuid.toString());
                notif.setMessage(receivedMsg);
                networkEventBus.publish(notif);
            }
        });
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String senderName = sender instanceof Player p ? p.getName() : null;
            return networkPlayerManager.completePlayerNames(args[0], senderName);
        }
        return List.of();
    }
}
