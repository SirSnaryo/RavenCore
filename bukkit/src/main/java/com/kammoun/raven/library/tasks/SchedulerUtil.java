package com.kammoun.raven.library.tasks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * Scheduler abstraction that works on both standard Bukkit/Paper and Folia.
 *
 * Call {@link #init(JavaPlugin)} once in your plugin's onEnable before using any methods.
 *
 * Usage:
 * <pre>
 *   SchedulerUtil.init(plugin);
 *
 *   SchedulerUtil.run(() -> doSomething());
 *   SchedulerUtil.Task task = SchedulerUtil.runTimer(() -> tick(), 0L, 20L);
 *   task.cancel();
 * </pre>
 */
public final class SchedulerUtil {

    private static JavaPlugin plugin;
    private static boolean isFolia;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }

    private SchedulerUtil() {}

    public static void init(JavaPlugin instance) {
        plugin = instance;
    }

    /** Runs a task on the main thread next tick. */
    public static void run(Runnable runnable) {
        if (isFolia)
            Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
        else
            Bukkit.getScheduler().runTask(plugin, runnable);
    }

    /**
     * Runs a task on the region thread that owns the given location.
     * Required on Folia before touching entities/world state tied to a specific
     * location — the global region scheduler thread isn't bound to any region.
     */
    public static void execute(Location location, Runnable runnable) {
        if (isFolia)
            Bukkit.getRegionScheduler().execute(plugin, location, runnable);
        else
            runnable.run();
    }

    /** Runs a task asynchronously. */
    public static Task runAsync(Runnable runnable) {
        if (isFolia)
            return new Task(Bukkit.getAsyncScheduler().runNow(plugin, t -> runnable.run()));
        else
            return new Task(Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    /** Runs a task asynchronously after a delay. */
    public static Task runAsyncLater(Runnable runnable, long delayTicks) {
        if (isFolia)
            return new Task(Bukkit.getAsyncScheduler()
                    .runDelayed(plugin, t -> runnable.run(), delayTicks * 50L, TimeUnit.MILLISECONDS));
        else
            return new Task(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks));
    }

    /** Runs a task on the main thread after a delay (in ticks). */
    public static Task runLater(Runnable runnable, long delayTicks) {
        if (isFolia)
            return new Task(Bukkit.getGlobalRegionScheduler()
                    .runDelayed(plugin, t -> runnable.run(), delayTicks));
        else
            return new Task(Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
    }

    /** Runs a repeating task on the main thread. */
    public static Task runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia)
            return new Task(Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, t -> runnable.run(),
                            delayTicks < 1 ? 1 : delayTicks, periodTicks));
        else
            return new Task(Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks));
    }

    /** Runs a repeating task asynchronously. */
    public static Task runAsyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia)
            return new Task(Bukkit.getAsyncScheduler()
                    .runAtFixedRate(plugin, t -> runnable.run(),
                            delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS));
        else
            return new Task(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks));
    }

    public static class Task {

        private final Object foliaTask;
        private final BukkitTask bukkitTask;

        Task(Object foliaTask) {
            this.foliaTask = foliaTask;
            this.bukkitTask = null;
        }

        Task(BukkitTask bukkitTask) {
            this.foliaTask = null;
            this.bukkitTask = bukkitTask;
        }

        public void cancel() {
            if (foliaTask != null)
                ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) foliaTask).cancel();
            else if (bukkitTask != null)
                bukkitTask.cancel();
        }
    }
}
