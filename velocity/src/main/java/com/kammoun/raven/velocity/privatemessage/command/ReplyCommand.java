package com.kammoun.raven.velocity.privatemessage.command;

import com.kammoun.raven.velocity.privatemessage.PrivateMessageModule;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

public class ReplyCommand implements SimpleCommand {

    private final PrivateMessageModule module;

    public ReplyCommand(PrivateMessageModule module) {
        this.module = module;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player sender)) return;
        if (!module.hasPermission(sender)) {
            module.sendNoPermission(sender);
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            module.sendUsage(sender, "/r <message>");
            return;
        }

        module.reply(sender, String.join(" ", args));
    }
}
