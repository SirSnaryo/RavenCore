package com.kammoun.raven.devmode;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.devmode.command.DevModeCommand;
import com.kammoun.raven.devmode.map.DevMapService;
import com.kammoun.raven.library.module.KModule;
import lombok.Getter;

@Getter
public class DevModeModule extends KModule<RavenCore> {

    private DevModeService service;
    private DevMapService mapService;

    public DevModeModule() {
        super("devmode");
    }

    @Override
    public void onModuleEnable() {
        mapService = new DevMapService(api, Math.max(1000L, getConfig().getLong("maps.update-ms", 5000L)));
        service = new DevModeService(api, getConfig(), mapService);
        registerCommand(new DevModeCommand(service), "devmode");
        getLogger().info("[DevMode] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (service != null) service.clear();
        if (mapService != null) mapService.clear();
        getLogger().info("[DevMode] Disabled.");
    }
}
