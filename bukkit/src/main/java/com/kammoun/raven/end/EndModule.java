package com.kammoun.raven.end;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import lombok.Getter;

/**
 * Single switch board for everything End related: whether /rtp may send players to the End server
 * and whether the End portal link runs at all.
 */
@Getter
public final class EndModule extends KModule<RavenCore> {

    private boolean rtpEnabled;
    private boolean portalsEnabled;

    public EndModule() {
        super("end");
    }

    @Override
    public void onModuleEnable() {
        load();
        getLogger().info("[End] Enabled (rtp=" + rtpEnabled + ", portals=" + portalsEnabled + ").");
    }

    @Override
    public void onModuleDisable() {
    }

    @Override
    public void reload() {
        reloadConfig();
        load();
    }

    private void load() {
        this.rtpEnabled = getConfig().getBoolean("rtp.enabled", false);
        this.portalsEnabled = getConfig().getBoolean("portals.enabled", false);
    }
}
