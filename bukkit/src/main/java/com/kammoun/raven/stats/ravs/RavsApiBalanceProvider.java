package com.kammoun.raven.stats.ravs;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RavsApiBalanceProvider implements RavsBalanceProvider {

    private static final String API_CLASS = "com.kammoun.ravens.api.RavensAPI";

    @Override
    public boolean available() {
        return api() != null;
    }

    @Override
    public boolean cached(UUID playerId) {
        Object value = call("isOnlineCached", playerId);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public long cachedBalance(UUID playerId) {
        Object value = call("getBalance", playerId);
        return value instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    @Override
    public CompletableFuture<Long> balance(UUID playerId) {
        Object value = call("getBalanceAsync", playerId);
        if (value instanceof CompletableFuture<?> future) {
            return future.thenApply(result -> result instanceof Number number
                    ? Math.max(0L, number.longValue())
                    : 0L);
        }
        return CompletableFuture.completedFuture(cachedBalance(playerId));
    }

    private Object call(String methodName, UUID playerId) {
        try {
            Object api = api();
            if (api == null) {
                return null;
            }
            return find(api.getClass(), methodName).invoke(api, playerId);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Object api() {
        try {
            return Class.forName(API_CLASS).getMethod("get").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Method find(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
