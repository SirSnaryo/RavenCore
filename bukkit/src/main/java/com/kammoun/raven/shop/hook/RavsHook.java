package com.kammoun.raven.shop.hook;

import com.kammoun.ravens.api.RavensAPI;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RavsHook {

    private boolean missing;

    public boolean available() {
        return api() != null;
    }

    public long balance(UUID playerId) {
        RavensAPI api = api();
        return api == null || !api.isOnlineCached(playerId) ? 0L : Math.max(0L, api.getBalance(playerId));
    }

    public boolean has(UUID playerId, long amount) {
        if (amount <= 0L) return true;
        RavensAPI api = api();
        return api != null && api.isOnlineCached(playerId) && api.getBalance(playerId) >= amount;
    }

    /**
     * Ravs floors balances at 0 rather than rejecting an overdraft, and its reads are cache-only,
     * so a withdrawal is only attempted for a player cached on this server who can afford it.
     */
    public CompletableFuture<Boolean> withdraw(UUID playerId, long amount) {
        if (amount <= 0L) return CompletableFuture.completedFuture(true);
        RavensAPI api = api();
        if (api == null || !api.isOnlineCached(playerId) || api.getBalance(playerId) < amount) {
            return CompletableFuture.completedFuture(false);
        }
        return api.removeBalance(playerId, amount)
                .thenApply(balance -> true)
                .exceptionally(error -> false);
    }

    public CompletableFuture<Boolean> deposit(UUID playerId, long amount) {
        if (amount <= 0L) return CompletableFuture.completedFuture(true);
        RavensAPI api = api();
        if (api == null) return CompletableFuture.completedFuture(false);
        return api.addBalance(playerId, amount)
                .thenApply(balance -> true)
                .exceptionally(error -> false);
    }

    private RavensAPI api() {
        if (missing || !Bukkit.getPluginManager().isPluginEnabled("Ravs")) return null;
        try {
            return RavensAPI.get();
        } catch (LinkageError error) {
            missing = true;
            return null;
        }
    }
}
