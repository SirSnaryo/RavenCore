package com.kammoun.raven.clearinventory.command;

import com.kammoun.raven.clearinventory.config.ClearInventoryMessages;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.redis.event.ClearInventoryEvent;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClearInventoryCommand extends KCommand {

    private final NetworkPlayerManager networkPlayerManager;
    private final NetworkEventBus networkEventBus;
    private final ClearInventoryMessages messages;
    private final boolean requireConfirmation;
    private final long confirmationTimeoutMs;

    // Tracks players waiting to confirm a self-clear: uuid → expiry timestamp
    private final Map<UUID, Long> pendingConfirmations = new ConcurrentHashMap<>();

    public ClearInventoryCommand(NetworkPlayerManager networkPlayerManager,
                                 NetworkEventBus networkEventBus,
                                 ClearInventoryMessages messages,
                                 boolean requireConfirmation,
                                 int confirmationTimeoutSeconds) {
        super("clear", "raven.clearinventory.self", false);
        this.networkPlayerManager = networkPlayerManager;
        this.networkEventBus = networkEventBus;
        this.messages = messages;
        this.requireConfirmation = requireConfirmation;
        this.confirmationTimeoutMs = confirmationTimeoutSeconds * 1000L;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Self-clear — must be a player
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.get("console-requires-player"));
                return;
            }
            handleSelfClear(player);
            return;
        }

        // Clear another player — requires permission
        if (!sender.hasPermission("raven.clearinventory.others")) {
            sender.sendMessage(messages.get("no-permission-others"));
            return;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);

        if (target != null) {
            // Player is on this server
            clearInventory(target);
            sender.sendMessage(messages.format("cleared-other", "player", target.getName()));
            target.sendMessage(messages.get("cleared-by-admin"));
            return;
        }

        // Player is on another server — publish cross-server event
        if (networkPlayerManager.findPlayer(targetName).isPresent()) {
            ClearInventoryEvent event = new ClearInventoryEvent();
            event.setTargetUuid(networkPlayerManager.findPlayer(targetName).get().getUuid().toString());
            event.setSenderName(sender.getName());
            networkEventBus.publish(event);
            sender.sendMessage(messages.format("cleared-other", "player", targetName));
        } else {
            sender.sendMessage(messages.format("player-not-found", "player", targetName));
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("raven.clearinventory.others")) {
            return networkPlayerManager.completePlayerNames(args[0]);
        }
        return List.of();
    }

    private void handleSelfClear(Player player) {
        if (!requireConfirmation) {
            clearInventory(player);
            player.sendMessage(messages.get("cleared-self"));
            return;
        }

        UUID uuid = player.getUniqueId();
        Long expiry = pendingConfirmations.get(uuid);

        if (expiry != null && System.currentTimeMillis() < expiry) {
            // Second run within the window — confirmed
            pendingConfirmations.remove(uuid);
            clearInventory(player);
            player.sendMessage(messages.get("cleared-self"));
        } else {
            // First run — ask for confirmation
            pendingConfirmations.put(uuid, System.currentTimeMillis() + confirmationTimeoutMs);
            player.sendMessage(messages.format("confirm",
                    "seconds", confirmationTimeoutMs / 1000));
        }
    }

    private static void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }
}
