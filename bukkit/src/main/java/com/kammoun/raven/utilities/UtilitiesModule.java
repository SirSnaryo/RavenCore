package com.kammoun.raven.utilities;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.utilities.config.UtilitiesConfig;
import com.kammoun.raven.utilities.listener.JoinMessageListener;

public final class UtilitiesModule extends KModule<RavenCore> {
    // Listeners read this through the getter so /ravencore reload utilities takes effect
    // without re-registering them.
    private volatile UtilitiesConfig settings;

    public UtilitiesModule() {
        super("utilities");
    }

    @Override
    public void onModuleEnable() {
        settings = new UtilitiesConfig(getConfig());
        registerEvent(new JoinMessageListener(this::getSettings));
        getLogger().info("[Utilities] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        getLogger().info("[Utilities] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
        settings = new UtilitiesConfig(getConfig());
    }

    public UtilitiesConfig getSettings() {
        return settings;
    }
}
