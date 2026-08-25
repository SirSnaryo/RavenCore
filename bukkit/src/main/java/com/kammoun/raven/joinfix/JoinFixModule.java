package com.kammoun.raven.joinfix;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.joinfix.listener.JoinFixListener;
import com.kammoun.raven.library.module.KModule;

public final class JoinFixModule extends KModule<RavenCore> {
    public JoinFixModule() {
        super("joinfix");
    }

    @Override
    public void onModuleEnable() {
        registerEvent(new JoinFixListener(api, getConfig().getLong("delay-ticks", 20L)));
        getLogger().info("[JoinFix] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        getLogger().info("[JoinFix] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
    }
}
