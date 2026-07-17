package com.kammoun.raven.velocity.privatemessage;

import com.kammoun.raven.library.module.VModule;
import com.kammoun.raven.velocity.privatemessage.command.MsgCommand;
import com.kammoun.raven.velocity.privatemessage.command.ReplyCommand;
import com.kammoun.raven.velocity.privatemessage.config.PrivateMessages;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-server private messaging (/msg, /r). Runs entirely at the proxy layer: Velocity
 * already holds a direct connection to every player on the network regardless of which
 * backend server they're on, so messages are delivered in-memory with no Redis round trip.
 */
public class PrivateMessageModule extends VModule {

    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();
    private PrivateMessages messages;
    private String permission;

    public PrivateMessageModule() {
        super("privatemessage");
    }

    @Override
    public void onModuleEnable() {
        messages = new PrivateMessages(this);
        permission = getString("permission", "raven.msg");

        registerListener(this);
        registerCommand("msg", new MsgCommand(this, server), "w", "tell", "pm", "message");
        registerCommand("r", new ReplyCommand(this, server), "reply");

        getLogger().info("PrivateMessage module enabled.");
    }

    @Override
    public void onModuleDisable() {
        lastPartner.clear();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastPartner.remove(uuid);
        lastPartner.values().removeIf(uuid::equals);
    }

    public boolean hasPermission(Player player) {
        return player.hasPermission(permission);
    }

    public UUID getLastPartner(UUID uuid) {
        return lastPartner.get(uuid);
    }

    public void send(Player sender, Player target, String message) {
        target.sendMessage(messages.format("format.received", "&7[&f%sender%&7 -> You]: &f%message%",
                "%sender%", sender.getUsername(), "%message%", message));
        sender.sendMessage(messages.format("format.sent", "&7[You -> &f%target%&7]: &f%message%",
                "%target%", target.getUsername(), "%message%", message));

        lastPartner.put(sender.getUniqueId(), target.getUniqueId());
        lastPartner.put(target.getUniqueId(), sender.getUniqueId());
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
}
