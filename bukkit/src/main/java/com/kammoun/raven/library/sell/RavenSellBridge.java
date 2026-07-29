package com.kammoun.raven.library.sell;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.UUID;

public final class RavenSellBridge implements SellBridgeInterface {

    private static final String PROVIDER_CLASS = "com.kammoun.raven.api.RavenSellProvider";

    @Override
    public boolean available() {
        try {
            Object loaded = providerClass().getMethod("isLoaded").invoke(null);
            return Boolean.TRUE.equals(loaded) && provider() != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public boolean sellable(ItemStack item) {
        Object value = call("isSellable", item);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public double price(ItemStack item) {
        Object value = call("getPrice", item);
        return value instanceof Number number ? number.doubleValue() : -1D;
    }

    @Override
    public double effectivePrice(UUID playerId, ItemStack item) {
        if (!available()) {
            return -1D;
        }

        double base = price(item);
        Object category = call("getCategory", item);
        if (base < 0D || category == null) {
            return base;
        }

        Object multiplier = call("getMultiplier", playerId, category);
        return multiplier instanceof Number number ? base * number.doubleValue() : base;
    }

    @Override
    public double record(UUID playerId, ItemStack item, int amount, double maximumEarnings) {
        Object value = call("recordSellCapped", playerId, item, amount, maximumEarnings);
        return value instanceof Number number ? number.doubleValue() : -1D;
    }

    private Object call(String methodName, Object... args) {
        try {
            Object provider = provider();
            if (provider == null) {
                return null;
            }
            return find(provider.getClass(), methodName, args.length).invoke(provider, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Object provider() throws ReflectiveOperationException {
        return providerClass().getMethod("get").invoke(null);
    }

    private Class<?> providerClass() throws ClassNotFoundException {
        return Class.forName(PROVIDER_CLASS);
    }

    private Method find(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
