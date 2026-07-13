package com.kammoun.raven.library.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedisConnection {

    private JedisPool pool;
    private final Map<String, List<Consumer<String>>> handlers = new ConcurrentHashMap<>();
    private RavenPubSub pubSub;
    private Thread subscriberThread;
    private volatile boolean connected = false;
    private RedisCredentials credentials;
    private final Logger logger;

    public RedisConnection(Logger logger) {
        this.logger = logger;
    }

    public void connect(RedisCredentials credentials) {
        this.credentials = credentials;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);

        if (credentials.hasPassword()) {
            pool = new JedisPool(poolConfig, credentials.getHost(), credentials.getPort(),
                    2000, credentials.getPassword(), credentials.getDatabase());
        } else {
            pool = new JedisPool(poolConfig, credentials.getHost(), credentials.getPort(),
                    2000, null, credentials.getDatabase());
        }

        connected = true;
        startSubscriberThread();
        logger.info("[Redis] Connected to " + credentials.getHost() + ":" + credentials.getPort());
    }

    public void disconnect() {
        connected = false;
        if (pubSub != null && pubSub.isSubscribed()) {
            pubSub.unsubscribe();
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
        logger.info("[Redis] Disconnected.");
    }

    public void publish(String channel, String message) {
        if (!connected || pool == null) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel, message);
        } catch (Exception e) {
            logger.warning("[Redis] Failed to publish to channel '" + channel + "': " + e.getMessage());
        }
    }

    public void addHandler(String channel, Consumer<String> handler) {
        handlers.computeIfAbsent(channel, k -> new ArrayList<>()).add(handler);
        // If pubSub is already running, subscribe to this new channel
        if (pubSub != null && pubSub.isSubscribed()) {
            pubSub.subscribe(channel);
        }
    }

    public void removeHandlers(String channel) {
        handlers.remove(channel);
        if (pubSub != null && pubSub.isSubscribed()) {
            pubSub.unsubscribe(channel);
        }
    }

    private void startSubscriberThread() {
        pubSub = new RavenPubSub();
        subscriberThread = new Thread(() -> {
            while (connected) {
                try (Jedis jedis = pool.getResource()) {
                    String[] channels = handlers.keySet().toArray(new String[0]);
                    if (channels.length > 0) {
                        jedis.subscribe(pubSub, channels);
                    } else {
                        // Subscribe to a dummy internal channel so the thread stays alive
                        jedis.subscribe(pubSub, "raven:keepalive");
                    }
                } catch (Exception e) {
                    if (connected) {
                        logger.warning("[Redis] Subscriber disconnected, reconnecting: " + e.getMessage());
                        try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }
        }, "RavenCore-Redis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public boolean isConnected() {
        return connected && pool != null && !pool.isClosed();
    }

    public JedisPool getPool() {
        return pool;
    }

    private class RavenPubSub extends JedisPubSub {
        @Override
        public void onMessage(String channel, String message) {
            List<Consumer<String>> channelHandlers = handlers.get(channel);
            if (channelHandlers == null) return;
            for (Consumer<String> handler : channelHandlers) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[Redis] Handler error on channel '" + channel + "'", e);
                }
            }
        }
    }
}
