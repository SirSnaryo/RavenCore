package com.kammoun.raven.tpa.command.tp;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.tpa.TeleportModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class TeleportHereCommand extends KCommand {

    private final TeleportModule module;
    private final NetworkPlayerManager npm;

    public TeleportHereCommand(TeleportModule module, NetworkPlayerManager npm) {
        super("tphere", "raven.teleport", false);
        this.module = module;
        this.npm = npm;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player self)) {
            send(sender, "&#D90429Console must use /tp <player> <destination> or /tp <player> <x> <y> <z> [server].");
            return;
        }

        if (args.length != 1) {
            send(sender, "&#D90429Usage: /tphere <player>");
            return;
        }

        NetworkPlayerInfo traveller = resolveNetwork(sender, args[0]);
        if (traveller == null) return;
        if (traveller.getUuid().equals(self.getUniqueId())) {
            send(sender, "&#D90429You cannot teleport yourself to yourself.");
            return;
        }

        module.teleportToPlayer(sender, traveller,
                new NetworkPlayerInfo(self.getUniqueId(), self.getName(), module.currentServer(), self.getWorld().getName()));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return sender instanceof Player player
                    ? module.tabPlayers(args[0], player.getName())
                    : module.tabPlayers(args[0]);
        }
        return List.of();
    }

    private NetworkPlayerInfo resolveNetwork(CommandSender sender, String name) {
        Optional<NetworkPlayerInfo> info = npm.findPlayer(name);
        if (info.isEmpty()) send(sender, "&#D90429Player &#FFD447" + name + " &#D90429is not online.");
        return info.orElse(null);
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatFormatter.color(message));
    }
}
