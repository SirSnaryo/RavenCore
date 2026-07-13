package com.kammoun.raven.spawner.manager;

import com.kammoun.raven.spawner.model.SpawnerType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Holds all {@link SpawnerType} definitions, each loaded from its own file under the
 * spawners/ directory (e.g. spawners/blaze.yml). The file name (minus extension) is the type ID.
 * Not a singleton — obtained via SpawnerModule.
 */
public class SpawnerRegistry {

    private final Map<String, SpawnerType> types = new HashMap<>();
    private final Logger logger;

    public SpawnerRegistry(Logger logger) {
        this.logger = logger;
    }

    public void load(File spawnersDir, JavaPlugin plugin) {
        types.clear();
        if (!spawnersDir.isDirectory()) {
            logger.warning("[SpawnerRegistry] Spawners directory not found: " + spawnersDir.getPath());
            return;
        }

        File[] files = spawnersDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length());
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                SpawnerType type = SpawnerType.fromConfig(id, cfg, plugin);
                types.put(type.getId(), type);
            } catch (Exception e) {
                logger.warning("[SpawnerRegistry] Failed to load spawner type '" + id + "': " + e.getMessage());
            }
        }
        logger.info("[SpawnerRegistry] Loaded " + types.size() + " spawner type(s).");
    }

    public SpawnerType get(String id) {
        return types.get(id.toUpperCase());
    }

    public boolean has(String id) {
        return types.containsKey(id.toUpperCase());
    }

    public Collection<SpawnerType> all() {
        return types.values();
    }
}
