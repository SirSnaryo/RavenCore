package com.kammoun.raven.rtp.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import com.kammoun.raven.library.util.CooldownManager;
import com.kammoun.raven.rtp.config.RtpMessages;
import com.kammoun.raven.rtp.pool.PooledLocation;
import com.kammoun.raven.rtp.pool.RtpPoolStore;
import com.kammoun.raven.rtp.transfer.RtpTransferHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;


public class RtpService {

    private static final String COOLDOWN_KEY = "rtp";

    private final RavenCore api;
    private final CooldownManager cooldowns = new CooldownManager();
    private final RtpPoolStore poolStore;
    private final RtpMessages messages;
    private final BungeeHelper bungeeHelper;
    private final int cooldownSeconds;

    public RtpService(RavenCore api, RtpPoolStore poolStore, RtpMessages messages,
                       BungeeHelper bungeeHelper, int cooldownSeconds) {
        this.api = api;
        this.poolStore = poolStore;
        this.messages = messages;
        this.bungeeHelper = bungeeHelper;
        this.cooldownSeconds = cooldownSeconds;
    }

    /**
     * {@code poolId} identifies which pool to pop from (a destination id, since one server can
     * back more than one pool); {@code targetServer} is where to actually send the player.
     * {@code destinationLabel} is only used in the messages sent back to the player.
     */
    public void requestTeleport(Player player, String poolId, String targetServer, String destinationLabel) {
        if (cooldowns.isOnCooldown(player, COOLDOWN_KEY)) {
            player.sendMessage(messages.format("cooldown",
                    "seconds", String.valueOf(cooldowns.getRemainingSeconds(player, COOLDOWN_KEY))));
            return;
        }

        player.closeInventory();
        Bukkit.getAsyncScheduler().runNow(api, task -> {
            Optional<PooledLocation> destination = poolStore.pop(poolId);
            player.getScheduler().run(api,
                    t -> onDestinationResolved(player, targetServer, destinationLabel, destination), null);
        });
    }

    private void onDestinationResolved(Player player, String targetServer, String destinationLabel,
                                        Optional<PooledLocation> destination) {
        if (destination.isEmpty()) {
            player.sendMessage(messages.format("pool-empty", "destination", destinationLabel));
            return;
        }

        // Only burn the cooldown once a destination was actually found.
        cooldowns.setCooldown(player, COOLDOWN_KEY, cooldownSeconds);
        player.sendMessage(messages.format("teleporting", "destination", destinationLabel));

        PooledLocation loc = destination.get();
        if (targetServer.equals(api.getServerId())) {
            World world = Bukkit.getWorld(loc.world);
            if (world == null) {
                player.sendMessage(messages.get("world-missing"));
                return;
            }
            player.teleportAsync(new Location(world, loc.x, loc.y, loc.z, loc.yaw, 0f));
            return;
        }

        RtpTransferHelper.transfer(api, api.getRedisMessenger().getConnection().getPool(),
                bungeeHelper, player, loc, targetServer);
    }
}
