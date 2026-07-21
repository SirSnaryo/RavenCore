package com.kammoun.raven.teleport.command.tpa;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.teleport.TeleportModule;
import com.kammoun.raven.teleport.config.TeleportMessage;
import com.kammoun.raven.teleport.model.TpaRequest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

abstract class TpaRequestCommand extends KCommand {

    protected final TeleportModule module;
    private final NetworkPlayerManager networkPlayers;

    TpaRequestCommand(String commandName, TeleportModule module, NetworkPlayerManager networkPlayers) {
        super(commandName, "raven.teleport.tpa", true);
        this.module = module;
        this.networkPlayers = networkPlayers;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (args.length != 1) {
            player.sendMessage(ChatFormatter.color("&#D90429Usage: /" + getCommandName() + " <player>"));
            return;
        }

        Optional<NetworkPlayerInfo> target = networkPlayers.findPlayer(args[0]);
        if (target.isEmpty()) {
            module.getTeleportConfig().send(player, TeleportMessage.USER_NOT_FOUND, "%player%", args[0]);
            return;
        }

        if (target.get().getUuid().equals(player.getUniqueId())) {
            module.getTeleportConfig().send(player, TeleportMessage.SELF_TELEPORT);
            return;
        }

        if (module.usesConfirmMenu(player)) {
            openConfirm(player, target.get());
            return;
        }

        module.getTpaManager().request(player, target.get(), type());
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return module.tabPlayers(args[0], sender.getName());
        }
        return List.of();
    }

    protected abstract TpaRequest.Type type();

    protected abstract void openConfirm(Player player, NetworkPlayerInfo target);
}
