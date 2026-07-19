package com.kammoun.raven.billford.task;

import com.kammoun.raven.billford.manager.BillfordManager;
import com.kammoun.raven.library.tasks.SchedulerUtil;

/**
 * Ticks once a second (async, since {@link BillfordManager#attemptRotate} touches MySQL) and
 * drives the rotation once the shared interval elapses. Every server runs this; the manager's
 * compare-and-set ensures exactly one of them actually advances the trade.
 */
public class PresetRotationTask {

    private final BillfordManager manager;
    private SchedulerUtil.Task task;

    public PresetRotationTask(BillfordManager manager) {
        this.manager = manager;
    }

    public void start() {
        if (task != null) return;
        task = SchedulerUtil.runAsyncTimer(() -> {
            if (manager.timeUntilNextChange() <= 0) {
                manager.attemptRotate();
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
