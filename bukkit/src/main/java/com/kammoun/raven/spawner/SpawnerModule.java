package com.kammoun.raven.spawner;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.spawner.command.SpawnerCommand;
import com.kammoun.raven.spawner.config.SpawnerConfig;
import com.kammoun.raven.spawner.database.SpawnerDB;
import com.kammoun.raven.spawner.gui.SpawnerSellMenu;
import com.kammoun.raven.spawner.gui.SpawnerStorageMenu;
import com.kammoun.raven.spawner.listener.SpawnerListener;
import com.kammoun.raven.spawner.manager.SpawnerRegistry;
import com.kammoun.raven.spawner.manager.SpawnerStackManager;
import com.kammoun.raven.spawner.manager.SpawnerTickManager;
import lombok.Getter;

import java.io.File;
import java.sql.SQLException;

@Getter
public class SpawnerModule extends KModule<RavenCore> {

    private SpawnerConfig spawnerConfig;
    private SpawnerRegistry registry;
    private SpawnerStackManager stackManager;
    private SpawnerDB database;
    private SpawnerTickManager tickManager;
    private SpawnerStorageMenu storageMenu;
    private SpawnerSellMenu sellMenu;
    private SchedulerUtil.Task saveTask;
    private boolean runtimeStarted;

    // Default spawner types shipped with the plugin, each its own file under spawners/.
    private static final String[] DEFAULT_SPAWNER_TYPES = {
            "cow", "skeleton", "zombie", "spider", "creeper", "sheep",
            "pig", "zombified_piglin", "iron_golem", "blaze", "magma_cube"
    };

    public SpawnerModule() {
        super("spawner");
    }

    @Override
    public void onModuleEnable() {
        // Ensure default configs are on disk
        for (String type : DEFAULT_SPAWNER_TYPES) {
            saveDefaultConfig("spawners/" + type + ".yml");
        }
        saveDefaultConfig("gui/sell.yml");

        this.spawnerConfig = new SpawnerConfig(this);
        this.registry       = new SpawnerRegistry(getLogger());
        this.stackManager   = new SpawnerStackManager();

        // Load spawner type definitions — one file per type under spawners/
        File spawnersDir = new File(getDataFolder(), "spawners");
        registry.load(spawnersDir, api);

        // Connect database first; runtime listeners/tick start only after persisted stacks load.
        try {
            this.database = new SpawnerDB(this, registry, stackManager);
        } catch (SQLException e) {
            getLogger().severe("[SpawnerModule] Failed to initialise database: " + e.getMessage());
        }

        // Build GUI services
        this.storageMenu = new SpawnerStorageMenu();
        this.sellMenu    = new SpawnerSellMenu(this);

        if (database != null) {
            SchedulerUtil.runAsync(() -> {
                var loaded = database.readAll();
                SchedulerUtil.run(() -> {
                    int count = database.applyLoaded(loaded);
                    getLogger().info("[SpawnerModule] Loaded " + count + " placed spawner stack(s).");
                    startRuntime();
                });
            });
        } else {
            startRuntime();
        }
    }

    @Override
    public void onModuleDisable() {
        if (saveTask != null) saveTask.cancel();
        if (tickManager != null) tickManager.stop();

        if (database != null) {
            database.saveAll(stackManager.all());
            database.close();
        }

        stackManager.clear();
    }

    @Override
    public void reload() {
        spawnerConfig.reload();

        File spawnersDir = new File(getDataFolder(), "spawners");
        registry.load(spawnersDir, api);

        sellMenu.reload();
    }

    public RavenEconomy getEconomy() {
        return api.getEconomyModule() != null ? api.getEconomyModule().getEconomy() : null;
    }

    public void saveDirtyNow() {
        if (database != null) {
            SchedulerUtil.runAsync(() -> database.saveDirty(stackManager.all()));
        }
    }

    private void startRuntime() {
        if (runtimeStarted) return;
        runtimeStarted = true;

        this.tickManager = new SpawnerTickManager(spawnerConfig, stackManager);
        tickManager.start();

        saveTask = SchedulerUtil.runAsyncTimer(() -> {
            if (database != null) database.saveDirty(stackManager.all());
        }, 600L, 600L);

        registerEvent(new SpawnerListener(this));
        registerCommand(new SpawnerCommand(this), "spawner");
    }
}
