package com.kammoun.raven.crates.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.crates.config.CratesConfig;
import com.kammoun.raven.crates.db.CrateKeyRepository;
import com.kammoun.raven.crates.db.CrateLocationRepository;
import com.kammoun.raven.crates.model.CrateDefinition;
import com.kammoun.raven.crates.model.CrateLocation;
import com.kammoun.raven.crates.model.CrateReward;
import com.kammoun.raven.crates.player.CratePlayer;
import com.kammoun.raven.crates.player.CratePlayerManager;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.library.util.CommandHelper;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CrateService {
    private final RavenCore plugin;
    private final CratesConfig config;
    private final CrateKeyRepository keyRepository;
    private final CrateLocationRepository locationRepository;
    private final CrateHologramService holograms;
    private final CratePlayerManager players;
    private final Map<String, CrateLocation> placedCrates = new HashMap<>();
    private final ConcurrentMap<UUID, Boolean> opening = new ConcurrentHashMap<>();

    public CrateService(RavenCore plugin, CratesConfig config, CrateKeyRepository keyRepository,
                        CrateLocationRepository locationRepository, CrateHologramService holograms,
                        CratePlayerManager players) {
        this.plugin = plugin;
        this.config = config;
        this.keyRepository = keyRepository;
        this.locationRepository = locationRepository;
        this.holograms = holograms;
        this.players = players;
    }

    public void loadPlacedCrates() {
        placedCrates.clear();
        SchedulerUtil.runAsync(() -> {
            Collection<CrateLocation> loaded = locationRepository.loadAll();
            SchedulerUtil.run(() -> {
                for (CrateLocation location : loaded) {
                    CrateDefinition definition = config.crate(location.crateId());
                    Location bukkitLocation = location.toLocation();
                    if (definition == null || bukkitLocation == null) continue;
                    placedCrates.put(location.key(), location);
                    SchedulerUtil.execute(bukkitLocation, () -> {
                        bukkitLocation.getBlock().setType(definition.blockItem().getType());
                        holograms.createHolograms(bukkitLocation, definition);
                    });
                }
            });
        });
    }

    public CrateDefinition crateAt(Location location) {
        CrateLocation placed = placedCrates.get(key(location));
        return placed == null ? null : config.crate(placed.crateId());
    }

    public boolean isCrate(Location location) {
        return placedCrates.containsKey(key(location));
    }

    public void placeCrate(Player player, Location location, CrateDefinition crate) {
        CrateLocation placed = CrateLocation.of(location, crate.id());
        placedCrates.put(placed.key(), placed);
        SchedulerUtil.runAsync(() -> locationRepository.save(placed));
        holograms.createHolograms(location, crate);
        player.sendMessage(color(config.cratePlacedMessage(), crate));
    }

    public void removeCrate(Player player, Location location) {
        CrateLocation placed = placedCrates.remove(key(location));
        if (placed == null) return;
        CrateDefinition definition = config.crate(placed.crateId());
        SchedulerUtil.runAsync(() -> locationRepository.delete(placed));
        holograms.removeHolograms(location);
        player.sendMessage(color(config.crateRemovedMessage(), definition));
    }

    public void addKeys(UUID uuid, String crateId, int amount) {
        SchedulerUtil.runAsync(() -> {
            keyRepository.addKeys(uuid, crateId, amount);
            players.add(uuid, crateId, amount);
        });
    }

    public void setKeys(UUID uuid, String crateId, int amount) {
        SchedulerUtil.runAsync(() -> {
            keyRepository.setKeys(uuid, crateId, amount);
            players.set(uuid, crateId, amount);
        });
    }

    public int keys(Player player, CrateDefinition crate) {
        return players.keys(player.getUniqueId(), crate.id());
    }

    public CratePlayerManager players() {
        return players;
    }

    public int cleanupHolograms() {
        return holograms.cleanupAllCrateHolograms();
    }

    public void openReward(Player player, CrateDefinition crate, CrateReward reward) {
        UUID uuid = player.getUniqueId();
        CratePlayer cratePlayer = players.player(uuid);
        if (cratePlayer != null && !cratePlayer.hasKey(crate.id())) {
            denyOpen(player);
            return;
        }
        if (opening.putIfAbsent(uuid, true) != null) return;
        SchedulerUtil.runAsync(() -> {
            boolean consumed;
            try {
                consumed = keyRepository.consumeKey(uuid, crate.id());
            } catch (Exception e) {
                plugin.getLogger().warning("[Crates] Failed to consume key for " + player.getName() + ": " + e.getMessage());
                consumed = false;
            }
            if (consumed) players.add(uuid, crate.id(), -1);
            boolean finalConsumed = consumed;
            SchedulerUtil.execute(player, () -> {
                opening.remove(uuid);
                if (!player.isOnline()) return;
                if (!finalConsumed) {
                    denyOpen(player);
                    return;
                }
                giveReward(player, reward);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(ChatFormatter.color(config.rewardMessage()
                        .replace("%reward%", rewardName(reward))));
            });
        });
    }

    private void denyOpen(Player player) {
        player.closeInventory();
        player.sendMessage(ChatFormatter.color(config.noKeyMessage()));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.7f);
    }

    private void giveReward(Player player, CrateReward reward) {
        if (reward.giveItem()) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(reward.rewardItem().clone());
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        CommandHelper.executeCommand(player, reward.commands());
    }

    private String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private String color(String message, CrateDefinition crate) {
        String crateName = crate == null ? "unknown" : crate.displayName();
        return ChatFormatter.color(message.replace("%crate%", crateName));
    }

    private String rewardName(CrateReward reward) {
        if (reward.displayItem().hasItemMeta() && reward.displayItem().getItemMeta().hasDisplayName()) {
            return reward.displayItem().getItemMeta().getDisplayName();
        }
        return reward.displayItem().getType().name();
    }
}
