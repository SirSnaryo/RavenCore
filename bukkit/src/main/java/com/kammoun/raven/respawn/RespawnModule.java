package com.kammoun.raven.respawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.home.db.HomeRepository;
import com.kammoun.raven.home.db.HomeTable;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.respawn.db.BedSpawnRepository;
import com.kammoun.raven.respawn.db.BedSpawnTable;
import com.kammoun.raven.spawn.db.LocationRepository;
import com.kammoun.raven.spawn.db.LocationsTable;

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
        new HomeTable(api.getMySqlManager()).createTable();
        new LocationsTable(api.getMySqlManager()).createTable();
        BedSpawnRepository repository = new BedSpawnRepository(api.getMySqlManager());
        HomeRepository homes = new HomeRepository(api.getMySqlManager());
        LocationRepository spawnLocations = new LocationRepository(api.getMySqlManager());
        RespawnTransferStore transfers = new RespawnTransferStore(api,
                api.getRedisMessenger().getConnection().getPool());
        RespawnConfig config = RespawnConfig.load(getConfig());
        manager = new RespawnManager(api, repository, homes, spawnLocations, transfers, config);
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
