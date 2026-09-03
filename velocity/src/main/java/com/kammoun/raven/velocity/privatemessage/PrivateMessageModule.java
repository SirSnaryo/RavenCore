package com.kammoun.raven.velocity.privatemessage;

import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.redis.event.PrivateMessageDeliveredEvent;
import com.kammoun.raven.library.redis.event.PrivateMessageRequestEvent;
import com.kammoun.raven.library.module.VModule;
import com.kammoun.raven.velocity.RavenVelocity;
import com.kammoun.raven.velocity.privatemessage.command.MsgCommand;
import com.kammoun.raven.velocity.privatemessage.command.ReplyCommand;
import com.kammoun.raven.velocity.privatemessage.config.PrivateMessages;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Multi-proxy private messaging. Local Velocity lookups only see this proxy, so
 * cross-proxy delivery uses Raven's Redis event bus and network player registry.
 */
public class PrivateMessageModule extends VModule {

    private final RavenVelocity ravenVelocity;
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();
    private final Map<String, PendingMessage> pending = new ConcurrentHashMap<>();
    private PrivateMessages messages;
    private String permission;

    public PrivateMessageModule(RavenVelocity ravenVelocity) {
        super("privatemessage");
        this.ravenVelocity = ravenVelocity;
    }

    @Override
    public void onModuleEnable() {
        messages = new PrivateMessages(this);
        permission = getString("permission", "");

        ravenVelocity.getNetworkEventBus().subscribe(PrivateMessageRequestEvent.class, this::onMessageRequest);
        ravenVelocity.getNetworkEventBus().subscribe(PrivateMessageDeliveredEvent.class, this::onMessageDelivered);

        registerListener(this);
        registerCommand("msg", new MsgCommand(this), "w", "tell", "pm", "message");
        registerCommand("r", new ReplyCommand(this), "reply");

        getLogger().info("PrivateMessage module enabled.");
    }

    @Override
    public void onModuleDisable() {
        ravenVelocity.getNetworkEventBus().unsubscribe(PrivateMessageRequestEvent.class);
        ravenVelocity.getNetworkEventBus().unsubscribe(PrivateMessageDeliveredEvent.class);
        lastPartner.clear();
        pending.clear();
    }

    public void reload() {
        reloadConfig();
        messages = new PrivateMessages(this);
        permission = getString("permission", "");
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastPartner.remove(uuid);
        lastPartner.values().removeIf(uuid::equals);
        pending.values().removeIf(message -> message.senderUuid().equals(uuid));
    }

    public boolean hasPermission(Player player) {
        return permission == null || permission.isBlank()
                || "raven.msg".equalsIgnoreCase(permission)
                || player.hasPermission(permission);
    }

    public UUID getLastPartner(UUID uuid) {
        return lastPartner.get(uuid);
    }

    public List<String> completePlayerNames(String prefix, String excludedName) {
        return ravenVelocity.getNetworkPlayerManager().completePlayerNames(prefix, excludedName);
    }

    public void send(Player sender, String targetName, String message) {
        if (!hasPermission(sender)) {
            sendNoPermission(sender);
            return;
        }

        Optional<NetworkPlayerInfo> targetInfo = ravenVelocity.getNetworkPlayerManager().findPlayer(targetName);
        if (targetInfo.isEmpty()) {
            sendPlayerNotFound(sender, targetName);
            return;
        }
        send(sender, targetInfo.get(), message);
    }

    public void reply(Player sender, String message) {
        if (!hasPermission(sender)) {
            sendNoPermission(sender);
            return;
        }

        UUID partnerId = getLastPartner(sender.getUniqueId());
        if (partnerId == null) {
            sendNoReplyTarget(sender);
            return;
        }

        Optional<NetworkPlayerInfo> targetInfo = ravenVelocity.getNetworkPlayerManager().findPlayer(partnerId);
        if (targetInfo.isEmpty()) {
            sendReplyTargetOffline(sender);
            return;
        }
        send(sender, targetInfo.get(), message);
    }

