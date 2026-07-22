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

public class TeleportCommand extends KCommand {

    private final TeleportModule module;
    private final NetworkPlayerManager npm;

    public TeleportCommand(TeleportModule module, NetworkPlayerManager npm) {
        super("tp", "raven.teleport", false);
        this.module = module;
        this.npm = npm;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 1) {
            teleportSelfToPlayer(sender, args[0]);
            return;
        }

        if (args.length == 2) {
            teleportPlayerToPlayer(sender, args[0], args[1]);
            return;
        }

        if (args.length == 3 && areCoordinates(args, 0)) {
            teleportSelfToCoordinates(sender, args, 0, module.currentServer());
            return;
        }

        if (args.length == 4) {
            if (areCoordinates(args, 0)) {
                teleportSelfToCoordinates(sender, args, 0, args[3]);
                return;
            }
            if (areCoordinates(args, 1)) {
                teleportPlayerToCoordinates(sender, args[0], args, 1, module.currentServer());
                return;
            }
        }

        if (args.length == 5 && areCoordinates(args, 1)) {
            teleportPlayerToCoordinates(sender, args[0], args, 1, args[4]);
            return;
        }

        send(sender, "&#D90429Usage: /tp <player> | /tp <player> <player> | /tp <x> <y> <z> [server] | /tp <player> <x> <y> <z> [server]");
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return module.tabPlayers(args[0]);
        }
        if (args.length == 2 && !isNumber(args[0])) {
            return module.tabPlayers(args[1], args[0]);
        }
        if (args.length == 4 && areCoordinates(args, 0)) {
            return module.tabServers(args[3]);
        }
        if (args.length == 5 && areCoordinates(args, 1)) {
            return module.tabServers(args[4]);
        }
        return List.of();
    }

    private void teleportSelfToPlayer(CommandSender sender, String destinationName) {
        if (!(sender instanceof Player self)) {
            send(sender, "&#D90429Console must use: /tp <player> <destination>");
            return;
        }

        NetworkPlayerInfo destination = resolveNetwork(sender, destinationName);
        if (destination == null) return;
        module.teleportToPlayer(sender, localInfo(self), destination);
    }

    private void teleportPlayerToPlayer(CommandSender sender, String travellerName, String destinationName) {
        NetworkPlayerInfo traveller = resolveNetwork(sender, travellerName);
        if (traveller == null) return;

        NetworkPlayerInfo destination = resolveNetwork(sender, destinationName);
        if (destination == null) return;

        module.teleportToPlayer(sender, traveller, destination);
    }

    private void teleportSelfToCoordinates(CommandSender sender, String[] args, int offset, String server) {
        if (!(sender instanceof Player self)) {
            send(sender, "&#D90429Console must use: /tp <player> <x> <y> <z> [server]");
            return;
        }

        TeleportModule.CoordinateTarget target = target(sender, args, offset, server, self.getWorld().getName(), self.getLocation().getYaw(), self.getLocation().getPitch());
        if (target == null) return;
        module.teleportToCoordinates(sender, localInfo(self), target);
    }

    private void teleportPlayerToCoordinates(CommandSender sender, String travellerName, String[] args, int offset, String server) {
        NetworkPlayerInfo traveller = resolveNetwork(sender, travellerName);
        if (traveller == null) return;

        String world = null;
        float yaw = 0.0f;
        float pitch = 0.0f;
        Player localTraveller = org.bukkit.Bukkit.getPlayer(traveller.getUuid());
        if (localTraveller != null && module.currentServer().equalsIgnoreCase(server)) {
            world = localTraveller.getWorld().getName();
            yaw = localTraveller.getLocation().getYaw();
            pitch = localTraveller.getLocation().getPitch();
        }

        TeleportModule.CoordinateTarget target = target(sender, args, offset, server, world, yaw, pitch);
        if (target == null) return;
        module.teleportToCoordinates(sender, traveller, target);
    }

    private TeleportModule.CoordinateTarget target(CommandSender sender, String[] args, int offset,
                                                    String server, String world, float yaw, float pitch) {
        if (server == null || server.isBlank()) {
            send(sender, "&#D90429Target server is required.");
            return null;
        }

        Double x = parseNumber(sender, args[offset]);
        Double y = parseNumber(sender, args[offset + 1]);
        Double z = parseNumber(sender, args[offset + 2]);
        if (x == null || y == null || z == null) return null;

        return new TeleportModule.CoordinateTarget(server, world, x, y, z, yaw, pitch);
    }

    private NetworkPlayerInfo resolveNetwork(CommandSender sender, String name) {
        Optional<NetworkPlayerInfo> info = npm.findPlayer(name);
        if (info.isEmpty()) send(sender, "&#D90429Player &#FFD447" + name + " &#D90429is not online.");
        return info.orElse(null);
    }

    private NetworkPlayerInfo localInfo(Player player) {
        return new NetworkPlayerInfo(player.getUniqueId(), player.getName(), module.currentServer(), player.getWorld().getName());
    }

    private boolean areCoordinates(String[] args, int offset) {
        return offset + 2 < args.length
                && isNumber(args[offset])
                && isNumber(args[offset + 1])
                && isNumber(args[offset + 2]);
    }

    private boolean isNumber(String value) {
        try {
            return Double.isFinite(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private Double parseNumber(CommandSender sender, String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isFinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        send(sender, "&#D90429Invalid coordinate: &#FFD447" + value);
        return null;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatFormatter.color(message));
    }
}
