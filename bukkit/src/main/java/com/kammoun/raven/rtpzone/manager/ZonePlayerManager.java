package com.kammoun.raven.rtpzone.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ZonePlayerManager {

    private final Set<UUID> players = Collections.synchronizedSet(new HashSet<>());

    public void add(Player player)         { players.add(player.getUniqueId()); }
    public void remove(Player player)      { players.remove(player.getUniqueId()); }
    public boolean contains(Player player) { return players.contains(player.getUniqueId()); }
    public boolean isEmpty()               { return players.isEmpty(); }
    public void clearAll()                 { players.clear(); }

    public Set<Player> getOnlinePlayers() {
        Set<Player> online = new HashSet<>();
        synchronized (players) {
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) online.add(p);
            }
        }
        return online;
    }

    public void removeOffline() {
        synchronized (players) {
            players.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        }
    }
}
