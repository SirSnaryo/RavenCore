package com.kammoun.raven.velocity.info.command;

import com.kammoun.raven.velocity.info.InfoModule;
import com.kammoun.raven.velocity.info.config.InfoMessage;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

public class MediaCommand implements SimpleCommand {

    private final InfoModule module;

    public MediaCommand(InfoModule module) {
        this.module = module;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return;
        module.getConfigManager().sendMessage(player, InfoMessage.MEDIA);
    }
}
