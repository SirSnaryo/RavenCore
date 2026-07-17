package com.kammoun.raven.spawner.manager;

import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.spawner.config.SpawnerConfig;
import com.kammoun.raven.spawner.model.SpawnerDrop;
import com.kammoun.raven.spawner.model.SpawnerStack;

public class SpawnerTickManager {

    private final SpawnerConfig config;
    private final SpawnerStackManager stackManager;
    private SchedulerUtil.Task task;

    public SpawnerTickManager(SpawnerConfig config, SpawnerStackManager stackManager) {
        this.config = config;
        this.stackManager = stackManager;
    }

    public void start() {
        task = SchedulerUtil.runTimer(this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        // Dispatched per-stack to the region thread that owns each spawner's location —
        // on Folia the global region scheduler thread isn't bound to any region, so
        // touching entities/world state here directly fails a thread-ownership check.
        for (SpawnerStack stack : stackManager.all()) {
            SchedulerUtil.execute(stack.getLocation(), () -> tickStack(stack, now));
        }
    }

    private void tickStack(SpawnerStack stack, long now) {
        // Paper's getNearbyPlayers uses a spatial index — O(players near location)
        boolean playerNearby = !stack.getLocation().getNearbyPlayers(config.getActivationRadius()).isEmpty();
        if (!playerNearby) return;

        long elapsed = now - stack.getLastSpawnTime();
        long requiredMs = (long) stack.getType().getSpawnIntervalSeconds() * 1000L;
        if (elapsed < requiredMs) return;

        if (!stack.canSpawn()) return;

        int stackAmt = stack.getStackAmount();
        for (SpawnerDrop drop : stack.getType().getDrops()) {
            stack.addItems(drop.getMaterial(), drop.getAmountPerSpawner() * stackAmt);
        }
        stack.addXp(stack.getType().getXpPerCycle() * stackAmt);
        stack.setLastSpawnTime(now);
    }
}
