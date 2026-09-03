package com.kammoun.raven.velocity.privatemessage.command;

import com.kammoun.raven.velocity.privatemessage.PrivateMessageModule;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.Arrays;
import java.util.List;

public class MsgCommand implements SimpleCommand {

    private final PrivateMessageModule module;

    public MsgCommand(PrivateMessageModule module) {
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
        if (args.length < 2) {
            module.sendUsage(sender, "/msg <player> <message>");
            return;
        }

        module.send(sender, args[0], String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 1 || !(invocation.source() instanceof Player sender)) {
            return List.of();
        }

        String partial = args.length == 1 ? args[0] : "";
        return module.completePlayerNames(partial, sender.getUsername());
    }
}
