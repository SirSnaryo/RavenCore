package com.kammoun.raven.economy.command;

import com.kammoun.raven.economy.confirm.EconomyConfirmationService;
import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.util.EconomyAmountParser;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PayCommand extends KCommand {

    private final JavaPlugin plugin;
    private final EconomyConfirmationService confirmations;
    private final NetworkPlayerManager networkPlayerManager;
    private final EconomyMessages messages;

    public PayCommand(JavaPlugin plugin, EconomyConfirmationService confirmations,
                      NetworkPlayerManager networkPlayerManager, EconomyMessages messages) {
        super("pay", null, true);
        this.plugin = plugin;
        this.confirmations = confirmations;
        this.networkPlayerManager = networkPlayerManager;
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
            amount = EconomyAmountParser.parse(args[1]);
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

            SchedulerUtil.execute(player, () -> confirmations.startPay(player, targetUuid, info.getPlayerName(), amount,
                    "/pay " + targetName + " " + args[1]));
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
