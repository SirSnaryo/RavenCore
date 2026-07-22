package com.kammoun.raven.tpa.command.tpa;

import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.tpa.TeleportModule;
import com.kammoun.raven.tpa.model.TpaRequest;
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
