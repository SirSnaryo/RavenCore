package com.kammoun.raven.time;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Optional;

/**
 * Stores the authority's latest {@link WorldClockState} in Redis ({@code raven:time:state}) so a
 * follower that starts up or has a player join can pull the current clock immediately instead of
 * waiting for the next broadcast. Blocking Redis I/O — call off the main thread.
 */
public class WorldClockStore {

    private static final String KEY = "raven:time:state";
    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final JedisPool pool;

    public WorldClockStore(JavaPlugin plugin, JedisPool pool) {
        this.plugin = plugin;
        this.pool = pool;
    }

    public void write(WorldClockState state) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY, GSON.toJson(state));
        } catch (Exception e) {
            plugin.getLogger().warning("[TimeSync] Failed to write clock state: " + e.getMessage());
        }
    }

    public Optional<WorldClockState> read() {
        String json;
        try (Jedis jedis = pool.getResource()) {
            json = jedis.get(KEY);
        } catch (Exception e) {
            plugin.getLogger().warning("[TimeSync] Failed to read clock state: " + e.getMessage());
            return Optional.empty();
        }
        if (json == null) return Optional.empty();

        try {
            return Optional.ofNullable(GSON.fromJson(json, WorldClockState.class));
        } catch (JsonSyntaxException e) {
            plugin.getLogger().warning("[TimeSync] Malformed clock state in Redis.");
            return Optional.empty();
        }
    }
}
