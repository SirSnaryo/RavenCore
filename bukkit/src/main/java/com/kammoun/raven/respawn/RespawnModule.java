package com.kammoun.raven.respawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.respawn.db.BedSpawnRepository;
import com.kammoun.raven.respawn.db.BedSpawnTable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

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
        RespawnConfig config = RespawnConfig.load(getConfig());
        Location defaultSpawn = resolveDefaultSpawn(config.defaultSpawn());
        manager = new RespawnManager(api, repository, api.getLocationPoolModule().getPool(),
                transfers, config, defaultSpawn);
        registerEvent(new BedSpawnListener(api, repository));
        registerEvent(manager);
    }

    private Location resolveDefaultSpawn(RespawnConfig.DefaultSpawn configured) {
        World world = Bukkit.getWorld(configured.world());
        if (world == null) {
            throw new IllegalArgumentException("[Respawn] Configured default-spawn world is not loaded: "
                    + configured.world());
        }
        if (world.getEnvironment() != World.Environment.NORMAL) {
            throw new IllegalArgumentException("[Respawn] Configured default-spawn world must be an Overworld: "
                    + configured.world());
        }
        return new Location(world, configured.x(), configured.y(), configured.z(),
                configured.yaw(), configured.pitch());
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
