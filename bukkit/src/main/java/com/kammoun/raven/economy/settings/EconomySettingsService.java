package com.kammoun.raven.economy.settings;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomySettingsService {

    private static final String KEY_PREFIX = "raven:economy:settings:";

    private final JedisPool pool;
    private final Map<UUID, EconomyPlayerSettings> cache = new ConcurrentHashMap<>();

    public EconomySettingsService(JedisPool pool) {
        this.pool = pool;
    }

    public EconomyPlayerSettings get(UUID uuid) {
        EconomyPlayerSettings cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }

        try (Jedis jedis = pool.getResource()) {
            Map<String, String> values = jedis.hgetAll(KEY_PREFIX + uuid);
            EconomyPlayerSettings settings = new EconomyPlayerSettings(
                    bool(values.get("accepts-payments"), true),
                    bool(values.get("payment-messages"), true)
            );
            cache.put(uuid, settings);
            return settings;
        }
    }

    public EconomyPlayerSettings togglePayments(UUID uuid) {
        EconomyPlayerSettings current = get(uuid);
        return setPayments(uuid, !current.acceptsPayments());
    }

    public EconomyPlayerSettings toggleMessages(UUID uuid) {
        EconomyPlayerSettings current = get(uuid);
        return setMessages(uuid, !current.paymentMessages());
    }

    public EconomyPlayerSettings setPayments(UUID uuid, boolean enabled) {
        EconomyPlayerSettings current = get(uuid);
        EconomyPlayerSettings updated = new EconomyPlayerSettings(enabled, current.paymentMessages());
        save(uuid, updated);
        return updated;
    }

    public EconomyPlayerSettings setMessages(UUID uuid, boolean enabled) {
        EconomyPlayerSettings current = get(uuid);
        EconomyPlayerSettings updated = new EconomyPlayerSettings(current.acceptsPayments(), enabled);
        save(uuid, updated);
        return updated;
    }

    public boolean acceptsPayments(UUID uuid) {
        return get(uuid).acceptsPayments();
    }

    public boolean paymentMessages(UUID uuid) {
        return get(uuid).paymentMessages();
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    private void save(UUID uuid, EconomyPlayerSettings settings) {
        cache.put(uuid, settings);
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(KEY_PREFIX + uuid, Map.of(
                    "accepts-payments", String.valueOf(settings.acceptsPayments()),
                    "payment-messages", String.valueOf(settings.paymentMessages())
            ));
        }
    }

    private static boolean bool(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
