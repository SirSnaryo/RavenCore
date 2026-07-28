package com.kammoun.raven.stats.ravs;

import com.kammoun.ravens.api.RavensAPI;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RavsApiBalanceProvider implements RavsBalanceProvider {

    private RavensAPI api() {
        return RavensAPI.get();
    }

    @Override
    public boolean available() {
        return api() != null;
    }

    @Override
    public boolean cached(UUID playerId) {
        RavensAPI api = api();
        return api != null && api.isOnlineCached(playerId);
    }

    @Override
    public long cachedBalance(UUID playerId) {
        RavensAPI api = api();
        return api == null ? 0L : api.getBalance(playerId);
    }

    @Override
    public CompletableFuture<Long> balance(UUID playerId) {
        RavensAPI api = api();
        return api == null ? CompletableFuture.completedFuture(0L) : api.getBalanceAsync(playerId);
    }
}
