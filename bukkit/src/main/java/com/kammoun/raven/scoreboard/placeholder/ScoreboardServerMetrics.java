package com.kammoun.raven.scoreboard.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Locale;

class ScoreboardServerMetrics {

    String tps() {
        double[] values = tpsArray(invokeStatic(Bukkit.class, "getTPS"));
        if (values.length == 0) {
            return "N/A";
        }
        return formatTps(values[0]);
    }

    String mspt() {
        Object value = invokeStatic(Bukkit.class, "getAverageTickTime");
        if (value instanceof Number number) {
            return formatMspt(number.doubleValue());
        }

        long[] tickTimes = tickTimes();
        if (tickTimes.length == 0) {
            return "N/A";
        }

        long totalNanos = 0L;
        for (long tickTime : tickTimes) {
            totalNanos += Math.max(0L, tickTime);
        }
        return formatMspt((totalNanos / (double) tickTimes.length) / 1_000_000D);
    }

    String cpu() {
        java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
        if (baseBean instanceof com.sun.management.OperatingSystemMXBean systemBean) {
            double load = systemBean.getProcessCpuLoad();
            if (load >= 0D) {
                return String.format(Locale.US, "%.0f%%", load * 100D);
            }
        }
        return "N/A";
    }

    String regionTps(Player player, int index) {
        double[] values = regionTpsArray(player);
        if (values.length <= index) {
            return "N/A";
        }
        return formatTps(values[index]);
    }

    private double[] regionTpsArray(Player player) {
        Location location = player.getLocation();
        Object value = invokeStatic(Bukkit.class, "getRegionTPS", Location.class, location);
        if (value == null) {
            Server server = Bukkit.getServer();
            value = invoke(server, "getRegionTPS", Location.class, location);
        }
        return tpsArray(value);
    }

    private long[] tickTimes() {
        Object value = invoke(Bukkit.getServer(), "getTickTimes");
        if (value instanceof long[] tickTimes) {
            return tickTimes;
        }
        return new long[0];
    }

    private static double[] tpsArray(Object value) {
        if (value instanceof double[] tps) {
            return tps;
        }
        return new double[0];
    }

    private static String formatTps(double value) {
        return String.format(Locale.US, "%.2f", Math.min(20D, Math.max(0D, value)));
    }

    private static String formatMspt(double value) {
        return String.format(Locale.US, "%.1fms", Math.max(0D, value));
    }

    private static Object invokeStatic(Class<?> owner, String name, Class<?> parameterType, Object parameter) {
        try {
            Method method = owner.getMethod(name, parameterType);
            return method.invoke(null, parameter);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeStatic(Class<?> owner, String name) {
        try {
            Method method = owner.getMethod(name);
            return method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String name, Class<?> parameterType, Object parameter) {
        try {
            Method method = target.getClass().getMethod(name, parameterType);
            return method.invoke(target, parameter);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
