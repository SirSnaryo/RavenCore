package com.kammoun.raven.mythictools.config;

import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.mythictools.model.MythicToolDefinition;
import com.kammoun.raven.mythictools.model.MythicToolType;
import com.kammoun.raven.mythictools.util.DurationParser;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MythicToolsConfig {

    private final MythicToolsModule module;
    private final Map<MythicToolType, MythicToolDefinition> tools = new EnumMap<>(MythicToolType.class);
    private final Set<Material> mineable = new HashSet<>();
    private final Set<Material> logs = new HashSet<>();
    private final Set<Material> diggable = new HashSet<>();
    private FileConfiguration messages;

    public MythicToolsConfig(MythicToolsModule module) {
        this.module = module;
    }

    public void load() {
        module.saveDefaultConfig("tools.yml");
        module.saveDefaultConfig("blocks.yml");
        module.saveDefaultConfig("messages.yml");
        loadTools(YamlConfiguration.loadConfiguration(file("tools.yml")));
        loadBlocks(YamlConfiguration.loadConfiguration(file("blocks.yml")));
        messages = YamlConfiguration.loadConfiguration(file("messages.yml"));
    }

    private void loadTools(FileConfiguration config) {
        tools.clear();
        ConfigurationSection root = config.getConfigurationSection("tools");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                MythicToolType type = MythicToolType.valueOf(key.toUpperCase());
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null || ItemReader.readItem(section, module.getApi()) == null) continue;
                ItemStack template = ItemReader.readItem(section, module.getApi()).build();
                long duration = DurationParser.parseMillis(section.getString("self-destruct", "5d"));
                ConfigurationSection soundSection = section.getConfigurationSection("sound");
                KSound sound = soundSection != null && soundSection.getBoolean("enabled", true)
                        ? KSound.getSoundFromConfig(soundSection) : null;
                tools.put(type, new MythicToolDefinition(type, template, Math.max(0L, duration),
                        section.getBoolean("unbreakable", true), sound));
            } catch (IllegalArgumentException e) {
                module.getLogger().warning("[MythicTools] Invalid tool type: " + key);
            }
        }
    }

    private void loadBlocks(FileConfiguration config) {
        loadMaterials(config, "blocks.mineable", mineable);
        loadMaterials(config, "blocks.logs", logs);
        loadMaterials(config, "blocks.diggable", diggable);
    }

    private void loadMaterials(FileConfiguration config, String path, Set<Material> target) {
        target.clear();
        for (String name : config.getStringList(path)) {
            try {
                target.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                module.getLogger().warning("[MythicTools] Invalid material in " + path + ": " + name);
            }
        }
    }

    public MythicToolDefinition tool(MythicToolType type) {
        return tools.get(type);
    }

    public Map<MythicToolType, MythicToolDefinition> tools() {
        return Map.copyOf(tools);
    }

    public boolean mineable(Material material) {
        return mineable.contains(material);
    }

    public boolean log(Material material) {
        return logs.contains(material);
    }

    public boolean diggable(Material material) {
        return diggable.contains(material);
    }

    public String message(String path) {
        return messages.getString(path, "");
    }

    public String timerDelayed() {
        return module.getConfig().getString("timer-lore.delayed", "&#FFD447Timer starts on first use");
    }

    public String timerDuration() {
        return module.getConfig().getString("timer-lore.duration", "&#7A7A7ADuration: &#33FF66%time%");
    }

    public String timerExpires() {
        return module.getConfig().getString("timer-lore.expires", "&#7A7A7AExpires in: &#FF2B2B%time%");
    }

    private File file(String name) {
        return new File(module.getDataFolder(), name);
    }
}
