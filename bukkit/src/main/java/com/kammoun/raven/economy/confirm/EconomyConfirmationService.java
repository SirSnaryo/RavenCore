package com.kammoun.raven.economy.confirm;

import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.economy.model.EconomyReason;
import com.kammoun.raven.economy.model.EconomyTransactionResult;
import com.kammoun.raven.economy.model.PendingEconomyAction;
import com.kammoun.raven.economy.service.EconomyService;
import com.kammoun.raven.economy.settings.EconomySettingsService;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.redis.event.PaymentNotificationEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class EconomyConfirmationService {

    private final JavaPlugin plugin;
    private final EconomyService service;
    private final RavenEconomy economy;
    private final EconomySettingsService settings;
    private final NetworkEventBus eventBus;
    private final EconomyMessages messages;
    private final KSound openSound;
    private final KSound successSound;
    private final KSound cancelSound;
    private final KSound failSound;
    private final boolean enabled;
    private final double threshold;
    private final long expiresMillis;
    private EconomyConfirmationMenu menu;

    public EconomyConfirmationService(JavaPlugin plugin, EconomyService service, RavenEconomy economy,
                                      EconomySettingsService settings, NetworkEventBus eventBus,
                                      EconomyMessages messages, KSound openSound, KSound successSound,
                                      KSound cancelSound, KSound failSound,
                                      boolean enabled, double threshold, int expiresSeconds) {
        this.plugin = plugin;
        this.service = service;
        this.economy = economy;
        this.settings = settings;
        this.eventBus = eventBus;
        this.messages = messages;
        this.openSound = openSound;
        this.successSound = successSound;
        this.cancelSound = cancelSound;
        this.failSound = failSound;
        this.enabled = enabled;
        this.threshold = threshold;
        this.expiresMillis = Math.max(5, expiresSeconds) * 1_000L;
    }

    public void menu(EconomyConfirmationMenu menu) {
        this.menu = menu;
    }

    public void startPay(Player player, UUID targetUuid, String targetName, double amount, String rawCommand) {
        if (!enabled || amount < threshold || player.hasPermission("raven.economy.confirm.bypass")) {
            executePay(player, action(player, targetUuid, targetName, amount, rawCommand));
            return;
        }

        PendingEconomyAction action = action(player, targetUuid, targetName, amount, rawCommand);
        String formattedAmount = economy.format(amount);
        String current = economy.format(service.getBalance(player.getUniqueId()));
        String after = economy.format(Math.max(0D, service.getBalance(player.getUniqueId()) - amount));
        play(openSound, player);
        menu.open(player, action, formattedAmount, current, after);
    }

    public void confirm(Player player, PendingEconomyAction action) {
        executePay(player, action);
    }

    public void cancel(Player player, PendingEconomyAction action) {
        play(cancelSound, player);
        player.sendMessage(messages.format("pay.confirm-cancelled", "amount", economy.format(action.amount()), "player", action.targetName()));
    }

    private PendingEconomyAction action(Player player, UUID targetUuid, String targetName, double amount, String rawCommand) {
        return new PendingEconomyAction(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                player.getName(),
                targetUuid,
                targetName,
                amount,
                "Player payment",
                rawCommand,
                System.currentTimeMillis() + expiresMillis
        );
    }

    private void executePay(Player player, PendingEconomyAction action) {
        if (action.expired()) {
            play(failSound, player);
            player.sendMessage(messages.get("pay.confirm-expired"));
            return;
        }

        SchedulerUtil.runAsync(() -> {
            if (!settings.acceptsPayments(action.targetUuid()) && !player.hasPermission("raven.economy.paytoggle.bypass")) {
                SchedulerUtil.execute(player, () -> player.sendMessage(messages.format("pay.pay-disabled", "player", action.targetName())));
                return;
            }

            EconomyTransactionResult result = service.transfer(
                    action.actorUuid(),
                    action.targetUuid(),
                    action.amount(),
                    EconomyReason.playerPay(action.rawCommand()),
                    action.actorName(),
                    action.targetName(),
                    action.rawCommand()
            );

            SchedulerUtil.execute(player, () -> sendResult(player, action, result));
        });
    }

    private void sendResult(Player player, PendingEconomyAction action, EconomyTransactionResult result) {
        if (!result.success()) {
            play(failSound, player);
            String key = "INSUFFICIENT_FUNDS".equalsIgnoreCase(result.failureReason())
                    ? "pay.insufficient-funds"
                    : "pay.operation-failed";
            player.sendMessage(messages.format(key,
                    "balance", economy.format(service.getBalance(player.getUniqueId())),
                    "reason", result.failureReason()));
            return;
        }

        String formattedAmount = economy.format(action.amount());
        play(successSound, player);
        if (settings.paymentMessages(player.getUniqueId())) {
            player.sendMessage(messages.format("pay.sent", "amount", formattedAmount, "player", action.targetName()));
        }

        String receivedMsg = messages.format("pay.received", "amount", formattedAmount, "player", action.actorName());
        Player localTarget = Bukkit.getPlayer(action.targetUuid());
        if (localTarget != null) {
            if (settings.paymentMessages(localTarget.getUniqueId())) {
                SchedulerUtil.execute(localTarget, () -> localTarget.sendMessage(receivedMsg));
            }
        } else {
            PaymentNotificationEvent notif = new PaymentNotificationEvent();
            notif.setRecipientUuid(action.targetUuid().toString());
            notif.setMessage(receivedMsg);
            eventBus.publish(notif);
        }
    }

    private void play(KSound sound, Player player) {
        if (sound != null) {
            sound.playSound(player);
        }
    }
}
