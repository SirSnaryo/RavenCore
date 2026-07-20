package com.kammoun.raven.teleport;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TeleportModule extends KModule<RavenCore> {

    /** playerUuid -> destinationUuid, waiting for the player to arrive on this server. */
    private final Map<UUID, UUID> pendingPlayerTeleports = new ConcurrentHashMap<>();
    /** playerUuid -> coordinates, waiting for the player to arrive on this server. */
    private final Map<UUID, CoordinateTarget> pendingCoordinateTeleports = new ConcurrentHashMap<>();

    public TeleportModule() {
        super("teleport");
    }

    @Override
    public void onModuleEnable() {
        api.getNetworkEventBus().subscribe(CrossServerTeleportEvent.class, this::onCrossServerTeleport);
        onEvent(PlayerJoinEvent.class, this::onJoin);
        registerCommand(new TeleportCommand(this, api.getNetworkPlayerManager()), "tp");
        registerCommand(new TeleportHereCommand(this, api.getNetworkPlayerManager()), "tphere");
        getLogger().info("[Teleport] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        api.getNetworkEventBus().unsubscribe(CrossServerTeleportEvent.class);
        pendingPlayerTeleports.clear();
        pendingCoordinateTeleports.clear();
    }

    void teleportToPlayer(CommandSender sender, NetworkPlayerInfo traveller, NetworkPlayerInfo destination) {
        Player localTraveller = Bukkit.getPlayer(traveller.getUuid());
        Player localDestination = Bukkit.getPlayer(destination.getUuid());

        if (localTraveller != null && localDestination != null) {
            localTraveller.teleportAsync(localDestination.getLocation());
            send(sender, "&#33FF66Teleported &#FFD447" + traveller.getPlayerName()
                    + " &#33FF66to &#FFD447" + destination.getPlayerName() + "&#33FF66.");
            return;
        }

        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(traveller.getUuid().toString());
        event.setDestinationUuid(destination.getUuid().toString());
        event.setTargetServer(destination.getServerId());
        api.getNetworkEventBus().publish(event);

        send(sender, "&#33FF66Sending &#FFD447" + traveller.getPlayerName()
                + " &#33FF66to &#FFD447" + destination.getPlayerName() + "&#33FF66...");
    }

    void teleportToCoordinates(CommandSender sender, NetworkPlayerInfo traveller, CoordinateTarget target) {
        Player localTraveller = Bukkit.getPlayer(traveller.getUuid());

        if (localTraveller != null && api.getServerId().equalsIgnoreCase(target.server())) {
            if (teleportLocal(sender, localTraveller, target)) {
                send(sender, "&#33FF66Teleported &#FFD447" + traveller.getPlayerName()
                        + " &#33FF66to &#FFFFFF" + target.shortLabel() + "&#33FF66.");
            }
            return;
        }

        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(traveller.getUuid().toString());
        event.setTargetServer(target.server());
        event.setWorld(target.world());
        event.setX(target.x());
        event.setY(target.y());
        event.setZ(target.z());
        event.setYaw(target.yaw());
        event.setPitch(target.pitch());
        api.getNetworkEventBus().publish(event);

        send(sender, "&#33FF66Sending &#FFD447" + traveller.getPlayerName()
                + " &#33FF66to &#FFFFFF" + target.shortLabel() + "&#33FF66.");
    }

    private void onCrossServerTeleport(CrossServerTeleportEvent event) {
        if (event.getTargetServer() != null && !api.getServerId().equalsIgnoreCase(event.getTargetServer())) {
            return;
        }

        UUID playerUuid = UUID.fromString(event.getPlayerUuid());
        if (event.hasCoordinates()) {
            CoordinateTarget target = new CoordinateTarget(
                    event.getTargetServer() == null ? api.getServerId() : event.getTargetServer(),
                    event.getWorld(),
                    event.getX(),
                    event.getY(),
                    event.getZ(),
                    event.getYaw() == null ? 0.0f : event.getYaw(),
                    event.getPitch() == null ? 0.0f : event.getPitch()
            );
            Player traveller = Bukkit.getPlayer(playerUuid);
            if (traveller != null) {
                teleportLocal(Bukkit.getConsoleSender(), traveller, target);
            } else {
                pendingCoordinateTeleports.put(playerUuid, target);
            }
            return;
        }

        if (event.getDestinationUuid() == null) return;
        UUID destUuid = UUID.fromString(event.getDestinationUuid());
        Player destination = Bukkit.getPlayer(destUuid);
        if (destination == null) return;

        Player traveller = Bukkit.getPlayer(playerUuid);
        if (traveller != null) {
            traveller.teleportAsync(destination.getLocation());
        } else {
            pendingPlayerTeleports.put(playerUuid, destUuid);
        }
    }

    private void onJoin(PlayerJoinEvent event) {
        CoordinateTarget coordinateTarget = pendingCoordinateTeleports.remove(event.getPlayer().getUniqueId());
        if (coordinateTarget != null) {
            event.getPlayer().getScheduler().runDelayed(api,
                    task -> teleportLocal(Bukkit.getConsoleSender(), event.getPlayer(), coordinateTarget), null, 20L);
            return;
        }

        UUID destUuid = pendingPlayerTeleports.remove(event.getPlayer().getUniqueId());
        if (destUuid == null) return;
        Player destination = Bukkit.getPlayer(destUuid);
        if (destination == null) return;
        event.getPlayer().getScheduler().runDelayed(api,
                task -> event.getPlayer().teleportAsync(destination.getLocation()), null, 20L);
    }

    private boolean teleportLocal(CommandSender sender, Player player, CoordinateTarget target) {
        World world = target.world() == null || target.world().isBlank()
                ? player.getWorld()
                : Bukkit.getWorld(target.world());
        if (world == null) {
            send(sender, "&#D90429World &#FFD447" + target.world() + " &#D90429is not loaded.");
            return false;
        }
        Location location = new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        player.teleportAsync(location);
        return true;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatFormatter.color(message));
    }

    List<String> tabPlayers(String prefix) {
        return api.getNetworkPlayerManager().completePlayerNames(prefix);
    }

    List<String> tabPlayers(String prefix, String excludedName) {
        return api.getNetworkPlayerManager().completePlayerNames(prefix, excludedName);
    }

    List<String> tabServers(String prefix) {
        String lower = prefix.toLowerCase();
        return Stream.concat(
                        Stream.of(api.getServerId()),
                        api.getNetworkPlayerManager().getOnlineNetworkPlayers().stream().map(NetworkPlayerInfo::getServerId)
                )
                .filter(server -> server != null && server.toLowerCase().startsWith(lower))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    String currentServer() {
        return api.getServerId();
    }

    record CoordinateTarget(String server, String world, double x, double y, double z, float yaw, float pitch) {
        String shortLabel() {
            return trim(x) + " " + trim(y) + " " + trim(z) + " @ " + server;
        }

        private static String trim(double value) {
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        }
    }
}
