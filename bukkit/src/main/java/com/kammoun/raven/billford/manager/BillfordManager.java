package com.kammoun.raven.billford.manager;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.billford.config.BillfordConfig;
import com.kammoun.raven.billford.config.BillfordMessages;
import com.kammoun.raven.billford.db.BillfordRepository;
import com.kammoun.raven.billford.model.BillfordPreset;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory view of the network-wide Billford state and the single place that mutates it.
 *
 * <p>Presets live in MySQL and are cached here, rebuilt wholesale on {@link #reloadPresets()}
 * (triggered by a {@code BillfordPresetsUpdatedEvent}, on a periodic fallback, and at startup).
 * The active preset + last-change timestamp are held in memory and kept authoritative by
 * {@code BillfordStateUpdatedEvent}s; the rotation itself is coordinated across servers by
 * {@link BillfordRepository#tryAdvance a compare-and-set} so only one server advances per tick.</p>
 */
public class BillfordManager {

    private final RavenCore api;
    private final BillfordRepository repository;
    private final Random random = new Random();

    private volatile BillfordConfig config;
    private volatile BillfordMessages messages;

    private final AtomicLong nextSequence = new AtomicLong(0);
    private final AtomicLong appliedSequence = new AtomicLong(0);
    private volatile Map<String, BillfordPreset> presets = Collections.emptyMap();
    private volatile boolean loaded = false;

    private volatile String currentPresetName;
    private volatile long lastChange;

    public BillfordManager(RavenCore api, BillfordRepository repository, BillfordConfig config, BillfordMessages messages) {
        this.api = api;
        this.repository = repository;
        this.config = config;
        this.messages = messages;
    }

    public void updateConfig(BillfordConfig config, BillfordMessages messages) {
        this.config = config;
        this.messages = messages;
    }

    // ─── Reads ───────────────────────────────────────────────────────────────────

    public boolean isLoaded() {
        return loaded;
    }

    public Map<String, BillfordPreset> getPresets() {
        return presets;
    }

    public BillfordPreset getPreset(String name) {
        return presets.get(name);
    }

    public boolean presetExists(String name) {
        return presets.containsKey(name);
    }

    public BillfordPreset getCurrentPreset() {
        String name = currentPresetName;
        return name == null ? null : presets.get(name);
    }

    public String getCurrentPresetName() {
        return currentPresetName;
    }

    public long timeUntilNextChange() {
        return Math.max(0, lastChange + config.getIntervalMillis() - System.currentTimeMillis());
    }

    // ─── Loading ─────────────────────────────────────────────────────────────────

    /** Async, sequence-guarded reload of every preset from MySQL. */
    public void reloadPresets() {
        long mySequence = nextSequence.incrementAndGet();
        SchedulerUtil.runAsync(() -> {
            List<BillfordPreset> rows;
            try {
                rows = repository.findAllPresets();
            } catch (SQLException e) {
                api.getLogger().warning("[Billford] Preset reload failed: " + e.getMessage());
                return;
            }
            Map<String, BillfordPreset> built = new LinkedHashMap<>();
            for (BillfordPreset preset : rows) built.put(preset.getName(), preset);

            appliedSequence.updateAndGet(prev -> {
                if (mySequence > prev) {
                    presets = built;
                    loaded = true;
                    return mySequence;
                }
                return prev; // a newer reload already applied — discard this stale result
            });
        });
    }

    /** Async reload of the rotation state row (used at startup and to resync after a lost CAS). */
    public void reloadState() {
        SchedulerUtil.runAsync(() -> {
            try {
                BillfordRepository.State state = repository.loadState();
                currentPresetName = state.currentPreset();
                lastChange = state.lastChange();
            } catch (SQLException e) {
                api.getLogger().warning("[Billford] State reload failed: " + e.getMessage());
            }
        });
    }

    /**
     * First-run seeding: if the shared table is empty, write a starter preset and point the
     * rotation at it. Idempotent across servers — the upsert keys on the preset name, so two
     * servers seeding at once converge on the same single "example" row.
     */
    public void seedDefaultIfEmpty() {
        SchedulerUtil.runAsync(() -> {
            try {
                if (!repository.findAllPresets().isEmpty()) return;

                BillfordPreset example = new BillfordPreset("example");
                example.getRequiredItems().add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 5));
                example.getRequiredItems().add(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_INGOT, 10));
                example.setTradeItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.EMERALD, 1));
                repository.upsertPreset(example);

                BillfordRepository.State state = repository.loadState();
                if (state.currentPreset() == null) {
                    repository.setState("example", System.currentTimeMillis());
                }
                api.getLogger().info("[Billford] Seeded default 'example' preset.");
                reloadPresets();
                reloadState();
            } catch (SQLException e) {
                api.getLogger().warning("[Billford] Default seeding failed: " + e.getMessage());
            }
        });
    }

    // ─── Mutations (write MySQL, refresh cache, signal the network) ──────────────

    public void savePreset(BillfordPreset preset, Consumer<Boolean> callback) {
        SchedulerUtil.runAsync(() -> {
            boolean ok = true;
            try {
                repository.upsertPreset(preset);
                putLocal(preset);
                api.getNetworkEventBus().publish(new com.kammoun.raven.library.redis.event.BillfordPresetsUpdatedEvent());
            } catch (SQLException e) {
                api.getLogger().warning("[Billford] Failed to save preset '" + preset.getName() + "': " + e.getMessage());
                ok = false;
            }
            finish(callback, ok);
        });
    }

    public void deletePreset(String name, Consumer<Boolean> callback) {
        SchedulerUtil.runAsync(() -> {
            boolean ok;
            try {
                ok = repository.deletePreset(name);
                if (ok) {
                    removeLocal(name);
                    api.getNetworkEventBus().publish(new com.kammoun.raven.library.redis.event.BillfordPresetsUpdatedEvent());
                }
            } catch (SQLException e) {
                api.getLogger().warning("[Billford] Failed to delete preset '" + name + "': " + e.getMessage());
                ok = false;
            }
            finish(callback, ok);
        });
    }

    /** Explicit admin switch to a named preset (last writer wins). */
    public void switchPreset(String name, Consumer<Boolean> callback) {
        if (!presetExists(name)) {
            finish(callback, false);
            return;
        }
        SchedulerUtil.runAsync(() -> {
            boolean ok = true;
            long now = System.currentTimeMillis();
            try {
                repository.setState(name, now);
                currentPresetName = name;
                lastChange = now;
                publishState(name, now, true);
                broadcastChange(name);
            } catch (SQLException e) {
                api.getLogger().warning("[Billford] Failed to switch preset: " + e.getMessage());
                ok = false;
            }
            finish(callback, ok);
        });
    }

    /**
     * Rotation step, called from the (async) rotation task once the interval elapses.
     * Picks a random enabled preset other than the current one and tries to claim the
     * advance via CAS; the winner broadcasts and signals, losers resync from the row.
     */
    public void attemptRotate() {
        if (!loaded) return;

        List<String> candidates = new ArrayList<>();
        for (BillfordPreset preset : presets.values()) {
            if (!preset.isEnabled()) continue;
            if (preset.getName().equals(currentPresetName)) continue;
            candidates.add(preset.getName());
        }
        if (candidates.isEmpty()) return; // nothing to rotate to — cheap in-memory no-op

        String next = candidates.get(random.nextInt(candidates.size()));
        long expected = lastChange;
        long now = System.currentTimeMillis();
        try {
            if (repository.tryAdvance(next, expected, now)) {
                currentPresetName = next;
                lastChange = now;
                publishState(next, now, true);
                broadcastChange(next);
            } else {
                reloadState(); // another server advanced first — catch up
            }
        } catch (SQLException e) {
            api.getLogger().warning("[Billford] Rotation failed: " + e.getMessage());
        }
    }

    // ─── Remote application (from cross-server events) ───────────────────────────

    public void applyRemoteState(String currentPreset, long lastChange, boolean announce) {
        this.currentPresetName = currentPreset;
        this.lastChange = lastChange;
        if (announce && currentPreset != null) broadcastChange(currentPreset);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void publishState(String preset, long lastChange, boolean announce) {
        var event = new com.kammoun.raven.library.redis.event.BillfordStateUpdatedEvent();
        event.setCurrentPreset(preset);
        event.setLastChange(lastChange);
        event.setAnnounce(announce);
        api.getNetworkEventBus().publish(event);
    }

    private void broadcastChange(String presetName) {
        String line = messages.format("preset-changed", "preset", presetName);
        if (line.isEmpty()) return;
        SchedulerUtil.run(() -> Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(line)));
    }

    private synchronized void putLocal(BillfordPreset preset) {
        Map<String, BillfordPreset> copy = new LinkedHashMap<>(presets);
        copy.put(preset.getName(), preset);
        presets = copy;
    }

    private synchronized void removeLocal(String name) {
        Map<String, BillfordPreset> copy = new LinkedHashMap<>(presets);
        copy.remove(name);
        presets = copy;
    }

    private void finish(Consumer<Boolean> callback, boolean ok) {
        if (callback != null) SchedulerUtil.run(() -> callback.accept(ok));
    }
}
