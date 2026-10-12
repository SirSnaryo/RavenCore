package com.kammoun.raven.settings.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Per-player hostile mob spawning. The cache defaults to "mobs spawn" so the spawn listener never
 * blocks the main thread on a Redis read; the stored value is loaded asynchronously on join.
 */
public class MobSpawnSettingsService {

    private static final String KEY_PREFIX = "raven:settings:mobspawn:";

    private final JedisPool pool;
    private final Logger logger;
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    public MobSpawnSettingsService(RavenCore plugin) {
        this.pool = plugin.getRedisMessenger() == null || plugin.getRedisMessenger().getConnection() == null
                ? null
                : plugin.getRedisMessenger().getConnection().getPool();
        this.logger = plugin.getLogger();
    }

    public boolean allowsMobSpawn(UUID uuid) {
        return cache.getOrDefault(uuid, Boolean.TRUE);
    }

    /** True when nobody has hostile spawning switched off, so the listener can exit immediately. */
    public boolean anyDisabled() {
        return cache.containsValue(Boolean.FALSE);
    }

    public void set(Player player, boolean enabled) {
        cache.put(player.getUniqueId(), enabled);
        save(player.getUniqueId(), enabled);
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        cache.putIfAbsent(uuid, Boolean.TRUE);
        if (pool == null) return;

        SchedulerUtil.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                String value = jedis.get(KEY_PREFIX + uuid);
                cache.put(uuid, value == null || Boolean.parseBoolean(value));
            } catch (Exception e) {
                logger.warning("[Settings] Failed to load mob spawn setting for " + player.getName()
                        + ": " + e.getMessage());
            }
        });
    }

    public void unload(Player player) {
        cache.remove(player.getUniqueId());
    }

    public Set<UUID> disabledPlayers() {
        Set<UUID> disabled = ConcurrentHashMap.newKeySet();
        cache.forEach((uuid, enabled) -> {
            if (!enabled) disabled.add(uuid);
        });
        return disabled;
    }

    public void clear() {
        cache.clear();
    }

    private void save(UUID uuid, boolean enabled) {
        if (pool == null) return;
        SchedulerUtil.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.set(KEY_PREFIX + uuid, String.valueOf(enabled));
            } catch (Exception e) {
                logger.warning("[Settings] Failed to save mob spawn setting for " + uuid + ": " + e.getMessage());
            }
        });
    }
}