    private void send(Player sender, NetworkPlayerInfo targetInfo, String message) {
        if (targetInfo.getUuid().equals(sender.getUniqueId())) {
            sendCannotMessageSelf(sender);
            return;
        }

        String requestId = UUID.randomUUID().toString();
        pending.put(requestId, new PendingMessage(sender.getUniqueId(), targetInfo.getUuid(),
                targetInfo.getPlayerName(), message));

        PrivateMessageRequestEvent event = new PrivateMessageRequestEvent();
        event.setRequestId(requestId);
        event.setSenderUuid(sender.getUniqueId().toString());
        event.setSenderName(sender.getUsername());
        event.setTargetUuid(targetInfo.getUuid().toString());
        event.setTargetName(targetInfo.getPlayerName());
        event.setMessage(message);
        ravenVelocity.getNetworkEventBus().publish(event);

        server.getScheduler().buildTask(plugin, () -> {
            PendingMessage expired = pending.remove(requestId);
            if (expired != null) {
                server.getPlayer(expired.senderUuid()).ifPresent(player ->
                        player.sendMessage(messages.format("delivery-failed",
                                "&cCould not deliver your message to &f%player%&c.",
                                "%player%", expired.targetName())));
            }
        }).delay(3, TimeUnit.SECONDS).schedule();
    }

    private void onMessageRequest(PrivateMessageRequestEvent event) {
        if (event == null || event.getTargetUuid() == null || event.getSenderUuid() == null) {
            return;
        }

        UUID targetUuid = parseUuid(event.getTargetUuid());
        UUID senderUuid = parseUuid(event.getSenderUuid());
        if (targetUuid == null || senderUuid == null) {
            return;
        }

        server.getScheduler().buildTask(plugin, () ->
                server.getPlayer(targetUuid).ifPresent(target -> {
                    target.sendMessage(messages.format("format.received",
                            "&7[&f%sender%&7 -> You]: &f%message%",
                            "%sender%", event.getSenderName(), "%message%", event.getMessage()));
                    lastPartner.put(targetUuid, senderUuid);

                    PrivateMessageDeliveredEvent delivered = new PrivateMessageDeliveredEvent();
                    delivered.setRequestId(event.getRequestId());
                    delivered.setSenderUuid(event.getSenderUuid());
                    delivered.setTargetUuid(event.getTargetUuid());
                    ravenVelocity.getNetworkEventBus().publish(delivered);
                })
        ).schedule();
    }

    private void onMessageDelivered(PrivateMessageDeliveredEvent event) {
        if (event == null || event.getRequestId() == null) {
            return;
        }

        PendingMessage message = pending.remove(event.getRequestId());
        if (message == null) {
            return;
        }

        server.getScheduler().buildTask(plugin, () ->
                server.getPlayer(message.senderUuid()).ifPresent(sender -> {
                    sender.sendMessage(messages.format("format.sent",
                            "&7[You -> &f%target%&7]: &f%message%",
                            "%target%", message.targetName(), "%message%", message.message()));
                    lastPartner.put(message.senderUuid(), message.targetUuid());
                })
        ).schedule();
    }

    public void sendPlayerNotFound(Player player, String name) {
        player.sendMessage(messages.format("player-not-found", "&cPlayer &f%player%&c is not online.",
                "%player%", name));
    }

    public void sendCannotMessageSelf(Player player) {
        player.sendMessage(messages.format("cannot-message-self", "&cYou cannot message yourself."));
    }

    public void sendNoReplyTarget(Player player) {
        player.sendMessage(messages.format("no-reply-target", "&cYou have no one to reply to."));
    }

    public void sendReplyTargetOffline(Player player) {
        player.sendMessage(messages.format("reply-target-offline", "&cThat player is no longer online."));
    }

    public void sendNoPermission(Player player) {
        player.sendMessage(messages.format("no-permission", "&cYou don't have permission to use private messages."));
    }

    public void sendUsage(Player player, String usage) {
        player.sendMessage(messages.format("usage", "&cUsage: %usage%", "%usage%", usage));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record PendingMessage(UUID senderUuid, UUID targetUuid, String targetName, String message) {}
}
