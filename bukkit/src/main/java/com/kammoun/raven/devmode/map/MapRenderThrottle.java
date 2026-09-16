package com.kammoun.raven.devmode.map;

final class MapRenderThrottle {

    private final long intervalMs;
    private long lastRenderAt = Long.MIN_VALUE;

    MapRenderThrottle(long intervalMs) {
        this.intervalMs = Math.max(1000L, intervalMs);
    }

    boolean shouldRender(long now) {
        if (lastRenderAt == Long.MIN_VALUE || now - lastRenderAt >= intervalMs) {
            lastRenderAt = now;
            return true;
        }
        return false;
    }
}
