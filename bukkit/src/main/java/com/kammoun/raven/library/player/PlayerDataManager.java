package com.kammoun.raven.library.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages in-memory per-player data stores.
 * Automatically creates a store on join and removes it on quit.
 *
 * Usage:
 * <pre>
 *   PlayerDataManager playerData = new PlayerDataManager(plugin);
 *   playerData.get(player).set("kills", 5);
 *   int kills = playerData.get(player).getInt("kills", 0);
 * </pre>
 */
public class PlayerDataManager implements Listener {

    private final Map<UUID, PlayerDataStore> stores = new HashMap<>();

    public PlayerDataManager(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Handle already-online players (plugin reload scenario)
        for (Player p : Bukkit.getOnlinePlayers()) {
            stores.put(p.getUniqueId(), new PlayerDataStore(p.getUniqueId()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        stores.put(e.getPlayer().getUniqueId(), new PlayerDataStore(e.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stores.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Returns the data store for the given player, creating one if absent.
     */
    public PlayerDataStore get(Player player) {
        return stores.computeIfAbsent(player.getUniqueId(), PlayerDataStore::new);
    }

    /**
     * Returns the data store for the given UUID, or null if the player is not online.
     */
    public PlayerDataStore get(UUID uuid) {
        return stores.get(uuid);
    }

    public boolean has(Player player) {
        return stores.containsKey(player.getUniqueId());
    }

    /**
     * Clears and removes the store for the given player.
     */
    public void invalidate(Player player) {
        stores.remove(player.getUniqueId());
    }
}
