package com.kammoun.raven.tpa.command.tpa;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.tpa.TeleportModule;
import com.kammoun.raven.tpa.config.TeleportMessage;
import com.kammoun.raven.tpa.model.TpaRequest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class TpaDenyCommand extends KCommand {

    private final TeleportModule module;

    public TpaDenyCommand(TeleportModule module) {
        super("tpadeny", "raven.teleport.tpa", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        Optional<TpaRequest> request = args.length == 0
                ? module.getTpaManager().latestRequest(player.getUniqueId())
                : module.findRequest(player, args[0]);

        if (request.isEmpty()) {
            module.getTeleportConfig().send(player, TeleportMessage.NO_REQUESTS_FOUND);
            return;
        }

        module.getTpaManager().denyRequest(player, request.get());
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (sender instanceof Player player && args.length == 1) {
            return module.getTpaManager().requesterNames(player, args[0]);
        }
        return List.of();
    }
}
