package com.kammoun.raven.velocity.lastserver;

import com.kammoun.raven.library.module.VModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which backend server each player is on and persists it to Redis on disconnect,
 * so that on next login the player is sent back to that server automatically.
 *
 * <p>Servers listed under {@code spawn-servers} in the module config are excluded from
 * persistence — players who disconnect from a spawn server are not redirected on rejoin.</p>
 */
public class LastServerModule extends VModule {

    private static final String REDIS_KEY_PREFIX = "raven:lastserver:";

    private final JedisPool jedisPool;

    /** Live map of player → current backend server name. */
    private final ConcurrentHashMap<UUID, String> currentServer = new ConcurrentHashMap<>();

    /** Server names that are considered "spawn" — not persisted to Redis. */
    private Set<String> spawnServers;

    public LastServerModule(JedisPool jedisPool) {
        super("lastserver");
        this.jedisPool = jedisPool;
    }

    @Override
    public void onModuleEnable() {
        spawnServers = new HashSet<>(getStringList("spawn-servers"));
        registerListener(this);
        getLogger().info("LastServer module enabled. Spawn servers: " + spawnServers);
    }

    @Override
    public void onModuleDisable() {
        currentServer.clear();
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /** Keep the local cache up-to-date whenever a player switches servers. */
    @Subscribe
    public void onServerSwitch(ServerPostConnectEvent event) {
        event.getPlayer().getCurrentServer().ifPresent(conn ->
                currentServer.put(
                        event.getPlayer().getUniqueId(),
                        conn.getServerInfo().getName()
                )
        );
    }

    /**
     * On full disconnect: write the last known server to Redis (unless it is a
     * spawn server), then evict the player from the local cache.
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String lastServer = currentServer.remove(uuid);

        if (lastServer == null || spawnServers.contains(lastServer)) return;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(REDIS_KEY_PREFIX + uuid, lastServer);
        } catch (Exception e) {
            getLogger().warning("Failed to persist last-server for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * On initial login (no previous server): look up Redis and, if a valid server
     * is found, redirect the player there instead of the default target.
     */
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        // getPreviousServer() is present when switching between backend servers.
        // An empty optional means this is the player's initial connection to the proxy.
        if (event.getPreviousServer() != null) return;

        UUID uuid = event.getPlayer().getUniqueId();

        String lastServer;
        try (Jedis jedis = jedisPool.getResource()) {
            lastServer = jedis.get(REDIS_KEY_PREFIX + uuid);
        } catch (Exception e) {
            getLogger().warning("Failed to read last-server for " + uuid + ": " + e.getMessage());
            return;
        }

        if (lastServer == null) return;

        Optional<RegisteredServer> target = server.getServer(lastServer);
        if (target.isPresent()) {
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(target.get()));
        }
        // If the server no longer exists on the proxy, fall through to the default.
    }
}
