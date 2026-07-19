package com.kammoun.raven.scoreboard;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
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

    Map<UUID, String> loadBoards() {
        Map<UUID, String> boards = new ConcurrentHashMap<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (!config.isConfigurationSection("boards")) {
            return boards;
        }

        for (String raw : config.getConfigurationSection("boards").getKeys(false)) {
            try {
                String board = config.getString("boards." + raw, "");
                if (board != null && !board.isBlank()) {
                    boards.put(UUID.fromString(raw), board);
                }
            } catch (IllegalArgumentException ignored) {
                logger.warning("[Scoreboard] Ignoring invalid board override UUID: " + raw);
            }
        }

        return boards;
    }

    void saveDisabled(Collection<UUID> disabled) {
        saveState(disabled, loadBoards());
    }

    void saveState(Collection<UUID> disabled, Map<UUID, String> boards) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("disabled", disabled.stream()
                .map(UUID::toString)
                .sorted()
                .toList());
        boards.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> config.set("boards." + entry.getKey(), entry.getValue()));

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
