package com.kammoun.raven.continuum.listener;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.continuum.ContinuumEntityIdReservation;
import com.kammoun.raven.continuum.ContinuumModule;
import com.kammoun.raven.library.continuum.ContinuumTransferMetadata;
import com.kammoun.raven.library.continuum.ContinuumTransferStore;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public final class ContinuumSpawnLocationListener implements Listener {

    private final RavenCore plugin;
    private final ContinuumModule module;
    private final ContinuumTransferStore store;
    private final ContinuumEntityIdReservation reservation = new ContinuumEntityIdReservation();

    public ContinuumSpawnLocationListener(RavenCore plugin, ContinuumModule module, ContinuumTransferStore store) {
        this.plugin = plugin;
        this.module = module;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (!module.shouldApplyClientEntityId()) {
            return;
        }

        PlayerConfigurationConnection connection = event.getConnection();
        UUID playerId = connection.getProfile().getId();
        if (playerId == null) {
            return;
        }

        ContinuumTransferMetadata metadata = store.consume(playerId, plugin.getServerId());
        if (metadata == null) {
            return;
        }

        if (!module.isNetworkEntityId(metadata.clientEntityId())) {
            module.debug("Skipping client entity id " + metadata.clientEntityId()
                    + " for " + connection.getProfile().getName()
                    + " because it is outside the configured network entity-id pool.");
            return;
        }

        ContinuumEntityIdReservation.Result result = reservation.reserve(connection, metadata.clientEntityId());
        if (result.reserved()) {
            module.debug("Reserved client entity id " + metadata.clientEntityId()
                    + " for " + connection.getProfile().getName());
        } else {
            module.markEntityIdReservationUnavailable(result.failureReason());
            module.debug("Continuing with normal switch for " + connection.getProfile().getName()
                    + " after entity id reservation failed.");
        }
    }
}
