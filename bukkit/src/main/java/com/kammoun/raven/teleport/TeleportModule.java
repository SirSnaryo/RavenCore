package com.kammoun.raven.teleport;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TeleportModule extends KModule<RavenCore> {

    /** playerUuid → destinationUuid, waiting for the player to arrive on this server. */
    private final Map<UUID, UUID> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportModule() {
        super("teleport");
    }

    @Override
    public void onModuleEnable() {
        api.getNetworkEventBus().subscribe(CrossServerTeleportEvent.class, this::onCrossServerTeleport);
        onEvent(PlayerJoinEvent.class, this::onJoin);
        registerCommand(new TeleportCommand(this, api.getNetworkPlayerManager()), "tp");
        getLogger().info("[Teleport] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        api.getNetworkEventBus().unsubscribe(CrossServerTeleportEvent.class);
        pendingTeleports.clear();
    }

    // -------------------------------------------------------------------------

    /**
     * Main entry point. Traveller UUID must belong to a player on THIS server.
     * Destination is described by a NetworkPlayerInfo (can be on any server).
     */
    void teleport(CommandSender sender, UUID travellerUuid, String travellerName, NetworkPlayerInfo dest) {
        Player traveller = Bukkit.getPlayer(travellerUuid);
        if (traveller == null) {
            sender.sendMessage("§e" + travellerName + " §cis no longer online.");
            return;
        }

        Player localDest = Bukkit.getPlayer(dest.getUuid());

        if (localDest != null) {
            // Both on this server — direct teleport
            traveller.teleportAsync(localDest.getLocation());
            sender.sendMessage("§aTeleported §e" + travellerName + " §ato §e" + dest.getPlayerName() + "§a.");
        } else {
            // Destination is on another server — publish pending teleport then send traveller over
            CrossServerTeleportEvent event = new CrossServerTeleportEvent();
            event.setPlayerUuid(travellerUuid.toString());
            event.setDestinationUuid(dest.getUuid().toString());
            api.getNetworkEventBus().publish(event);

            new BungeeHelper(api).connectToServer(traveller, dest.getServerId());
            sender.sendMessage("§aSending §e" + travellerName + " §ato §e" + dest.getPlayerName() + "§a's server...");
        }
    }

    // -------------------------------------------------------------------------

    private void onCrossServerTeleport(CrossServerTeleportEvent event) {
        UUID playerUuid = UUID.fromString(event.getPlayerUuid());
        UUID destUuid   = UUID.fromString(event.getDestinationUuid());

        Player destination = Bukkit.getPlayer(destUuid);
        if (destination == null) return; // destination not on this server, ignore

        Player traveller = Bukkit.getPlayer(playerUuid);
        if (traveller != null) {
            traveller.teleportAsync(destination.getLocation());
        } else {
            pendingTeleports.put(playerUuid, destUuid);
        }
    }

    private void onJoin(PlayerJoinEvent event) {
        UUID destUuid = pendingTeleports.remove(event.getPlayer().getUniqueId());
        if (destUuid == null) return;
        Player destination = Bukkit.getPlayer(destUuid);
        if (destination == null) return;
        // Small delay to let the joining player fully load in before teleporting.
        // Uses the player's entity scheduler (not the legacy Bukkit scheduler) so this works on Folia too.
        event.getPlayer().getScheduler().runDelayed(api,
                task -> event.getPlayer().teleportAsync(destination.getLocation()), null, 5L);
    }

    List<String> tabPlayers(String prefix) {
        return api.getNetworkPlayerManager().getNetworkPlayerNames().stream()
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
