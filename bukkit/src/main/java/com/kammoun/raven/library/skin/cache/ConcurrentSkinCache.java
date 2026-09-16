package com.kammoun.raven.library.skin.cache;

import com.kammoun.raven.library.skin.model.SkinData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ConcurrentSkinCache implements SkinCache {

    private static final int MAX_SIZE = 2_048;

    private final Map<UUID, SkinData> cache = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, SkinData> eldest) {
            return size() > MAX_SIZE;
        }
    };

    @Override
    public @Nullable SkinData get(@NotNull UUID uuid) {
        synchronized (cache) {
            return cache.get(uuid);
        }
    }

    @Override
    public void put(@NotNull SkinData skinData) {
        synchronized (cache) {
            cache.put(skinData.uuid(), skinData);
        }
    }

    @Override
    public boolean contains(@NotNull UUID uuid) {
        synchronized (cache) {
            return cache.containsKey(uuid);
        }
    }

    @Override
    public void invalidate(@NotNull UUID uuid) {
        synchronized (cache) {
            cache.remove(uuid);
        }
    }

    @Override
    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    @Override
    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }
}
