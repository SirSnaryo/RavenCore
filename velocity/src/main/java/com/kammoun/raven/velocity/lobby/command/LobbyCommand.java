package com.kammoun.raven.velocity.lobby.command;

import com.kammoun.raven.velocity.lobby.LobbyModule;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

public class LobbyCommand implements SimpleCommand {

    private final LobbyModule module;

    public LobbyCommand(LobbyModule module) {
        this.module = module;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            module.getConfig().sendPlayersOnly(invocation.source());
            return;
        }

        module.getConfig().sendTransferring(player);
        try {
            player.transferToHost(module.getConfig().target());
            module.getLogger().info("Lobby transfer sent for " + player.getUsername()
                    + " -> " + module.getConfig().target());
        } catch (Exception e) {
            module.getLogger().warning("transferToHost (lobby) failed for "
                    + player.getUsername() + ": " + e.getMessage());
            module.getConfig().sendTransferFailed(player);
        }
    }
}
