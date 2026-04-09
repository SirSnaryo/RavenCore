package com.kammoun.raven.library.network;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class AbstractNetworkPlayerManager {

    protected static final String HASH_KEY = "raven:network:players";

    protected final JedisPool pool;
    protected final String serverId;
    private final Gson gson = new Gson();

    protected AbstractNetworkPlayerManager(JedisPool pool, String serverId) {
        this.pool = pool;
        this.serverId = serverId;
    }

    protected void registerPlayer(UUID uuid, String playerName) {
        NetworkPlayerInfo info = new NetworkPlayerInfo(uuid, playerName, serverId);
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(HASH_KEY, uuid.toString(), gson.toJson(info));
        }
    }

    protected void unregisterPlayer(UUID uuid) {
        try (Jedis jedis = pool.getResource()) {
            jedis.hdel(HASH_KEY, uuid.toString());
        }
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
}
