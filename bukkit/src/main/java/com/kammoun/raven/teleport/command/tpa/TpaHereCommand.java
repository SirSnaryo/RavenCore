package com.kammoun.raven.teleport.command.tpa;

import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.teleport.TeleportModule;
import com.kammoun.raven.teleport.model.TpaRequest;
import org.bukkit.entity.Player;

public class TpaHereCommand extends TpaRequestCommand {

    public TpaHereCommand(TeleportModule module, NetworkPlayerManager networkPlayers) {
        super("tpahere", module, networkPlayers);
    }

    @Override
    protected TpaRequest.Type type() {
        return TpaRequest.Type.TPA_HERE;
    }

    @Override
    protected void openConfirm(Player player, NetworkPlayerInfo target) {
        module.getTpaHereRequestMenu().open(player, target);
    }
}
