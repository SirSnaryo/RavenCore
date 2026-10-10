package com.kammoun.raven.crates.player;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.crates.db.CrateKeyRepository;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Holds every online player's key counts in memory. Loaded once on join, written through
 * on every change, so reads (placeholders, menus) never touch the database.
 */
public final class CratePlayerManager {
    private final RavenCore plugin;
    private final CrateKeyRepository repository;
    private final ConcurrentMap<UUID, CratePlayer> players = new ConcurrentHashMap<>();

    public CratePlayerManager(RavenCore plugin, CrateKeyRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public CratePlayer player(UUID uuid) {
        return players.get(uuid);
    }

    public int keys(UUID uuid, String crateId) {
        CratePlayer player = players.get(uuid);
        return player == null ? 0 : player.keys(crateId);
    }

    public boolean isLoaded(UUID uuid) {
        return players.containsKey(uuid);
    }

    public void add(UUID uuid, String crateId, int amount) {
        CratePlayer player = players.get(uuid);
        if (player != null) player.add(crateId, amount);
    }

    public void set(UUID uuid, String crateId, int amount) {
        CratePlayer player = players.get(uuid);
        if (player != null) player.set(crateId, amount);
    }

    public void load(UUID uuid) {
        SchedulerUtil.runAsync(() -> {
            try {
                Map<String, Integer> keys = repository.getAllKeys(uuid);
                CratePlayer existing = players.get(uuid);
                if (existing != null) {
                    existing.replaceAll(keys);
                } else {
                    players.put(uuid, new CratePlayer(uuid, keys));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Crates] Failed to load keys for " + uuid + ": " + e.getMessage());
            }
        });
    }

    /** Used on enable/reload, when players are already online and have no entry yet. */
    public void loadOnline() {
        List<UUID> online = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) online.add(player.getUniqueId());
        players.keySet().retainAll(online);
        if (online.isEmpty()) return;
        SchedulerUtil.runAsync(() -> {
            try {
                repository.getAllKeys(online).forEach((uuid, keys) -> {
                    CratePlayer existing = players.get(uuid);
                    if (existing != null) {
                        existing.replaceAll(keys);
                    } else {
                        players.put(uuid, new CratePlayer(uuid, keys));
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[Crates] Failed to load keys for online players: " + e.getMessage());
            }
        });
    }

    public void unload(UUID uuid) {
        players.remove(uuid);
    }

    public void clear() {
        players.clear();
    }
}
