package com.kammoun.raven.shop.hook;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RavsHook {

    private static final String API_CLASS = "com.kammoun.ravens.api.RavensAPI";
    private boolean missing;

    public boolean available() {
        return api() != null;
    }

    public long balance(UUID playerId) {
        Object api = api();
        if (api == null || !isOnlineCached(api, playerId)) return 0L;
        Object val = call(api, "getBalance", playerId);
        return val instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    public boolean has(UUID playerId, long amount) {
        if (amount <= 0L) return true;
        Object api = api();
        if (api == null || !isOnlineCached(api, playerId)) return false;
        return balance(playerId) >= amount;
    }

    /**
     * Ravs floors balances at 0 rather than rejecting an overdraft, and its reads are cache-only,
     * so a withdrawal is only attempted for a player cached on this server who can afford it.
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> withdraw(UUID playerId, long amount) {
        if (amount <= 0L) return CompletableFuture.completedFuture(true);
        Object api = api();
        if (api == null || !isOnlineCached(api, playerId) || balance(playerId) < amount) {
            return CompletableFuture.completedFuture(false);
        }
        Object result = call(api, "removeBalance", playerId, amount);
        if (result instanceof CompletableFuture<?> future) {
            return ((CompletableFuture<Object>) future)
                    .thenApply(balance -> true)
                    .exceptionally(error -> false);
        }
        return CompletableFuture.completedFuture(false);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> deposit(UUID playerId, long amount) {
        if (amount <= 0L) return CompletableFuture.completedFuture(true);
        Object api = api();
        if (api == null) return CompletableFuture.completedFuture(false);
        Object result = call(api, "addBalance", playerId, amount);
        if (result instanceof CompletableFuture<?> future) {
            return ((CompletableFuture<Object>) future)
                    .thenApply(balance -> true)
                    .exceptionally(error -> false);
        }
        return CompletableFuture.completedFuture(false);
    }

    private boolean isOnlineCached(Object api, UUID playerId) {
        Object val = call(api, "isOnlineCached", playerId);
        return Boolean.TRUE.equals(val);
    }

    private Object call(Object target, String methodName, Object... args) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                    return method.invoke(target, args);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    private Object api() {
        if (missing || !Bukkit.getPluginManager().isPluginEnabled("Ravs")) return null;
        try {
            return Class.forName(API_CLASS).getMethod("get").invoke(null);
        } catch (ReflectiveOperationException | LinkageError error) {
            missing = true;
            return null;
        }
    }
}
