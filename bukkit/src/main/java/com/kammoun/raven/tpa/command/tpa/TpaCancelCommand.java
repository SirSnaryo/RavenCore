package com.kammoun.raven.tpa.command.tpa;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.tpa.TeleportModule;
import com.kammoun.raven.tpa.config.TeleportMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TpaCancelCommand extends KCommand {

    private final TeleportModule module;

    public TpaCancelCommand(TeleportModule module) {
        super("tpacancel", "raven.teleport.tpa", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (module.getTpaManager().cancelOutgoing(player.getUniqueId())) {
            module.getTeleportConfig().send(player, TeleportMessage.CANCEL_REQUESTS);
            module.getTeleportConfig().sound(player, "request-cancelled");
            return;
        }
        module.getTeleportConfig().send(player, TeleportMessage.CANCEL_REQUESTS_FAILED);
        module.getTeleportConfig().sound(player, "error");
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
