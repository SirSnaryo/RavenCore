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
        return cached(playerId) ? Math.max(0L, number(call("getBalance", playerId))) : 0L;
    }

    public boolean has(UUID playerId, long amount) {
        if (amount <= 0L) return true;
        return cached(playerId) && number(call("getBalance", playerId)) >= amount;
    }

    /**
     * Ravs floors balances at 0 rather than rejecting an overdraft, and its reads are cache-only,
     * so a withdrawal is only attempted for a player cached on this server who can afford it.
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> withdraw(UUID playerId, long amount) {
        if (amount <= 0L) return CompletableFuture.completedFuture(true);
        if (!cached(playerId) || number(call("getBalance", playerId)) < amount) {
            return CompletableFuture.completedFuture(false);
        }
        return future(call("removeBalance", playerId, amount));
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> deposit(UUID playerId, long amount) {
        if (amount <= 0L) return CompletableFuture.completedFuture(true);
        if (api() == null) return CompletableFuture.completedFuture(false);
        return future(call("addBalance", playerId, amount));
    }

    private boolean cached(UUID playerId) {
        return Boolean.TRUE.equals(call("isOnlineCached", playerId));
    }

    private CompletableFuture<Boolean> future(Object value) {
        if (value instanceof CompletableFuture<?> future) {
            return future.thenApply(result -> true).exceptionally(error -> false);
        }
        return CompletableFuture.completedFuture(false);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Object call(String methodName, Object... args) {
        try {
            Object api = api();
            if (api == null) {
                return null;
            }
            return find(api.getClass(), methodName, args.length).invoke(api, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
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

    private Method find(Class<?> type, String name, int parameters) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
