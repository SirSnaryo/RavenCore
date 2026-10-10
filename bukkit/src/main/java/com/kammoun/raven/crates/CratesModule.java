package com.kammoun.raven.crates;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.crates.command.CrateCommand;
import com.kammoun.raven.crates.config.CratesConfig;
import com.kammoun.raven.crates.db.CrateKeyRepository;
import com.kammoun.raven.crates.db.CrateLocationRepository;
import com.kammoun.raven.crates.db.CrateTables;
import com.kammoun.raven.crates.db.CrateTimerRepository;
import com.kammoun.raven.crates.listener.CrateListener;
import com.kammoun.raven.crates.papi.CratesPlaceholders;
import com.kammoun.raven.crates.player.CratePlayerManager;
import com.kammoun.raven.crates.service.CrateService;
import com.kammoun.raven.crates.service.CrateTimerService;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import lombok.Getter;

@Getter
public final class CratesModule extends KModule<RavenCore> {
    private CratesConfig configModel;
    private CrateService service;
    private CrateTimerService timerService;
    private CratePlayerManager playerManager;

    public CratesModule() {
        super("crates");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().severe("[Crates] Disabled because MySQL is unavailable.");
            return;
        }
        configModel = new CratesConfig(api);
        configModel.load();
        if (!configModel.enabled()) {
            getLogger().info("[Crates] Disabled by config.");
            return;
        }
        new CrateTables(api.getMySqlManager()).create();
        CrateKeyRepository keys = new CrateKeyRepository(api.getMySqlManager());
        CrateLocationRepository locations = new CrateLocationRepository(api.getMySqlManager(), api.getServerId());
        CrateTimerRepository timers = new CrateTimerRepository(api.getMySqlManager());
        playerManager = new CratePlayerManager(api, keys);
        service = new CrateService(api, configModel, keys, locations, playerManager);
        timerService = new CrateTimerService(api, configModel, timers, keys, playerManager);
        service.loadPlacedCrates();
        playerManager.loadOnline();
        timerService.start();
        if (KPlaceholderExpansion.isAvailable()) {
            new CratesPlaceholders(api, configModel, timerService, playerManager).register();
        }
        
        registerEvent(new CrateListener(configModel, service));
        if (api.getCommand("crates") != null) {
            CrateCommand command = new CrateCommand(this);
            api.getCommand("crates").setExecutor(command);
            api.getCommand("crates").setTabCompleter(command);
        }
        getLogger().info("[Crates] Loaded " + configModel.crates().size() + " crate definitions.");
    }

    @Override
    public void onModuleDisable() {
        if (timerService != null) timerService.stop();
        if (playerManager != null) playerManager.clear();
    }

    @Override
    public void reload() {
        if (timerService != null) timerService.stop();
        configModel.load();
        service.loadPlacedCrates();
        playerManager.loadOnline();
        timerService.start();
    }
}
