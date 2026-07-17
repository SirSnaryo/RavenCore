package com.kammoun.raven.velocity.privatemessage.command;

import com.kammoun.raven.velocity.privatemessage.PrivateMessageModule;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MsgCommand implements SimpleCommand {

    private final PrivateMessageModule module;
    private final ProxyServer server;

    public MsgCommand(PrivateMessageModule module, ProxyServer server) {
        this.module = module;
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player sender)) return;
        if (!module.hasPermission(sender)) return;

        String[] args = invocation.arguments();
        if (args.length < 2) return;

        Player target = server.getPlayer(args[0]).orElse(null);
        if (target == null) {
            module.sendPlayerNotFound(sender, args[0]);
            return;
        }
        if (target.equals(sender)) {
            module.sendCannotMessageSelf(sender);
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        module.send(sender, target, message);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 1) return List.of();

        String partial = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        return server.getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                .collect(Collectors.toList());
    }
}
