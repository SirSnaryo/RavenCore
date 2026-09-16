package com.kammoun.raven.continuum;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.continuum.listener.ContinuumSpawnLocationListener;
import com.kammoun.raven.library.continuum.ContinuumTransferStore;
import com.kammoun.raven.library.module.KModule;

public final class ContinuumModule extends KModule<RavenCore> {

    private boolean enabled;
    private boolean applyClientEntityId;
    private boolean networkEntityIdPoolEnabled;
    private int networkEntityIdMin;
    private int networkEntityIdMax;
    private boolean debug;
    private boolean entityIdReservationAvailable = true;

    public ContinuumModule() {
        super("continuum");
    }

    @Override
    public void onModuleEnable() {
        reloadSettings();
        ContinuumTransferStore store = new ContinuumTransferStore(
                api.getRedisMessenger().getConnection().getPool(),
                getLogger(),
                getConfig().getInt("transfer-metadata-ttl-seconds", 30)
        );
        registerEvent(new ContinuumSpawnLocationListener(api, this, store));
        if (enabled) {
            getLogger().info("[Continuum] Bukkit receiver enabled.");
        }
    }

    @Override
    public void onModuleDisable() {
    }

    @Override
    public void reload() {
        super.reload();
        reloadSettings();
    }

    private void reloadSettings() {
        enabled = getConfig().getBoolean("enabled", false);
        applyClientEntityId = getConfig().getBoolean("apply-client-entity-id", true);
        networkEntityIdPoolEnabled = getConfig().getBoolean("network-entity-id-pool.enabled", true);
        networkEntityIdMin = getConfig().getInt("network-entity-id-pool.min-id", 1_000_000_000);
        networkEntityIdMax = getConfig().getInt("network-entity-id-pool.max-id", 1_100_000_000);
        debug = getConfig().getBoolean("debug", false);
        entityIdReservationAvailable = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldApplyClientEntityId() {
        return enabled && applyClientEntityId && entityIdReservationAvailable;
    }

    public boolean isNetworkEntityId(int entityId) {
        return networkEntityIdPoolEnabled
                && entityId >= networkEntityIdMin
                && entityId <= networkEntityIdMax;
    }

    public void markEntityIdReservationUnavailable(String reason) {
        if (!entityIdReservationAvailable) {
            return;
        }
        entityIdReservationAvailable = false;
        warn("Disabling client entity id reservation for this runtime: " + reason);
    }

    public void debug(String message) {
        if (debug) {
            getLogger().info("[Continuum] " + message);
        }
    }

    public void warn(String message) {
        getLogger().warning("[Continuum] " + message);
    }
}
