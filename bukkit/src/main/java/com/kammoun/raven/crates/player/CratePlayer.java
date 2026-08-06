package com.kammoun.raven.crates.player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CratePlayer {
    private final UUID uuid;
    private final ConcurrentMap<String, Integer> keys = new ConcurrentHashMap<>();

    public CratePlayer(UUID uuid, Map<String, Integer> keys) {
        this.uuid = uuid;
        if (keys != null) {
            keys.forEach((crateId, amount) -> this.keys.put(crateId.toLowerCase(), Math.max(0, amount)));
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public int keys(String crateId) {
        return keys.getOrDefault(crateId.toLowerCase(), 0);
    }

    public boolean hasKey(String crateId) {
        return keys(crateId) > 0;
    }

    public int totalKeys() {
        return keys.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> keys() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    public void add(String crateId, int amount) {
        keys.compute(crateId.toLowerCase(), (key, current) -> Math.max(0, (current == null ? 0 : current) + amount));
    }

    public void set(String crateId, int amount) {
        keys.put(crateId.toLowerCase(), Math.max(0, amount));
    }

    public void replaceAll(Map<String, Integer> loaded) {
        keys.clear();
        loaded.forEach((crateId, amount) -> keys.put(crateId.toLowerCase(), Math.max(0, amount)));
    }
}
