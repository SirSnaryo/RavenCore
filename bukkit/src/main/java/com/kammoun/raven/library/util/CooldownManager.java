package com.kammoun.raven.library.util;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    /**
     * Returns true if the player is still on cooldown for the given key.
     */
    public boolean isOnCooldown(Player player, String key) {
        return getRemainingMillis(player, key) > 0;
    }

    /**
     * Sets a cooldown for the player in seconds.
     */
    public void setCooldown(Player player, String key, int seconds) {
        cooldowns
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(key, System.currentTimeMillis() + (seconds * 1000L));
    }

    /**
     * Returns remaining cooldown time in full seconds (0 if not on cooldown).
     */
    public long getRemainingSeconds(Player player, String key) {
        long remaining = getRemainingMillis(player, key);
        return remaining > 0 ? (remaining / 1000) + 1 : 0;
    }

    /**
     * Returns remaining cooldown time in milliseconds (0 if not on cooldown).
     */
    public long getRemainingMillis(Player player, String key) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;
        Long expiry = playerCooldowns.get(key);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    /**
     * Clears a specific cooldown for the player.
     */
    public void clearCooldown(Player player, String key) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            playerCooldowns.remove(key);
        }
    }

    /**
     * Clears all cooldowns for the player.
     */
    public void clearAll(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}
