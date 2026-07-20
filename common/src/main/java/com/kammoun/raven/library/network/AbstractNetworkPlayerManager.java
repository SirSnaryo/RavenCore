package com.kammoun.raven.library.network;

import com.google.gson.Gson;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.redis.event.NetworkPlayerJoinEvent;
import com.kammoun.raven.library.redis.event.NetworkPlayerQuitEvent;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class AbstractNetworkPlayerManager {

    protected static final String HASH_KEY = "raven:network:players";

    protected final JedisPool pool;
    protected final String serverId;
    protected final String serverDisplayName;
    private final NetworkEventBus eventBus;
    private final Gson gson = new Gson();

    // In-memory cache: uuid → playerName for all players on the whole network
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    protected AbstractNetworkPlayerManager(JedisPool pool, String serverId, NetworkEventBus eventBus) {
        this(pool, serverId, serverId, eventBus);
    }

    protected AbstractNetworkPlayerManager(JedisPool pool, String serverId, String serverDisplayName, NetworkEventBus eventBus) {
        this.pool = pool;
        this.serverId = serverId;
        this.serverDisplayName = serverDisplayName == null || serverDisplayName.isBlank() ? serverId : serverDisplayName;
        this.eventBus = eventBus;

        // Seed cache with players already online on other servers before this node started
        getOnlineNetworkPlayers().forEach(info -> nameCache.put(info.getUuid(), info.getPlayerName()));

        // Keep cache in sync with remote join/quit events
        eventBus.subscribe(NetworkPlayerJoinEvent.class, event -> {
            if (event == null || event.getUuid() == null) return;
            if (!serverId.equals(event.getSourceServer())) {
                nameCache.put(UUID.fromString(event.getUuid()), event.getPlayerName());
            }
        });

        eventBus.subscribe(NetworkPlayerQuitEvent.class, event -> {
            if (event == null || event.getUuid() == null) return;
            if (!serverId.equals(event.getSourceServer())) {
                nameCache.remove(UUID.fromString(event.getUuid()));
            }
        });
    }

    protected void registerPlayer(UUID uuid, String playerName, String world) {
        registerPlayer(uuid, playerName, serverId, serverDisplayName, world);
    }

    protected void registerPlayer(UUID uuid, String playerName, String serverId, String serverDisplayName, String world) {
        nameCache.put(uuid, playerName);

        NetworkPlayerInfo info = new NetworkPlayerInfo(uuid, playerName, serverId, serverDisplayName, world);
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(HASH_KEY, uuid.toString(), gson.toJson(info));
        }

        NetworkPlayerJoinEvent event = new NetworkPlayerJoinEvent();
        event.setUuid(uuid.toString());
        event.setPlayerName(playerName);
        eventBus.publish(event);
    }

    /**
     * Updates the world recorded for a player already registered on this server, e.g. after
     * a PlayerChangedWorldEvent. Live per-location player counts read this straight from Redis,
     * so no NetworkEventBus event is needed here — only the source server ever writes it.
     */
    protected void updatePlayerWorld(UUID uuid, String world) {
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.hget(HASH_KEY, uuid.toString());
            if (json == null) return;
            NetworkPlayerInfo info = gson.fromJson(json, NetworkPlayerInfo.class);
            NetworkPlayerInfo updated = new NetworkPlayerInfo(uuid, info.getPlayerName(), info.getServerId(),
                    info.getServerDisplayName(), world);
            jedis.hset(HASH_KEY, uuid.toString(), gson.toJson(updated));
        }
    }

    protected void unregisterPlayer(UUID uuid) {
        nameCache.remove(uuid);

        try (Jedis jedis = pool.getResource()) {
            jedis.hdel(HASH_KEY, uuid.toString());
        }

        NetworkPlayerQuitEvent event = new NetworkPlayerQuitEvent();
        event.setUuid(uuid.toString());
        eventBus.publish(event);
    }

    public Optional<NetworkPlayerInfo> findPlayer(UUID uuid) {
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.hget(HASH_KEY, uuid.toString());
            if (json == null) return Optional.empty();
            return Optional.of(gson.fromJson(json, NetworkPlayerInfo.class));
        }
    }

    public Optional<NetworkPlayerInfo> findPlayer(String name) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> all = jedis.hgetAll(HASH_KEY);
            return all.values().stream()
                    .map(json -> gson.fromJson(json, NetworkPlayerInfo.class))
                    .filter(info -> info.getPlayerName().equalsIgnoreCase(name))
                    .findFirst();
        }
    }

    public Collection<NetworkPlayerInfo> getOnlineNetworkPlayers() {
        try (Jedis jedis = pool.getResource()) {
            return jedis.hgetAll(HASH_KEY).values().stream()
                    .map(json -> gson.fromJson(json, NetworkPlayerInfo.class))
                    .collect(Collectors.toList());
        }
    }

    public boolean isOnNetwork(UUID uuid) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.hexists(HASH_KEY, uuid.toString());
        }
    }

    public String getPlayerServer(UUID uuid) {
        return findPlayer(uuid).map(NetworkPlayerInfo::getServerId).orElse(null);
    }

    /**
     * Returns all player names currently online across the whole network.
     * Reads from the in-memory cache — no Redis I/O, safe to call on tab completion.
     */
    public Collection<String> getNetworkPlayerNames() {
        return Collections.unmodifiableCollection(nameCache.values());
    }

    public List<String> completePlayerNames(String prefix) {
        return NetworkPlayerNameMatcher.complete(nameCache.values(), prefix, Set.of());
    }

    public List<String> completePlayerNames(String prefix, String excludedName) {
        return NetworkPlayerNameMatcher.complete(
                nameCache.values(),
                prefix,
                excludedName == null ? Set.of() : Set.of(excludedName)
        );
    }
}
