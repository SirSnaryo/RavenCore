package com.kammoun.raven.library.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds arbitrary key-value data for a single player in memory.
 * Use PlayerDataManager to obtain instances.
 */
public class PlayerDataStore {

    private final UUID uuid;
    private final Map<String, Object> data = new HashMap<>();

    PlayerDataStore(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key, String def) {
        Object val = data.get(key);
        return val instanceof String s ? s : def;
    }

    public int getInt(String key, int def) {
        Object val = data.get(key);
        return val instanceof Number n ? n.intValue() : def;
    }

    public double getDouble(String key, double def) {
        Object val = data.get(key);
        return val instanceof Number n ? n.doubleValue() : def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object val = data.get(key);
        return val instanceof Boolean b ? b : def;
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public void remove(String key) {
        data.remove(key);
    }

    public void clear() {
        data.clear();
    }

    /** Returns a snapshot of all stored data. */
    public Map<String, Object> getAll() {
        return new HashMap<>(data);
    }
}
