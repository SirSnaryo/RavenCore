package com.kammoun.raven.scoreboard;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

class ScoreboardToggleStorage {

    private final File file;
    private final Logger logger;

    ScoreboardToggleStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "state.yml");
        this.logger = logger;
    }

    Set<UUID> loadDisabled() {
        Set<UUID> disabled = ConcurrentHashMap.newKeySet();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String raw : config.getStringList("disabled")) {
            try {
                disabled.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                logger.warning("[Scoreboard] Ignoring invalid disabled player UUID: " + raw);
            }
        }

        return disabled;
    }

    void saveDisabled(Collection<UUID> disabled) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("disabled", disabled.stream()
                .map(UUID::toString)
                .sorted()
                .toList());

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("[Scoreboard] Failed to create state folder: " + parent.getPath());
            return;
        }

        try {
            config.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[Scoreboard] Failed to save toggle state.", e);
        }
    }
}
