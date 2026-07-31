package com.kammoun.raven.serverinfo;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.serverinfo.metrics.ServerMetrics;
import com.kammoun.raven.serverinfo.papi.ServerInfoPlaceholders;
import lombok.Getter;

/**
 * Exposes this server's identity, network player count and performance metrics as
 * {@code %ravenserver_*%} placeholders. No commands, no config, no storage — every value is read
 * live from RavenCore state or the running server.
 */
@Getter
public class ServerInfoModule extends KModule<RavenCore> {

    private ServerMetrics metrics;

    public ServerInfoModule() {
        super("serverinfo");
    }

    @Override
    public void onModuleEnable() {
        this.metrics = new ServerMetrics();

        if (!KPlaceholderExpansion.isAvailable()) {
            getLogger().info("[ServerInfo] PlaceholderAPI not found — %ravenserver_*% placeholders unavailable.");
            return;
        }

        new ServerInfoPlaceholders(api, metrics).register();
        getLogger().info("[ServerInfo] Registered PlaceholderAPI placeholders.");
    }

    @Override
    public void onModuleDisable() {
        this.metrics = null;
    }
}
