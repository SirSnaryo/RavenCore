package com.kammoun.raven.teleport;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class TeleportCommand extends KCommand {

    private final TeleportModule module;
    private final NetworkPlayerManager npm;

    public TeleportCommand(TeleportModule module, NetworkPlayerManager npm) {
        super("tp", "raven.teleport", false);
        this.module = module;
        this.npm    = npm;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // /tp <destination> — sender goes to destination
            if (!(sender instanceof Player self)) {
                sender.sendMessage("§cConsole must use: /tp <player> <destination>");
                return;
            }
            NetworkPlayerInfo dest = resolveNetwork(sender, args[0]);
            if (dest == null) return;
            module.teleport(sender, self.getUniqueId(), self.getName(), dest);

        } else if (args.length == 2) {
            // /tp <traveller> <destination> — traveller must be local
            Player traveller = Bukkit.getPlayerExact(args[0]);
            if (traveller == null) {
                sender.sendMessage("§e" + args[0] + " §cis not on this server.");
                return;
            }
            NetworkPlayerInfo dest = resolveNetwork(sender, args[1]);
            if (dest == null) return;
            module.teleport(sender, traveller.getUniqueId(), traveller.getName(), dest);

        } else {
            sender.sendMessage("§cUsage: /tp <destination> | /tp <player> <destination>");
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1 || args.length == 2)
            return module.tabPlayers(args[args.length - 1]);
        return List.of();
    }

    private NetworkPlayerInfo resolveNetwork(CommandSender sender, String name) {
        Optional<NetworkPlayerInfo> info = npm.findPlayer(name);
        if (info.isEmpty()) sender.sendMessage("§cPlayer §e" + name + " §cis not online.");
        return info.orElse(null);
    }
}

