package com.kammoun.raven.mythictools.config;

import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.mythictools.model.MultiToolRole;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MythicToolsConfig {

    // Used only when blocks.yml has no multi-tool section at all, e.g. a failed migration.
    private static final List<Material> DEFAULT_SWORD_BLOCKS =
            List.of(Material.COBWEB, Material.BAMBOO, Material.BAMBOO_SAPLING);

    private final MythicToolsModule module;
    private final Map<MythicToolType, MythicToolDefinition> tools = new EnumMap<>(MythicToolType.class);
    private final Map<MultiToolRole, Material> multiToolMaterials = new EnumMap<>(MultiToolRole.class);
    private final Map<Material, MultiToolRole> multiToolOverrides = new HashMap<>();
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
        MythicToolsMigrations.run(module, file("tools.yml"), file("blocks.yml"));
        loadTools(YamlConfiguration.loadConfiguration(file("tools.yml")));
        loadBlocks(YamlConfiguration.loadConfiguration(file("blocks.yml")));
        messages = YamlConfiguration.loadConfiguration(file("messages.yml"));
    }

    private void loadTools(FileConfiguration config) {
        tools.clear();
        ConfigurationSection root = config.getConfigurationSection("tools");
        if (root == null) {
            loadMultiToolMaterials(null);
            return;
        }
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
        loadMultiToolMaterials(root.getConfigurationSection("multi_tool"));
    }

    private void loadMultiToolMaterials(ConfigurationSection section) {
        multiToolMaterials.clear();
        ConfigurationSection materials = section == null ? null : section.getConfigurationSection("switch");
        for (MultiToolRole role : MultiToolRole.values()) {
            String path = "tools.multi_tool.switch." + role.key();
            Material material = materials == null ? null : material(materials.getString(role.key()), path);
            multiToolMaterials.put(role, material == null ? role.fallbackMaterial() : material);
        }
    }

    private void loadBlocks(FileConfiguration config) {
        loadMaterials(config, "blocks.mineable", mineable);
        loadMaterials(config, "blocks.logs", logs);
        loadMaterials(config, "blocks.diggable", diggable);
        loadMultiToolOverrides(config.getConfigurationSection("multi-tool"));
    }

    private void loadMultiToolOverrides(ConfigurationSection section) {
        multiToolOverrides.clear();
        if (section == null) {
            DEFAULT_SWORD_BLOCKS.forEach(material -> multiToolOverrides.put(material, MultiToolRole.SWORD));
            return;
        }
        for (MultiToolRole role : MultiToolRole.values()) {
            for (String name : section.getStringList(role.key())) {
                Material material = material(name, "multi-tool." + role.key());
                if (material != null) multiToolOverrides.put(material, role);
            }
        }
    }

    private void loadMaterials(FileConfiguration config, String path, Set<Material> target) {
        target.clear();
        for (String name : config.getStringList(path)) {
            Material material = material(name, path);
            if (material != null) target.add(material);
        }
    }

    private Material material(String name, String path) {
        if (name == null || name.isBlank()) return null;
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            module.getLogger().warning("[MythicTools] Invalid material in " + path + ": " + name);
            return null;
        }
    }

    public MythicToolDefinition tool(MythicToolType type) {
        return tools.get(type);
    }

    public Map<MythicToolType, MythicToolDefinition> tools() {
        return Map.copyOf(tools);
    }

    public MultiToolRole multiToolRole(Material block) {
        MultiToolRole override = multiToolOverrides.get(block);
        if (override != null) return override;
        for (MultiToolRole role : MultiToolRole.values()) {
            if (role.matches(block)) return role;
        }
        return MultiToolRole.PICKAXE;
    }

    public Material multiToolMaterial(MultiToolRole role) {
        return multiToolMaterials.getOrDefault(role, role.fallbackMaterial());
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
