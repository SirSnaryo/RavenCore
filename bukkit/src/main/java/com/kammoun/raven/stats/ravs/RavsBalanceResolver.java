package com.kammoun.raven.stats.ravs;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class RavsBalanceResolver {

    private static final long CACHE_MILLIS = 30_000L;

    private final RavsBalanceProvider provider;
    private final Map<UUID, CachedBalance> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Long>> inFlight = new ConcurrentHashMap<>();

    public RavsBalanceResolver(RavsBalanceProvider provider) {
        this.provider = provider;
    }

    public CompletableFuture<Long> resolve(UUID playerId) {
        if (!provider.available()) return CompletableFuture.completedFuture(0L);
        if (provider.cached(playerId)) {
            long balance = provider.cachedBalance(playerId);
            cache(playerId, balance);
            return CompletableFuture.completedFuture(balance);
        }
        return refresh(playerId);
    }

    public long current(UUID playerId) {
        if (!provider.available()) return 0L;
        if (provider.cached(playerId)) {
            long balance = provider.cachedBalance(playerId);
            cache(playerId, balance);
            return balance;
        }
        CachedBalance existing = cache.get(playerId);
        long now = System.currentTimeMillis();
        if (existing == null || existing.expiresAt() <= now) {
            refresh(playerId);
        }
        return existing == null ? 0L : existing.balance();
    }

    public void clear() {
        cache.clear();
        inFlight.clear();
    }

    private CompletableFuture<Long> refresh(UUID playerId) {
        CompletableFuture<Long> existing = inFlight.get(playerId);
        if (existing != null) return existing;
        CompletableFuture<Long> source;
        try {
            source = provider.balance(playerId);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(0L);
        }
        CompletableFuture<Long> created = source
                .exceptionally(ignored -> 0L)
                .thenApply(balance -> {
                    long safe = Math.max(0L, balance);
                    cache(playerId, safe);
                    return safe;
                });
        CompletableFuture<Long> raced = inFlight.putIfAbsent(playerId, created);
        if (raced != null) return raced;
        created.whenComplete((ignored, error) -> inFlight.remove(playerId, created));
        return created;
    }

    private void cache(UUID playerId, long balance) {
        cache.put(playerId, new CachedBalance(Math.max(0L, balance),
                System.currentTimeMillis() + CACHE_MILLIS));
    }

    private record CachedBalance(long balance, long expiresAt) {}
}
