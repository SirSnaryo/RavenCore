package com.kammoun.raven.library.network;

import com.kammoun.raven.library.network.AbstractNetworkPlayerManager;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import redis.clients.jedis.JedisPool;

public class VelocityNetworkPlayerManager extends AbstractNetworkPlayerManager {

    public VelocityNetworkPlayerManager(Object plugin, ProxyServer server, JedisPool pool,
                                        String serverId, NetworkEventBus eventBus) {
        super(pool, serverId, eventBus);
        server.getEventManager().register(plugin, this);
    }

    /** Fires after a player successfully connects to a backend server. */
    @Subscribe
    public void onServerConnect(ServerPostConnectEvent event) {
        // The proxy knows the backend server name but not the world. Bukkit will overwrite
        // this row moments later with the same server plus the real world.
        String backend = event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName())
                .orElse(event.getPreviousServer() == null ? serverId : event.getPreviousServer().getServerInfo().getName());
        registerPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getUsername(), backend, backend, null);
    }

    /** Fires when a player fully disconnects from the proxy. */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        unregisterPlayer(event.getPlayer().getUniqueId(), true);
    }
}
