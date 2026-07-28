package com.kammoun.raven.stats.ravs;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface RavsBalanceProvider {

    boolean available();

    boolean cached(UUID playerId);

    long cachedBalance(UUID playerId);

    CompletableFuture<Long> balance(UUID playerId);

    static RavsBalanceProvider unavailable() {
        return new RavsBalanceProvider() {
            @Override public boolean available() { return false; }
            @Override public boolean cached(UUID playerId) { return false; }
            @Override public long cachedBalance(UUID playerId) { return 0L; }
            @Override public CompletableFuture<Long> balance(UUID playerId) {
                return CompletableFuture.completedFuture(0L);
            }
        };
    }
}
