package com.kammoun.raven.respawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.respawn.db.BedSpawnRepository;
import com.kammoun.raven.respawn.db.BedSpawnTable;

public final class RespawnModule extends KModule<RavenCore> {
    private RespawnManager manager;

    public RespawnModule() {
        super("respawn");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().severe("[Respawn] Disabled because MySQL is unavailable.");
            return;
        }
        new BedSpawnTable(api.getMySqlManager()).createTable();
        BedSpawnRepository repository = new BedSpawnRepository(api.getMySqlManager());
        RespawnTransferStore transfers = new RespawnTransferStore(api,
                api.getRedisMessenger().getConnection().getPool());
        manager = new RespawnManager(api, repository, api.getLocationPoolModule().getPool(),
                transfers, RespawnConfig.load(getConfig()));
        registerEvent(new BedSpawnListener(api, repository));
        registerEvent(manager);
    }

    @Override
    public void onModuleDisable() {
        if (manager != null) manager.stop();
    }

    @Override
    public void reload() {
        reloadConfig();
        getLogger().info("[Respawn] Configuration changes apply after a server restart.");
    }
}
