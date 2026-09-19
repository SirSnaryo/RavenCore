package com.kammoun.raven.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Locale;

public class ServerMetrics {

    private static final long GLOBAL_CACHE_MILLIS = 1_000L;
    private static final Method BUKKIT_GET_TPS = method(Bukkit.class, "getTPS");
    private static final Method BUKKIT_GET_AVERAGE_TICK_TIME = method(Bukkit.class, "getAverageTickTime");
    private static final Method BUKKIT_GET_REGION_TPS = method(Bukkit.class, "getRegionTPS", Location.class);

    private long globalCacheUntilMillis;
    private String cachedTps = "N/A";
    private String cachedMspt = "N/A";
    private String cachedCpu = "N/A";
    private Method serverGetRegionTps;
    private Method serverGetTickTimes;

    public String tps() {
        refreshGlobalCache();
        return cachedTps;
    }

    public String mspt() {
        refreshGlobalCache();
        return cachedMspt;
    }

    public String cpu() {
        refreshGlobalCache();
        return cachedCpu;
    }

    public String regionTps(Player player, int index) {
        double[] values = regionTpsArray(player);
        if (values.length <= index) {
            return "N/A";
        }
        return formatTps(values[index]);
    }

    private void refreshGlobalCache() {
        long now = System.currentTimeMillis();
        if (now < globalCacheUntilMillis) {
            return;
        }

        cachedTps = readTps();
        cachedMspt = readMspt();
        cachedCpu = readCpu();
        globalCacheUntilMillis = now + GLOBAL_CACHE_MILLIS;
    }

    private String readTps() {
        double[] values = tpsArray(invoke(BUKKIT_GET_TPS, null));
        if (values.length == 0) {
            return "N/A";
        }
        return formatTps(values[0]);
    }

    private String readMspt() {
        Object value = invoke(BUKKIT_GET_AVERAGE_TICK_TIME, null);
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

    private String readCpu() {
        java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
        if (baseBean instanceof com.sun.management.OperatingSystemMXBean systemBean) {
            double load = systemBean.getProcessCpuLoad();
            if (load >= 0D) {
                return String.format(Locale.US, "%.0f%%", load * 100D);
            }
        }
        return "N/A";
    }

    private double[] regionTpsArray(Player player) {
        Location location = player.getLocation();
        Object value = invoke(BUKKIT_GET_REGION_TPS, null, location);
        if (value == null) {
            value = invoke(serverGetRegionTps(), Bukkit.getServer(), location);
        }
        return tpsArray(value);
    }

    private long[] tickTimes() {
        Object value = invoke(serverGetTickTimes(), Bukkit.getServer());
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

    private Method serverGetRegionTps() {
        if (serverGetRegionTps == null) {
            serverGetRegionTps = method(Bukkit.getServer().getClass(), "getRegionTPS", Location.class);
        }
        return serverGetRegionTps;
    }

    private Method serverGetTickTimes() {
        if (serverGetTickTimes == null) {
            serverGetTickTimes = method(Bukkit.getServer().getClass(), "getTickTimes");
        }
        return serverGetTickTimes;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... parameters) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, parameters);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
