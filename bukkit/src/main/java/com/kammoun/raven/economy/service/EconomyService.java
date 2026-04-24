package com.kammoun.raven.economy.service;

import com.kammoun.raven.library.redis.event.BalanceUpdateEvent;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyService {

    private static final String KEY_PREFIX = "raven:economy:balance:";

    private final JavaPlugin plugin;
    private final JedisPool jedisPool;
    private final NetworkEventBus eventBus;
    private final String serverId;
    private final double startingBalance;
    private final Map<UUID, Double> cache = new ConcurrentHashMap<>();

    public EconomyService(JavaPlugin plugin, JedisPool jedisPool, NetworkEventBus eventBus,
                          String serverId, double startingBalance) {
        this.plugin = plugin;
        this.jedisPool = jedisPool;
        this.eventBus = eventBus;
        this.serverId = serverId;
        this.startingBalance = startingBalance;

        eventBus.subscribe(BalanceUpdateEvent.class, this::onRemoteBalanceUpdate);
    }

    // Called on AsyncPlayerPreLoginEvent — already on an async thread
    public void loadPlayer(UUID uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(KEY_PREFIX + uuid);
            if (val == null) {
                jedis.set(KEY_PREFIX + uuid, String.valueOf(startingBalance));
                cache.put(uuid, startingBalance);
            } else {
                cache.put(uuid, Double.parseDouble(val));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy] Failed to load balance for " + uuid + ": " + e.getMessage());
            cache.put(uuid, startingBalance);
        }
    }

    // Called from an async context (PlayerQuitEvent handler schedules this)
    public void unloadPlayer(UUID uuid) {
        Double balance = cache.remove(uuid);
        if (balance == null) return;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(KEY_PREFIX + uuid, String.valueOf(balance));
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy] Failed to save balance for " + uuid + ": " + e.getMessage());
        }
    }

    public double getBalance(UUID uuid) {
        Double cached = cache.get(uuid);
        if (cached != null) return cached;
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(KEY_PREFIX + uuid);
            return val != null ? Double.parseDouble(val) : startingBalance;
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy] Failed to get balance for " + uuid + ": " + e.getMessage());
            return startingBalance;
        }
    }

    public boolean hasAccount(UUID uuid) {
        if (cache.containsKey(uuid)) return true;
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(KEY_PREFIX + uuid);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean createAccount(UUID uuid) {
        if (hasAccount(uuid)) return false;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx(KEY_PREFIX + uuid, String.valueOf(startingBalance));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean setBalance(UUID uuid, double amount) {
        if (amount < 0) return false;
        double previous = getBalance(uuid);
        cache.put(uuid, amount);
        persistAndPublish(uuid, amount, amount - previous);
        return true;
    }

    public boolean deposit(UUID uuid, double amount) {
        if (amount < 0) return false;
        double newBalance = getBalance(uuid) + amount;
        cache.put(uuid, newBalance);
        persistAndPublish(uuid, newBalance, amount);
        return true;
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (amount < 0) return false;
        double balance = getBalance(uuid);
        if (balance < amount) return false;
        double newBalance = balance - amount;
        cache.put(uuid, newBalance);
        persistAndPublish(uuid, newBalance, -amount);
        return true;
    }

    public void shutdown() {
        eventBus.unsubscribe(BalanceUpdateEvent.class);
    }

    // Persist to Redis and publish cross-server event asynchronously.
    // Must be async because deposit/withdraw/setBalance are called on the main thread.
    private void persistAndPublish(UUID uuid, double newBalance, double delta) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set(KEY_PREFIX + uuid, String.valueOf(newBalance));
            } catch (Exception e) {
                plugin.getLogger().warning("[Economy] Failed to persist balance for " + uuid + ": " + e.getMessage());
            }

            BalanceUpdateEvent event = new BalanceUpdateEvent();
            event.setUuid(uuid.toString());
            event.setNewBalance(newBalance);
            event.setDelta(delta);
            eventBus.publish(event);
        });
    }

    // Dispatched on the main thread via NetworkEventBus dispatcher.
    // Skip own-server events to avoid stale overwrites from in-flight async tasks.
    private void onRemoteBalanceUpdate(BalanceUpdateEvent event) {
        if (serverId.equals(event.getSourceServer())) return;
        UUID uuid = UUID.fromString(event.getUuid());
        if (cache.containsKey(uuid)) {
            cache.put(uuid, event.getNewBalance());
        }
    }
}
