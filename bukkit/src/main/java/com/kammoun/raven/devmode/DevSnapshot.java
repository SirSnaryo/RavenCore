package com.kammoun.raven.devmode;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Collection;

/**
 * One server sample, formatted once per tick and reused for every viewer, so the HUD work stays
 * independent of how many developers are watching.
 */
public record DevSnapshot(
        String tps,
        String mspt,
        String heap,
        String allocated,
        String cpu,
        String processCpu,
        String systemCpu,
        String chunks,
        String entities,
        String online,
        String uptime,
        String ping,
        String server,
        BarColor color,
        double progress
) {

    public static DevSnapshot capture() {
        double[] tps = Bukkit.getTPS();
        double recentTps = tps[0];
        double mspt = Bukkit.getAverageTickTime();

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long usedMb = heap.getUsed() / 1024L / 1024L;
        long committedMb = heap.getCommitted() / 1024L / 1024L;
        long maxMb = heap.getMax() / 1024L / 1024L;

        int chunks = 0;
        int entities = 0;
        for (World world : Bukkit.getWorlds()) {
            chunks += world.getLoadedChunks().length;
            entities += world.getLivingEntities().size();
        }

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        int ping = averagePing(online);

        return new DevSnapshot(
                colour(recentTps / 20D) + String.format("%.2f", recentTps),
                colour(1D - (mspt / 50D)) + String.format("%.1f", mspt),
                "&#A855F7" + usedMb + "/" + committedMb + "M",
                "&#3A86FF" + committedMb + "/" + maxMb + "M",
                readCpu(),
                readProcessCpu(),
                readSystemCpu(),
                "&#FF9F1C" + chunks,
                "&#FF9F1C" + entities,
                "&#4CC9F0" + online.size(),
                "&#FFD447" + readUptime(),
                ping(ping),
                "&#4CC9F0" + Bukkit.getServer().getName(),
                color(recentTps, mspt),
                progress(mspt));
    }

    static int averagePing(Collection<? extends Player> online) {
        if (online.isEmpty()) return -1;
        long total = 0L;
        for (Player player : online) {
            total += Math.max(0, player.getPing());
        }
        return (int) (total / online.size());
    }

    static String ping(int ping) {
        if (ping < 0) return "&#7A7A7An/a";
        return (ping < 100 ? "&#4CC9F0" : "&#FF9F1C") + ping + "ms";
    }

    /** Takes a health ratio where 1 is healthy, so the number is green before it degrades. */
    static String colour(double health) {
        if (health >= 0.95D) return "&#33FF66";
        if (health >= 0.75D) return "&#FFD447";
        return "&#D90429";
    }

    static String readCpu() {
        com.sun.management.OperatingSystemMXBean bean = bean();
        return bean == null ? "&#7A7A7An/a" : "&#A855F7" + percent(bean.getCpuLoad());
    }

    static String readProcessCpu() {
        com.sun.management.OperatingSystemMXBean bean = bean();
        return bean == null ? "&#7A7A7An/a" : "&#A855F7" + percent(bean.getProcessCpuLoad());
    }

    static String readSystemCpu() {
        com.sun.management.OperatingSystemMXBean bean = bean();
        return bean == null ? "&#7A7A7An/a" : "&#A855F7" + percent(bean.getSystemCpuLoad());
    }

    private static com.sun.management.OperatingSystemMXBean bean() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
            return bean;
        }
        return null;
    }

    private static String percent(double load) {
        if (load < 0D) return "n/a";
        return String.format("%.0f%%", load * 100D);
    }

    static String readUptime() {
        long seconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        return (seconds / 3600L) + "h " + ((seconds % 3600L) / 60L) + "m";
    }

    static BarColor color(double tps, double mspt) {
        return worse(colorForTps(tps), colorForMspt(mspt));
    }

    static BarColor colorForMspt(double mspt) {
        if (mspt < 40D) return BarColor.GREEN;
        if (mspt < 50D) return BarColor.YELLOW;
        return BarColor.RED;
    }

    static BarColor colorForTps(double tps) {
        if (tps >= 19D) return BarColor.GREEN;
        if (tps >= 15D) return BarColor.YELLOW;
        return BarColor.RED;
    }

    static double progress(double mspt) {
        return Math.max(0D, Math.min(1D, 1D - (mspt / 50D)));
    }

    private static BarColor worse(BarColor left, BarColor right) {
        return Math.max(rank(left), rank(right)) == rank(BarColor.RED) ? BarColor.RED
                : Math.max(rank(left), rank(right)) == rank(BarColor.YELLOW) ? BarColor.YELLOW
                : BarColor.GREEN;
    }

    private static int rank(BarColor color) {
        if (color == BarColor.RED) return 2;
        if (color == BarColor.YELLOW) return 1;
        return 0;
    }
}
