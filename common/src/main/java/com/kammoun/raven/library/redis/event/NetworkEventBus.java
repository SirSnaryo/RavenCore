package com.kammoun.raven.library.redis.event;

import com.google.gson.Gson;
import com.kammoun.raven.library.redis.RedisMessenger;

import java.util.function.Consumer;

public class NetworkEventBus {

    private static final String CHANNEL_PREFIX = "raven:events:";

    private final RedisMessenger messenger;
    private final Gson gson;
    private final String serverId;
    /**
     * Controls on which thread handlers are invoked.
     * Bukkit: {@code r -> Bukkit.getScheduler().runTask(plugin, r)}
     * Velocity: {@code Runnable::run} (no main-thread concept)
     */
    private final Consumer<Runnable> dispatcher;

    public NetworkEventBus(RedisMessenger messenger, String serverId, Consumer<Runnable> dispatcher) {
        this.messenger = messenger;
        this.gson = new Gson();
        this.serverId = serverId;
        this.dispatcher = dispatcher;
    }

    public void publish(CrossServerEvent event) {
        event.setSourceServer(serverId);
        event.setTimestamp(System.currentTimeMillis());
        String channel = CHANNEL_PREFIX + event.getClass().getSimpleName();
        messenger.publish(channel, gson.toJson(event));
    }

    public <T extends CrossServerEvent> void subscribe(Class<T> eventClass, NetworkEventHandler<T> handler) {
        String channel = CHANNEL_PREFIX + eventClass.getSimpleName();
        messenger.subscribe(channel, message -> {
            T event = gson.fromJson(message, eventClass);
            dispatcher.accept(() -> handler.handle(event));
        });
    }

    public <T extends CrossServerEvent> void unsubscribe(Class<T> eventClass) {
        String channel = CHANNEL_PREFIX + eventClass.getSimpleName();
        messenger.unsubscribe(channel);
    }
}
