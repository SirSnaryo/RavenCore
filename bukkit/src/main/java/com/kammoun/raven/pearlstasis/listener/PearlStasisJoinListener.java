package com.kammoun.raven.pearlstasis.listener;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.pearlstasis.PearlStasisRecord;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.pearlstasis.config.PearlStasisConfig;
import com.kammoun.raven.pearlstasis.db.PearlStasisRepository;
import com.kammoun.raven.pearlstasis.store.PearlStasisStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class PearlStasisJoinListener implements Listener {

    private static final int MAX_ATTEMPTS = 6;
    private static final long RETRY_DELAY_TICKS = 10L;

    private final RavenCore plugin;
    private final PearlStasisConfig config;
    private final PearlStasisRepository repository;
    private final PearlStasisStore store;

    public PearlStasisJoinListener(RavenCore plugin, PearlStasisConfig config,
                                   PearlStasisRepository repository, PearlStasisStore store) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        consumePending(player, 1);
    }

    private void consumePending(Player player, int attempt) {
        UUID playerUuid = player.getUniqueId();
        SchedulerUtil.runAsync(() -> {
            try {
                boolean marked = store.hasMarker(playerUuid);
                Optional<PearlStasisRecord> pendingOpt = repository.consumeOldestPending(
                        playerUuid, plugin.getServerId(), config.getTtlMillis());

                if (pendingOpt.isEmpty()) {
                    if (marked && attempt < MAX_ATTEMPTS) {
                        SchedulerUtil.runAsyncLater(() -> consumePending(player, attempt + 1), RETRY_DELAY_TICKS);
                    } else if (!repository.hasPending(playerUuid, config.getTtlMillis())) {
                        store.clearMarker(playerUuid);
                    }
                    return;
                }

                PearlStasisRecord record = pendingOpt.get();

                // If no more pending landings remain for this player, clear the fast-gate marker
                if (!repository.hasPending(playerUuid, config.getTtlMillis())) {
                    store.clearMarker(playerUuid);
                }

                SchedulerUtil.execute(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    World world = Bukkit.getWorld(record.world());
                    if (world == null) {
                        plugin.getLogger().warning("[PearlStasis] Target world '" + record.world()
                                + "' not found for stasis teleport of " + player.getName());
                        return;
                    }

                    Location loc = new Location(world, record.x(), record.y(), record.z(),
                            record.yaw(), record.pitch());

                    if (plugin.getBorderModule() != null) {
                        plugin.getBorderModule().suppressTransfers(playerUuid, 5_000L);
                    }

                    player.teleportAsync(loc).whenComplete((success, error) -> {
                        if (Boolean.TRUE.equals(success)) {
                            config.playArrivalSound(player);
                        }
                    });
                });
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().warning("[PearlStasis] Failed to consume stasis landing for "
                        + player.getName() + ": " + e.getMessage());
            }
        });
    }
}
