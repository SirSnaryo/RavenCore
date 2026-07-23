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
        if (!refillRunning.compareAndSet(false, true)) {
            return;
        }
        List<CompletableFuture<Void>> fills = new ArrayList<>();
        config.dimensions().forEach(dimension -> {
            long currentSize = store.getPoolSize(serverId, dimension);
            if (currentSize < config.targetSize()) {
                fills.add(fillUntilTarget(dimension, currentSize, "initial generation", 0));
            }
        });
        if (fills.isEmpty()) {
            refillRunning.set(false);
            return;
        }
        CompletableFuture.allOf(fills.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    refillRunning.set(false);
                    if (error != null && !stopped) {
                        logger.warning("[LocationPool] Initial fill failed: " + error.getMessage());
                    }
                });
    }

    private void refill() {
        if (stopped || !refillRunning.compareAndSet(false, true)) {
            return;
        }

        List<CompletableFuture<Void>> fills = new ArrayList<>();
        config.dimensions().forEach(dimension -> {
            long currentSize = store.getPoolSize(serverId, dimension);
            if (currentSize < config.targetSize()) {
                fills.add(fillUntilTarget(dimension, currentSize, "refill", 0));
            }
        });

        if (fills.isEmpty()) {
            refillRunning.set(false);
            return;
        }
        CompletableFuture.allOf(fills.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    refillRunning.set(false);
                    if (error != null && !stopped) {
                        logger.warning("[LocationPool] Refill failed: " + error.getMessage());
                    }
                });
    }

    private CompletableFuture<Void> fillUntilTarget(LocationDimension dimension, long startingSize,
                                                    String operation, int attempts) {
        String dimensionName = dimension.redisName();
        if (attempts == 0 && "refill".equals(operation)) {
            logger.info("[LocationPool] Refilling " + dimensionName + " pool for " + serverId
                    + ": " + startingSize + "/" + config.targetSize()
                    + " ready...");
        } else if (attempts == 0) {
            logger.info("[LocationPool] Filling " + dimensionName + " pool for " + serverId
                    + ": " + startingSize + "/" + config.targetSize() + " ready...");
        }

        if (stopped) {
            return CompletableFuture.completedFuture(null);
        }

        long currentSize = store.getPoolSize(serverId, dimension);
        if (currentSize >= config.targetSize()) {
            logger.info("[LocationPool] " + dimensionName + " pool for " + serverId
                    + " is full: " + currentSize + "/" + config.targetSize() + " ready.");
            return CompletableFuture.completedFuture(null);
        }

        return generator.generate(dimension)
                .handleAsync((result, error) -> {
                    if (!stopped && error == null && result.isPresent()) {
                        store.push(result.get());
                    } else if (error != null && attempts % 20 == 0) {
                        logger.warning("[LocationPool] " + dimensionName
                                + " candidate search failed: " + error.getMessage());
                    }
                    return null;
                })
                .thenCompose(ignored -> {
                    int nextAttempt = attempts + 1;
                    if (!stopped && nextAttempt % 10 == 0) {
                        long size = store.getPoolSize(serverId, dimension);
                        logger.info("[LocationPool] " + dimensionName + " pool progress for "
                                + serverId + ": " + size + "/" + config.targetSize()
                                + " ready after " + nextAttempt + " single attempts.");
                    }
                    return fillUntilTarget(dimension, startingSize, operation, nextAttempt);
                });
    }
}
