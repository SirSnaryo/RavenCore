package com.kammoun.raven.library.network;

import com.kammoun.raven.library.redis.event.NetworkEventBus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.JedisPool;

public class NetworkPlayerManager extends AbstractNetworkPlayerManager implements Listener {

    public NetworkPlayerManager(Plugin plugin, JedisPool pool, String serverId, NetworkEventBus eventBus) {
        super(pool, serverId, eventBus);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        registerPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        unregisterPlayer(event.getPlayer().getUniqueId());
    }
}
