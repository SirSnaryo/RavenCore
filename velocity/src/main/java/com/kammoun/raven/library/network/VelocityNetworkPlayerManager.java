package com.kammoun.raven.library.network;

import com.kammoun.raven.library.network.AbstractNetworkPlayerManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import redis.clients.jedis.JedisPool;

/**
 * Tracks online players in Redis from the Velocity proxy.
 * Registers/unregisters when a player first joins the network or fully disconnects.
 */
public class VelocityNetworkPlayerManager extends AbstractNetworkPlayerManager {

    public VelocityNetworkPlayerManager(Object plugin, ProxyServer server, JedisPool pool, String serverId) {
        super(pool, serverId);
        server.getEventManager().register(plugin, this);
    }

    /** Fires after a player successfully connects to a backend server. */
    @Subscribe
    public void onServerConnect(ServerPostConnectEvent event) {
        // previousServer == null means the player just joined the network (not switching servers)
        if (event.getPreviousServer() == null) {
            registerPlayer(
                    event.getPlayer().getUniqueId(),
                    event.getPlayer().getUsername()
            );
        }
    }

    /** Fires when a player fully disconnects from the proxy. */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        unregisterPlayer(event.getPlayer().getUniqueId());
    }
}
