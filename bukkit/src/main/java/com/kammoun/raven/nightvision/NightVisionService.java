package com.kammoun.raven.nightvision;

import com.kammoun.raven.nightvision.db.NightVisionRepository;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class NightVisionService {

    private static final PotionEffect NV_EFFECT =
            new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false);

    private final NightVisionRepository repository;
    private final Logger logger;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public NightVisionService(NightVisionRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    public void loadAll() {
        SchedulerUtil.runAsync(() -> {
            try {
                enabled.addAll(repository.loadEnabled());
            } catch (SQLException e) {
                logger.warning("[NightVision] Failed to load data: " + e.getMessage());
            }
        });
    }

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        boolean nowEnabled = !enabled.contains(uuid);
        if (nowEnabled) {
            enabled.add(uuid);
            apply(player);
        } else {
            enabled.remove(uuid);
            remove(player);
        }
        SchedulerUtil.runAsync(() -> {
            try {
                repository.save(uuid, nowEnabled);
            } catch (SQLException e) {
                logger.warning("[NightVision] Failed to save for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void onJoin(Player player) {
        if (enabled.contains(player.getUniqueId())){
            SchedulerUtil.runLater(()->apply(player),10L);
        }
    }

    public void onQuit(Player player) {
        remove(player);
    }

    private void apply(Player player) {
        player.addPotionEffect(NV_EFFECT);
    }

    private void remove(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }
}
