package com.kammoun.raven.pearlstasis;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.pearlstasis.config.PearlStasisConfig;
import com.kammoun.raven.pearlstasis.db.PearlStasisRepository;
import com.kammoun.raven.pearlstasis.db.PearlStasisTable;
import com.kammoun.raven.pearlstasis.listener.PearlStasisImpactListener;
import com.kammoun.raven.pearlstasis.listener.PearlStasisJoinListener;
import com.kammoun.raven.pearlstasis.store.PearlStasisStore;
import lombok.Getter;

import java.sql.SQLException;
import java.util.UUID;

@Getter
public final class PearlStasisModule extends KModule<RavenCore> {

    private PearlStasisConfig pearlStasisConfig;
    private PearlStasisTable table;
    private PearlStasisRepository repository;
    private PearlStasisStore store;
    private SchedulerUtil.Task cleanupTask;

    public PearlStasisModule() {
        super("pearlstasis");
    }

    @Override
    public void onModuleEnable() {
        pearlStasisConfig = new PearlStasisConfig(api.getConfig());
        table = new PearlStasisTable(api.getMySqlManager());
        table.createTable();
        repository = new PearlStasisRepository(api.getMySqlManager());
        store = new PearlStasisStore(
                api.getRedisMessenger().getConnection().getPool(),
                getLogger()
        );

        registerEvent(new PearlStasisImpactListener(api, pearlStasisConfig, repository, store, api.getNetworkPlayerManager()));
        registerEvent(new PearlStasisJoinListener(api, pearlStasisConfig, repository, store));

        if (pearlStasisConfig.isExpirePendingRecords()) {
            cleanupTask = SchedulerUtil.runAsyncTimer(this::cleanupExpired, 6000L, 6000L);
        }

        getLogger().info("[PearlStasis] Enabled cross-server pearl stasis module (enabled="
                + pearlStasisConfig.isEnabled() + ", TTL=" + pearlStasisConfig.getTtlSeconds()
                + "s, expire=" + pearlStasisConfig.isExpirePendingRecords()
                + ", debug=" + pearlStasisConfig.isDebug() + ").");
    }

    @Override
    public void onModuleDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    @Override
    public void reload() {
        pearlStasisConfig = new PearlStasisConfig(api.getConfig());
        getLogger().info("[PearlStasis] Reloaded configuration.");
    }

    public boolean hasPendingStasis(UUID playerUuid) {
        if (store != null && store.hasMarker(playerUuid)) {
            return true;
        }
        if (repository != null) {
            try {
                return repository.hasPending(playerUuid, pearlStasisConfig != null ? pearlStasisConfig.getTtlMillis() : -1L);
            } catch (SQLException e) {
                getLogger().warning("[PearlStasis] Failed to check pending stasis: " + e.getMessage());
            }
        }
        return false;
    }

    private void cleanupExpired() {
        if (repository == null || pearlStasisConfig == null) {
            return;
        }
        try {
            int deleted = repository.deleteExpired(pearlStasisConfig.getTtlMillis());
            if (deleted > 0) {
                getLogger().info("[PearlStasis] Cleaned up " + deleted + " expired stasis landing records.");
            }
        } catch (SQLException e) {
            getLogger().warning("[PearlStasis] Failed to clean up expired stasis records: " + e.getMessage());
        }
    }
}
