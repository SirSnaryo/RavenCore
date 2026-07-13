package com.kammoun.raven.portal.tracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players currently mid-transfer so callers (border enforcement, portal detection)
 * can stand down for them until the handoff completes. A transfer older than
 * {@code timeoutMillis} is treated as stale (dropped proxy message, Redis hiccup, etc.) and
 * cleared so normal handling resumes.
 */
public class PendingTransferTracker {

    private final long timeoutMillis;
    private final Map<UUID, Long> startedAt = new ConcurrentHashMap<>();

    public PendingTransferTracker(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public void mark(UUID playerId) {
        startedAt.put(playerId, System.currentTimeMillis());
    }

    public void clear(UUID playerId) {
        startedAt.remove(playerId);
    }

    public void clearAll() {
        startedAt.clear();
    }

    public boolean isPending(UUID playerId) {
        Long at = startedAt.get(playerId);
        if (at == null) return false;
        if (System.currentTimeMillis() - at < timeoutMillis) return true;
        startedAt.remove(playerId);
        return false;
    }
}
