package com.kammoun.raven.continuum;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ContinuumEntityIdReservation {

    private Method reserveMethod;
    private boolean searched;

    public Result reserve(PlayerConfigurationConnection connection, int clientEntityId) {
        if (connection == null || clientEntityId <= 0) {
            return Result.failed("invalid input: connection=" + (connection != null)
                    + ", clientEntityId=" + clientEntityId);
        }

        Result directResult = reserveOnConnection(connection, clientEntityId);
        if (directResult.reserved()) {
            return directResult;
        }

        Method method = reserveMethod();
        if (method == null) {
            return Result.failed(directResult.failureReason()
                    + "; Bukkit UnsafeValues does not expose reserveEntityId(PlayerConfigurationConnection,int)");
        }

        try {
            method.invoke(Bukkit.getUnsafe(), connection, clientEntityId);
            return Result.success();
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return Result.failed(cause.getClass().getName() + ": " + cause.getMessage());
        } catch (ReflectiveOperationException exception) {
            return Result.failed(exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private Result reserveOnConnection(PlayerConfigurationConnection connection, int clientEntityId) {
        Result methodResult = reserveWithDeclaredMethod(connection, clientEntityId);
        if (methodResult.reserved()) {
            return methodResult;
        }

        Result fieldResult = reserveWithDeclaredField(connection, clientEntityId);
        if (fieldResult.reserved()) {
            return fieldResult;
        }

        return Result.failed(methodResult.failureReason() + "; " + fieldResult.failureReason());
    }

    private Result reserveWithDeclaredMethod(PlayerConfigurationConnection connection, int clientEntityId) {
        try {
            Method method = findMethod(connection.getClass(), "setInternalPluginDefinedEntityId", int.class);
            method.setAccessible(true);
            method.invoke(connection, clientEntityId);
            return Result.success();
        } catch (NoSuchMethodException exception) {
            return Result.failed(connection.getClass().getName()
                    + " does not expose setInternalPluginDefinedEntityId(int)");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return Result.failed(cause.getClass().getName() + ": " + cause.getMessage());
        } catch (ReflectiveOperationException | SecurityException exception) {
            return Result.failed(exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private Result reserveWithDeclaredField(PlayerConfigurationConnection connection, int clientEntityId) {
        try {
            Field field = findField(connection.getClass(), "internalPluginDefinedEntityId");
            field.setAccessible(true);
            field.set(connection, clientEntityId);
            return Result.success();
        } catch (NoSuchFieldException exception) {
            return Result.failed(connection.getClass().getName()
                    + " does not expose internalPluginDefinedEntityId field");
        } catch (ReflectiveOperationException | SecurityException exception) {
            return Result.failed(exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private Method reserveMethod() {
        if (searched) {
            return reserveMethod;
        }

        searched = true;
        try {
            reserveMethod = Bukkit.getUnsafe().getClass()
                    .getMethod("reserveEntityId", PlayerConfigurationConnection.class, int.class);
        } catch (NoSuchMethodException ignored) {
            reserveMethod = null;
        }
        return reserveMethod;
    }

    public record Result(boolean reserved, String failureReason) {

        public static Result success() {
            return new Result(true, "");
        }

        public static Result failed(String reason) {
            return new Result(false, reason == null || reason.isBlank() ? "unknown" : reason);
        }
    }
}
