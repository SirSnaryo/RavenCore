package com.kammoun.raven.stats.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.stats.config.StatsConfig;
import com.kammoun.raven.stats.db.StatsRepository;
import com.kammoun.raven.stats.model.StatsDelta;
import com.kammoun.raven.stats.model.StatsSession;
import com.kammoun.raven.stats.model.StatsSnapshot;
import com.kammoun.raven.stats.model.StatsType;
import com.kammoun.raven.stats.model.StatsWrite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory hot path plus bounded, batched persistence.
 *
 * No gameplay event submits database work. Periodic cycles drain every player
 * into a single transaction, and a shared cycle guard prevents a slow database
 * from building an unbounded backlog of flush/refresh jobs.
 */
public final class StatsService {
    private final RavenCore plugin;
    private final StatsRepository repository;
    private volatile StatsConfig config;
    private final Map<UUID, StatsSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeLoadTokens = new ConcurrentHashMap<>();
    private final AtomicLong loadTokenSequence = new AtomicLong();
    private final ConcurrentLinkedQueue<StatsWrite> detachedWrites = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean persistenceCycleRunning = new AtomicBoolean();
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final ThreadPoolExecutor databaseExecutor;
    private SchedulerUtil.Task flushTask;
    private SchedulerUtil.Task refreshTask;

