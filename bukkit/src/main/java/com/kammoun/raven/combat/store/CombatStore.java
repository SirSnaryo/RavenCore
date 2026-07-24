package com.kammoun.raven.combat.store;

import com.google.gson.Gson;
import com.kammoun.raven.combat.model.CombatTag;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;

public final class CombatStore {
    private static final String PREFIX = "raven:combat:tag:";
    private static final Gson GSON = new Gson();
    private final JedisPool pool;

    public CombatStore(JedisPool pool) {
        this.pool = pool;
    }

    public CombatTag load(UUID playerUuid) {
        if (pool == null) return null;
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.get(PREFIX + playerUuid);
            if (json == null) return null;
            CombatTag tag = GSON.fromJson(json, CombatTag.class);
            return tag != null && tag.active() ? tag : null;
        }
    }

    public void save(UUID playerUuid, CombatTag tag) {
        if (pool == null) return;
        long ttl = Math.max(1L, (long) Math.ceil((tag.expiresAt() - System.currentTimeMillis()) / 1000.0));
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(PREFIX + playerUuid, ttl, GSON.toJson(tag));
        }
    }

    public void delete(UUID playerUuid) {
        if (pool == null) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(PREFIX + playerUuid);
        }
    }
}
