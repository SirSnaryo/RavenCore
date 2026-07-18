package com.kammoun.raven.velocity.lobby.config;

import com.kammoun.raven.velocity.lobby.LobbyModule;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.net.InetSocketAddress;

/**
 * Holds the lobby transfer target and player-facing messages for {@link LobbyModule}.
 * Every line is read from {@code lobby/config.yml} — nothing is hardcoded.
 */
public class LobbyConfig {

    private final LobbyModule module;

    private InetSocketAddress target;
    private Component playersOnly;
    private String transferringRaw;
    private Component transferring;
    private Component transferFailed;

    public LobbyConfig(LobbyModule module) {
        this.module = module;
        load();
    }

    public void load() {
        String host = module.getString("target.host", "127.0.0.1");
        int port = module.getInt("target.port", 25565);
        this.target = new InetSocketAddress(host, port);

        this.playersOnly = translate(module.getString("messages.players-only", "&cOnly players can use this command."));
        this.transferringRaw = module.getString("messages.transferring", "&7Sending you to the lobby...");
        this.transferring = translate(transferringRaw);
        this.transferFailed = translate(module.getString("messages.transfer-failed", "&cTransfer failed — please try again."));
    }

    public InetSocketAddress target() {
        return target;
    }

    public void sendPlayersOnly(CommandSource source) {
        source.sendMessage(playersOnly);
    }

    public void sendTransferring(Player player) {
        if (transferringRaw == null || transferringRaw.isBlank()) return;
        player.sendMessage(transferring);
    }

    public void sendTransferFailed(Player player) {
        player.sendMessage(transferFailed);
    }

    private Component translate(String legacyText) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
    }
}