    public StatsService(RavenCore plugin, StatsRepository repository, StatsConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "RavenCore-Stats-DB-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        databaseExecutor = new ThreadPoolExecutor(
                config.databaseWorkers(), config.databaseWorkers(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.databaseQueueCapacity()),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) load(player);
        scheduleTasks();
    }

    public void updateConfig(StatsConfig config) {
        this.config = config;
        int workers = config.databaseWorkers();
        if (workers > databaseExecutor.getMaximumPoolSize()) {
            databaseExecutor.setMaximumPoolSize(workers);
            databaseExecutor.setCorePoolSize(workers);
        } else {
            databaseExecutor.setCorePoolSize(workers);
            databaseExecutor.setMaximumPoolSize(workers);
        }
        scheduleTasks();
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        long token = loadTokenSequence.incrementAndGet();
        activeLoadTokens.put(uuid, token);
        // Install a session immediately so activity during a large join wave is
        // counted while this player's database read waits in the bounded queue.
        sessions.put(uuid, new StatsSession(StatsSnapshot.empty(uuid, name)));
        submit(() -> {
            try {
                StatsSnapshot persisted = repository.loadOrCreate(uuid, name);
                sessions.compute(uuid, (key, existing) -> {
                    if (!Long.valueOf(token).equals(activeLoadTokens.get(uuid))) return existing;
                    if (existing == null) return new StatsSession(persisted);
                    existing.replacePersisted(persisted);
                    return existing;
                });
            } catch (Exception error) {
                plugin.getLogger().warning("[Stats] Failed to load " + name + ": " + error.getMessage());
            }
        }, "load " + name);
    }

    public void unload(Player player) {
        activeLoadTokens.remove(player.getUniqueId());
        StatsSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.capturePlayTime();
        StatsDelta delta = session.drain();
        if (!delta.isEmpty()) {
            enqueueDetachedWrite(new StatsWrite(player.getUniqueId(), player.getName(), delta, session));
            requestFlush();
        }
    }

    public void increment(Player player, StatsType type) {
        increment(player.getUniqueId(), type, 1D);
    }

    public void increment(UUID uuid, StatsType type, double amount) {
        if (!config.tracks(type)) return;
        StatsSession session = sessions.get(uuid);
        if (session != null) session.increment(type, amount);
    }

    public Optional<StatsSnapshot> cached(UUID uuid) {
        StatsSession session = sessions.get(uuid);
        if (session == null) return Optional.empty();
        session.capturePlayTime();
        return Optional.of(session.snapshot());
    }

    public CompletableFuture<Optional<StatsSnapshot>> find(String name) {
        Player local = Bukkit.getPlayerExact(name);
        if (local != null) {
            Optional<StatsSnapshot> value = cached(local.getUniqueId());
            if (value.isPresent()) return CompletableFuture.completedFuture(value);
        }
        return supply(() -> {
            try {
                return repository.findByName(name);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    public CompletableFuture<Boolean> mutate(String playerName, StatsType type, String operation, double amount) {
        return supply(() -> {
            mutationLock.lock();
            try {
                Optional<StatsSnapshot> optional = repository.findByName(playerName);
                if (optional.isEmpty()) return false;
                StatsSnapshot target = optional.get();
                flushLocalInline(target.uuid());
                boolean changed = repository.mutate(target.uuid(), type, operation, amount);
                refreshLocalInline(target.uuid());
                return changed;
            } catch (Exception error) {
                throw new RuntimeException(error);
            } finally {
                mutationLock.unlock();
            }
        });
    }

    public CompletableFuture<Boolean> reset(String playerName) {
        return supply(() -> {
            mutationLock.lock();
            try {
                Optional<StatsSnapshot> optional = repository.findByName(playerName);
                if (optional.isEmpty()) return false;
                StatsSnapshot target = optional.get();
                flushLocalInline(target.uuid());
                boolean changed = repository.reset(target.uuid());
                refreshLocalInline(target.uuid());
                return changed;
            } catch (Exception error) {
                throw new RuntimeException(error);
            } finally {
                mutationLock.unlock();
            }
        });
    }

    /**
     * Coalesces any number of timer/quit triggers into at most one database job.
     */
    public void requestFlush() {
        if (!persistenceCycleRunning.compareAndSet(false, true)) return;
        if (!submit(() -> {
            try {
                flushCycle();
            } finally {
                persistenceCycleRunning.set(false);
            }
        }, "batch flush")) {
            persistenceCycleRunning.set(false);
        }
    }

    public void shutdown() {
        if (flushTask != null) flushTask.cancel();
        if (refreshTask != null) refreshTask.cancel();
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
                databaseExecutor.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            databaseExecutor.shutdownNow();
        }

        List<StatsWrite> finalWrites = drainWrites();
        if (!finalWrites.isEmpty()) {
            try {
                repository.applyDeltas(finalWrites, config.batchSize());
            } catch (Exception error) {
                plugin.getLogger().warning("[Stats] Final batch save failed for "
                        + finalWrites.size() + " players: " + error.getMessage());
            }
        }
        sessions.clear();
        activeLoadTokens.clear();
        detachedWrites.clear();
    }

    private void scheduleTasks() {
        if (flushTask != null) flushTask.cancel();
        if (refreshTask != null) refreshTask.cancel();
        flushTask = SchedulerUtil.runAsyncTimer(this::requestFlush,
                config.flushIntervalTicks(), config.flushIntervalTicks());
        refreshTask = SchedulerUtil.runAsyncTimer(this::requestRefresh,
                config.refreshIntervalTicks(), config.refreshIntervalTicks());
    }

    private void requestRefresh() {
        if (!persistenceCycleRunning.compareAndSet(false, true)) return;
        if (!submit(() -> {
            try {
                mutationLock.lock();
                // Persist first so the authoritative totals include every
                // delta drained from this backend before reconciliation.
                flushCycle();
                List<UUID> uuids = new ArrayList<>(sessions.keySet());
                Map<UUID, StatsSnapshot> refreshed = repository.findByUuids(uuids, config.batchSize());
                refreshed.forEach((uuid, snapshot) -> {
                    StatsSession session = sessions.get(uuid);
                    if (session != null) session.replacePersisted(snapshot);
                });
            } catch (Exception error) {
                plugin.getLogger().warning("[Stats] Bulk refresh failed: " + error.getMessage());
            } finally {
                if (mutationLock.isHeldByCurrentThread()) mutationLock.unlock();
                persistenceCycleRunning.set(false);
            }
        }, "bulk refresh")) {
            persistenceCycleRunning.set(false);
        }
    }

    private void flushCycle() {
        mutationLock.lock();
        try {
        List<StatsWrite> writes = drainWrites();
        if (writes.isEmpty()) return;
        try {
            repository.applyDeltas(writes, config.batchSize());
        } catch (Exception error) {
            restoreFailed(writes);
            plugin.getLogger().warning("[Stats] Batch save failed for " + writes.size()
                    + " players; queued for retry: " + error.getMessage());
        }
        } finally {
            mutationLock.unlock();
        }
    }

    private List<StatsWrite> drainWrites() {
        List<StatsWrite> writes = new ArrayList<>(sessions.size() + detachedWrites.size());
        StatsWrite detached;
        while ((detached = detachedWrites.poll()) != null) writes.add(detached);
        for (Map.Entry<UUID, StatsSession> entry : sessions.entrySet()) {
            StatsSession session = entry.getValue();
            session.capturePlayTime();
            StatsDelta delta = session.drain();
            if (!delta.isEmpty()) {
                writes.add(new StatsWrite(entry.getKey(), session.playerName(), delta, session));
            }
        }
        return writes;
    }

    private void restoreFailed(List<StatsWrite> writes) {
        for (StatsWrite write : writes) {
            if (sessions.get(write.uuid()) == write.owner()) write.owner().restore(write.delta());
            else enqueueDetachedWrite(write);
        }
    }

    private void enqueueDetachedWrite(StatsWrite write) {
        int capacity = config.detachedWriteCapacity();
        while (detachedWrites.size() >= capacity && detachedWrites.poll() != null) {
            plugin.getLogger().warning("[Stats] Detached write queue is full; dropped oldest pending write.");
        }
        detachedWrites.add(write);
    }

    private void flushLocalInline(UUID uuid) throws SQLException {
        StatsSession session = sessions.get(uuid);
        if (session == null) return;
        session.capturePlayTime();
        StatsDelta delta = session.drain();
        if (delta.isEmpty()) return;
        try {
            repository.applyDelta(uuid, session.playerName(), delta);
        } catch (SQLException error) {
            session.restore(delta);
            throw error;
        }
    }

    private void refreshLocalInline(UUID uuid) throws SQLException {
        StatsSession session = sessions.get(uuid);
        if (session != null) repository.findByUuid(uuid).ifPresent(session::replacePersisted);
    }

    private boolean submit(Runnable task, String description) {
        try {
            databaseExecutor.execute(task);
            return true;
        } catch (RejectedExecutionException rejected) {
            if (!databaseExecutor.isShutdown()) {
                plugin.getLogger().warning("[Stats] Database queue is full; rejected " + description + ".");
            }
            return false;
        }
    }

    private <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        boolean accepted = submit(() -> {
            try {
                result.complete(supplier.get());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        }, "command lookup");
        if (!accepted) {
            result.completeExceptionally(new RejectedExecutionException("Stats database queue is full"));
        }
        return result;
    }
}
