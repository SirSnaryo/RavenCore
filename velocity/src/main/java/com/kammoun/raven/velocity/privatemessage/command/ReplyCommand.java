package com.kammoun.raven.velocity.privatemessage.command;

import com.kammoun.raven.velocity.privatemessage.PrivateMessageModule;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.UUID;

public class ReplyCommand implements SimpleCommand {

    private final PrivateMessageModule module;
    private final ProxyServer server;

    public ReplyCommand(PrivateMessageModule module, ProxyServer server) {
        this.module = module;
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player sender)) return;
        if (!module.hasPermission(sender)) return;

        String[] args = invocation.arguments();
        if (args.length < 1) return;

        UUID partnerId = module.getLastPartner(sender.getUniqueId());
        if (partnerId == null) {
            module.sendNoReplyTarget(sender);
            return;
        }

        Player target = server.getPlayer(partnerId).orElse(null);
        if (target == null) {
            module.sendReplyTargetOffline(sender);
            return;
        }

        String message = String.join(" ", args);
        module.send(sender, target, message);
    }
}
