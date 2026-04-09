package com.kammoun.raven.library.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class RedisMessenger {

    private final RedisConnection connection;
    private final Map<String, List<Consumer<String>>> subscribers = new ConcurrentHashMap<>();

    public RedisMessenger(Logger logger) {
        this.connection = new RedisConnection(logger);
    }

    public void connect(RedisCredentials credentials) {
        connection.connect(credentials);
    }

    public void publish(String channel, String message) {
        connection.publish(channel, message);
    }

    public void subscribe(String channel, Consumer<String> handler) {
        subscribers.computeIfAbsent(channel, k -> new ArrayList<>()).add(handler);
        connection.addHandler(channel, handler);
    }

    public void unsubscribe(String channel) {
        subscribers.remove(channel);
        connection.removeHandlers(channel);
    }

    public void close() {
        connection.disconnect();
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    public RedisConnection getConnection() {
        return connection;
    }
}
