package com.kammoun.raven.tpa.settings;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.tpa.config.TeleportConfig;
import com.kammoun.raven.tpa.config.TeleportMessage;
import com.kammoun.raven.tpa.event.TeleportSettingsChangeEvent;
import com.kammoun.raven.tpa.model.TeleportPreference;
import com.kammoun.raven.tpa.model.TeleportSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class TeleportSettingsService {

    private final RavenCore plugin;
    private final TeleportSettingsRepository repository;
    private final TeleportConfig config;
    private final Logger logger;
    private final Map<UUID, TeleportSettings> cache = new ConcurrentHashMap<>();

    public TeleportSettingsService(RavenCore plugin, TeleportSettingsRepository repository,
                                   TeleportConfig config, Logger logger) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
        this.logger = logger;
    }

    public void init() {
        if (repository == null) {
            logger.warning("[Teleport] MySQL unavailable; TPA settings will only live in memory.");
            return;
        }
        try {
            repository.init();
        } catch (SQLException e) {
            logger.warning("[Teleport] Failed to initialize TPA settings table: " + e.getMessage());
        }
    }

    public void load(Player player) {
        if (repository == null) {
            cache.putIfAbsent(player.getUniqueId(), TeleportSettings.defaults(player.getUniqueId()));
            return;
        }

        SchedulerUtil.runAsync(() -> {
            TeleportSettings settings;
            try {
                settings = repository.load(player.getUniqueId())
                        .orElseGet(() -> TeleportSettings.defaults(player.getUniqueId()));
            } catch (SQLException e) {
                logger.warning("[Teleport] Failed to load TPA settings for " + player.getName() + ": " + e.getMessage());
                settings = TeleportSettings.defaults(player.getUniqueId());
            }

            cache.put(player.getUniqueId(), settings);
            if (settings.autoAccept() && player.isOnline()) {
                SchedulerUtil.execute(player, () -> config.actionBar(player, TeleportMessage.TPA_AUTO_STATUS_ACTIONBAR));
            }
        });
    }

    public void unload(Player player) {
        TeleportSettings settings = cache.remove(player.getUniqueId());
        if (settings != null) {
            save(settings);
        }
    }

    public TeleportSettings get(UUID uuid) {
        return cache.computeIfAbsent(uuid, TeleportSettings::defaults);
    }

    public TeleportSettings toggle(Player player, TeleportPreference preference) {
        return set(player, preference, !enabled(player, preference));
    }

    public TeleportSettings set(Player player, TeleportPreference preference, boolean enabled) {
        TeleportSettings settings = get(player.getUniqueId()).with(preference, enabled);
        cache.put(player.getUniqueId(), settings);
        save(settings);
        Bukkit.getPluginManager().callEvent(new TeleportSettingsChangeEvent(player, preference, settings));
        return settings;
    }

    private boolean enabled(Player player, TeleportPreference preference) {
        TeleportSettings settings = get(player.getUniqueId());
        return switch (preference) {
            case TPA -> settings.acceptsTpa();
            case TPA_HERE -> settings.acceptsTpaHere();
            case TPA_AUTO -> settings.autoAccept();
            case CONFIRM_MENU -> settings.confirmMenu();
        };
    }

    public void saveAll() {
        Collection<TeleportSettings> values = cache.values();
        if (repository == null || values.isEmpty()) {
            return;
        }
        try {
            repository.saveAll(values);
        } catch (SQLException e) {
            logger.warning("[Teleport] Failed to save TPA settings: " + e.getMessage());
        }
    }

    private void save(TeleportSettings settings) {
        if (repository == null) {
            return;
        }
        SchedulerUtil.runAsync(() -> {
            try {
                repository.save(settings);
            } catch (SQLException e) {
                logger.warning("[Teleport] Failed to save TPA settings for " + settings.uuid() + ": " + e.getMessage());
            }
        });
    }
}
