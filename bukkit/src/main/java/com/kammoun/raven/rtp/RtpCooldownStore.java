package com.kammoun.raven.rtp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtpCooldownStore {
    private final Map<UUID, Long> expiresAt = new ConcurrentHashMap<>();

    public long remainingSeconds(UUID uuid) {
        Long expiry = expiresAt.get(uuid);
        if (expiry == null) return 0;
        long remainingMillis = expiry - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            expiresAt.remove(uuid, expiry);
            return 0;
        }
        return (remainingMillis + 999L) / 1000L;
    }

    public void start(UUID uuid, int seconds) {
        if (seconds <= 0) return;
        expiresAt.put(uuid, System.currentTimeMillis() + seconds * 1000L);
    }

    public void clear() {
        expiresAt.clear();
    }
}
