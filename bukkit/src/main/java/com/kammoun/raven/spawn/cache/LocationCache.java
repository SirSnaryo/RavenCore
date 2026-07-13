package com.kammoun.raven.spawn.cache;

import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.library.location.NetworkLocation;
import com.kammoun.raven.spawn.db.LocationRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory, read-through cache of every server's SPAWN/AFK rows, network-wide.
 * Rebuilt wholesale on reload() (write-triggered via LocationsUpdatedEvent, and
 * periodically as a fallback).
 *
 * reload() calls can race (e.g. the initial startup load still in flight when an
 * admin's /setspawn triggers its own reload, or the 60s periodic task overlapping
 * a manual edit). A monotonic sequence guard ensures whichever reload was started
 * MOST RECENTLY always wins, regardless of which one's async DB query completes
 * first — a slower, stale in-flight reload can never silently overwrite a newer one.
 */
public class LocationCache {

    private final LocationRepository repository;
    private final JavaPlugin plugin;

    private final AtomicLong nextSequence = new AtomicLong(0);
    private final AtomicLong appliedSequence = new AtomicLong(0);

    private volatile Map<LocationType, Map<String, NetworkLocation>> snapshot = emptySnapshot();
    private volatile boolean loaded = false;

    public LocationCache(LocationRepository repository, JavaPlugin plugin) {
        this.repository = repository;
        this.plugin = plugin;
    }

    public void reload() {
        long mySequence = nextSequence.incrementAndGet();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            List<NetworkLocation> rows;
            try {
                rows = repository.findAll();
            } catch (SQLException e) {
                plugin.getLogger().warning("[Spawn] Cache reload failed: " + e.getMessage());
                return;
            }
            Map<LocationType, Map<String, NetworkLocation>> built = build(rows);
            appliedSequence.updateAndGet(prev -> {
                if (mySequence > prev) {
                    snapshot = built;
                    loaded = true;
                    return mySequence;
                }
                return prev; // a newer reload already applied — discard this late/stale result
            });
        });
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Optional<NetworkLocation> get(LocationType type, String serverName) {
        return Optional.ofNullable(snapshot.get(type)).map(byServer -> byServer.get(serverName));
    }

    public Collection<NetworkLocation> getAll(LocationType type) {
        Map<String, NetworkLocation> byServer = snapshot.get(type);
        return byServer == null ? Collections.emptyList() : Collections.unmodifiableCollection(byServer.values());
    }

    private static Map<LocationType, Map<String, NetworkLocation>> build(List<NetworkLocation> rows) {
        Map<LocationType, Map<String, NetworkLocation>> built = emptySnapshot();
        for (NetworkLocation row : rows) {
            built.get(row.getType()).put(row.getServerName(), row);
        }
        return built;
    }

    private static Map<LocationType, Map<String, NetworkLocation>> emptySnapshot() {
        Map<LocationType, Map<String, NetworkLocation>> map = new EnumMap<>(LocationType.class);
        for (LocationType type : LocationType.values()) {
            map.put(type, new java.util.HashMap<>());
        }
        return map;
    }
}
