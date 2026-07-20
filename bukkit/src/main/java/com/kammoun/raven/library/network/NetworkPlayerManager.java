package com.kammoun.raven.library.network;

import com.kammoun.raven.library.redis.event.NetworkEventBus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.JedisPool;

public class NetworkPlayerManager extends AbstractNetworkPlayerManager implements Listener {

    public NetworkPlayerManager(Plugin plugin, JedisPool pool, String serverId, NetworkEventBus eventBus) {
        this(plugin, pool, serverId, serverId, eventBus);
    }

    public NetworkPlayerManager(Plugin plugin, JedisPool pool, String serverId, String serverDisplayName, NetworkEventBus eventBus) {
        super(pool, serverId, serverDisplayName, eventBus);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        registerPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getPlayer().getWorld().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        unregisterPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        updatePlayerWorld(event.getPlayer().getUniqueId(), event.getPlayer().getWorld().getName());
    }
}
