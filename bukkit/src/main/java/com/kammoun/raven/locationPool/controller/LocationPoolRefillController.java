package com.kammoun.raven.locationPool.controller;

import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.locationPool.config.LocationPoolConfig;
import com.kammoun.raven.locationPool.generator.SafeLocationGenerator;
import com.kammoun.raven.locationPool.redis.RedisLocationPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class LocationPoolRefillController {

    private final String serverId;
    private final LocationPoolConfig config;
    private final RedisLocationPool store;
    private final SafeLocationGenerator generator;
    private final Logger logger;
    private final AtomicBoolean refillRunning = new AtomicBoolean();
    private volatile boolean stopped;
    private SchedulerUtil.Task refillTask;

    public LocationPoolRefillController(String serverId, LocationPoolConfig config,
                                        RedisLocationPool store, SafeLocationGenerator generator,
                                        Logger logger) {
        this.serverId = serverId;
        this.config = config;
        this.store = store;
        this.generator = generator;
        this.logger = logger;
    }

    public void start() {
        stopped = false;
        SchedulerUtil.runAsync(this::initialFill);
        refillTask = SchedulerUtil.runAsyncTimer(this::refill, config.refillIntervalTicks(),
                config.refillIntervalTicks());
    }

    public void stop() {
        stopped = true;
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
    }

    private void initialFill() {
        runRefillCycle("initial generation", config.targetSize());
    }

    private void refill() {
        runRefillCycle("refill", config.maxRefillPerCycle());
    }

    private void runRefillCycle(String operation, int maxAddsPerDimension) {
        if (stopped || !refillRunning.compareAndSet(false, true)) {
            return;
        }

        List<CompletableFuture<Void>> fills = new ArrayList<>();
        config.dimensions().forEach(dimension -> {
            long currentSize = store.getPoolSize(serverId, dimension);
            if (currentSize < config.targetSize()) {
                fills.add(fillDimension(dimension, currentSize, operation, maxAddsPerDimension));
            }
        });

        if (fills.isEmpty()) {
            refillRunning.set(false);
            return;
        }
        CompletableFuture.allOf(fills.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    refillRunning.set(false);
                    if (error != null && !stopped) logger.warning("[LocationPool] "
                            + operation + " failed: " + error.getMessage());
                });
    }

    private CompletableFuture<Void> fillDimension(LocationDimension dimension, long startingSize,
                                                  String operation, int maxAddsPerDimension) {
        String dimensionName = dimension.redisName();
        if ("refill".equals(operation)) {
            logger.info("[LocationPool] Refilling " + dimensionName + " pool for " + serverId
                    + ": " + startingSize + "/" + config.targetSize()
                    + " ready...");
        } else {
            logger.info("[LocationPool] Filling " + dimensionName + " pool for " + serverId
                    + ": " + startingSize + "/" + config.targetSize() + " ready...");
        }

        CompletableFuture<Void> done = new CompletableFuture<>();
        long missing = Math.max(0L, config.targetSize() - startingSize);
        int maxAdds = (int) Math.min(missing, Math.max(1, maxAddsPerDimension));
        fillNext(dimension, operation, maxAdds, 0, 0, done);
        return done;
    }

    private void fillNext(LocationDimension dimension, String operation, int maxAdds,
                          int attempts, int added, CompletableFuture<Void> done) {
        if (stopped || done.isDone()) {
            done.complete(null);
            return;
        }
        long currentSize = store.getPoolSize(serverId, dimension);
        if (currentSize >= config.targetSize() || added >= maxAdds
                || attempts >= config.maxAttemptsPerCycle()) {
            logCycleResult(dimension, operation, currentSize, attempts, added);
            done.complete(null);
            return;
        }

        generator.generate(dimension).whenComplete((result, error) -> {
            int nextAttempts = attempts + 1;
            int nextAdded = added;
            if (!stopped && error == null && result.isPresent()) {
                store.push(result.get());
                nextAdded++;
            } else if (error != null && nextAttempts % 20 == 0) {
                logger.warning("[LocationPool] " + dimension.redisName()
                        + " candidate search failed: " + error.getMessage());
            }
            fillNext(dimension, operation, maxAdds, nextAttempts, nextAdded, done);
        });
    }

    private void logCycleResult(LocationDimension dimension, String operation,
                                long currentSize, int attempts, int added) {
        if (currentSize >= config.targetSize()) {
            logger.info("[LocationPool] " + dimension.redisName() + " pool for " + serverId
                    + " is full: " + currentSize + "/" + config.targetSize()
                    + " ready after " + attempts + " attempts.");
            return;
        }
        if (added > 0 || attempts >= config.maxAttemptsPerCycle()) {
            logger.info("[LocationPool] " + operation + " " + dimension.redisName()
                    + " pool for " + serverId + ": +" + added + ", now "
                    + currentSize + "/" + config.targetSize() + " after " + attempts
                    + "/" + config.maxAttemptsPerCycle() + " attempts.");
        }
    }
}
