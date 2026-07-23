package com.kammoun.raven.locationPool;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.LocationSearchBounds;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.ServerRole;

import com.kammoun.raven.locationPool.config.LocationPoolConfig;
import com.kammoun.raven.locationPool.controller.LocationPoolRefillController;
import com.kammoun.raven.locationPool.generator.SafeLocationGenerator;
import com.kammoun.raven.locationPool.protection.AllowAllLocationProtection;
import com.kammoun.raven.locationPool.redis.RedisLocationPool;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.List;

@Getter
public final class LocationPoolModule extends KModule<RavenCore> {

    private RedisLocationPool locationPool;
    private LocationPoolConfig poolConfig;
    private LocationPoolRefillController refillController;
    private boolean generationEnabled;
    private boolean waitingForWorldsLogged;

    public LocationPoolModule() {
        super("locationpool");
    }

    @Override
    public void onModuleEnable() {
        removeObsoleteLimits();
        locationPool = new RedisLocationPool(
                api.getRedisMessenger().getConnection().getPool(), getLogger());
        onEvent(WorldLoadEvent.class, event -> tryStartGeneration());
        prepareGeneration();
    }

    @Override
    public void onModuleDisable() {
        stopGeneration();
    }

    @Override
    public void reload() {
        stopGeneration();
        reloadConfig();
        api.reloadConfig();
        prepareGeneration();
    }

    public LocationPool getPool() {
        return locationPool;
    }

    private void removeObsoleteLimits() {
        String base = "location-library.";
        boolean changed = false;
        for (String key : List.of("pool-target-size", "max-refill-per-cycle",
                "max-attempts-per-location")) {
            String path = base + key;
            if (getConfig().contains(path)) {
                getConfig().set(path, null);
                changed = true;
            }
        }
        if (changed) {
            saveConfig();
            getLogger().info("[LocationPool] Removed obsolete generation limits from config.yml.");
        }
    }

    private void prepareGeneration() {
        generationEnabled = false;
        waitingForWorldsLogged = false;
        poolConfig = null;
        if (api.getServerRole() != ServerRole.SURVIVAL && api.getServerRole() != ServerRole.END) {
            return;
        }

        try {
            poolConfig = LocationPoolConfig.load(
                    getConfig(), api.getConfig(), getLogger(), api.getServerRole());
            tryStartGeneration();
        } catch (IllegalArgumentException e) {
            poolConfig = null;
            refillController = null;
            getLogger().severe("[LocationPool] Generation disabled: " + e.getMessage());
        }
    }

    private void tryStartGeneration() {
        if (poolConfig == null || refillController != null || generationEnabled) {
            return;
        }

        List<LocationDimension> missing = poolConfig.dimensions().stream()
                .filter(dimension -> findWorld(dimension) == null)
                .toList();
        if (!missing.isEmpty()) {
            if (!waitingForWorldsLogged) {
                getLogger().info("[LocationPool] Waiting for worlds to load before generating pools: "
                        + missing.stream().map(LocationDimension::redisName).toList() + ".");
                waitingForWorldsLogged = true;
            }
            return;
        }

        try {
            for (LocationDimension dimension : poolConfig.dimensions()) {
                validateWorldBounds(dimension);
            }
        } catch (IllegalArgumentException e) {
            poolConfig = null;
            getLogger().severe("[LocationPool] Generation disabled: " + e.getMessage());
            return;
        }

        SafeLocationGenerator generator = new SafeLocationGenerator(api, api.getServerId(), poolConfig,
                new AllowAllLocationProtection());
        poolConfig.dimensions().forEach(dimension ->
                locationPool.resetIfDefinitionChanged(
                        api.getServerId(), dimension, poolConfig.bounds(dimension)));
        registerServerPools();
        refillController = new LocationPoolRefillController(api.getServerId(), poolConfig,
                locationPool, generator, getLogger());
        refillController.start();
        generationEnabled = true;
        getLogger().info("[LocationPool] All required worlds are loaded. Filling pools asynchronously for "
                + api.getServerId() + ".");
    }

    private void registerServerPools() {
        String cluster = api.getConfig().getString("network.cluster", "").trim();
        int cellX = api.getConfig().getInt("grid.cell-x", 0);
        int cellZ = api.getConfig().getInt("grid.cell-z", 0);
        if (locationPool.registerServer(cluster, cellX, cellZ, api.getServerId(),
                poolConfig.dimensions())) {
            getLogger().info("[LocationPool] Registered " + api.getServerId()
                    + " in Redis grid cluster " + cluster + " at cell "
                    + cellX + ":" + cellZ + " for "
                    + poolConfig.dimensions().stream()
                    .map(LocationDimension::registryName).toList() + ".");
        } else {
            getLogger().severe("[LocationPool] RTP cluster discovery is unavailable because "
                    + api.getServerId() + " could not be registered in the Redis grid.");
        }
    }

    private void stopGeneration() {
        generationEnabled = false;
        waitingForWorldsLogged = false;
        if (refillController != null) {
            refillController.stop();
            refillController = null;
        }
    }

    private void validateWorldBounds(LocationDimension dimension) {
        World world = findWorld(dimension);
        if (world == null) {
            throw new IllegalArgumentException("No " + dimension.redisName() + " world is loaded on this server");
        }
        LocationSearchBounds bounds = poolConfig.bounds(dimension);
        if (bounds.minY() < world.getMinHeight() || bounds.maxY() > world.getMaxHeight()) {
            throw new IllegalArgumentException("LocationPool " + dimension.redisName()
                    + " Y bounds must fit world range [" + world.getMinHeight() + ","
                    + world.getMaxHeight() + ")");
        }
    }

    private World findWorld(LocationDimension dimension) {
        World.Environment environment = switch (dimension) {
            case OVERWORLD -> World.Environment.NORMAL;
            case NETHER -> World.Environment.NETHER;
            case END -> World.Environment.THE_END;
        };
        return api.getServer().getWorlds().stream()
                .filter(candidate -> candidate.getEnvironment() == environment)
                .findFirst()
                .orElse(null);
    }
}
