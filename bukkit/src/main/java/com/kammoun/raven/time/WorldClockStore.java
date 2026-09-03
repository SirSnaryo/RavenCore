package com.kammoun.raven.time;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Locale;
import java.util.Optional;

public class WorldClockStore {

    private static final String KEY_PREFIX = "raven:time:state:";
    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final JedisPool pool;
    private final String key;

    public WorldClockStore(JavaPlugin plugin, JedisPool pool) {
        this(plugin, pool, "survival");
    }

    public WorldClockStore(JavaPlugin plugin, JedisPool pool, String scope) {
        this.plugin = plugin;
        this.pool = pool;
        this.key = KEY_PREFIX + normalizeScope(scope);
    }

    public void write(WorldClockState state) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key, GSON.toJson(state));
        } catch (Exception e) {
            plugin.getLogger().warning("[TimeSync] Failed to write clock state: " + e.getMessage());
        }
    }

    public Optional<WorldClockState> read() {
        String json;
        try (Jedis jedis = pool.getResource()) {
            json = jedis.get(key);
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

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "survival" : scope.toLowerCase(Locale.ROOT);
    }
}
