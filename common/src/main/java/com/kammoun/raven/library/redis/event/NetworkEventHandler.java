package com.kammoun.raven.library.redis.event;

@FunctionalInterface
public interface NetworkEventHandler<T extends CrossServerEvent> {
    void handle(T event);
}
