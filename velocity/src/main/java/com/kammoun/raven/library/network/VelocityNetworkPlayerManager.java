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
        // previousServer == null means the player just joined the network (not switching servers)
        if (event.getPreviousServer() == null) {
            // world is unknown at the proxy layer; the backend's own registerPlayer call
            // (fired by its PlayerJoinEvent) overwrites this with the real world moments later
            registerPlayer(
                    event.getPlayer().getUniqueId(),
                    event.getPlayer().getUsername(),
                    null
            );
        }
    }

    /** Fires when a player fully disconnects from the proxy. */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        unregisterPlayer(event.getPlayer().getUniqueId());
    }
}
