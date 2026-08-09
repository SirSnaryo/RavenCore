package com.kammoun.raven.crates.config;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.crates.model.CrateDefinition;
import com.kammoun.raven.crates.model.CrateDefinition.HologramConfig;
import com.kammoun.raven.crates.model.CrateReward;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CratesConfig {
    private final RavenCore plugin;
    private final File moduleFolder;
    private final Map<String, CrateDefinition> crates = new LinkedHashMap<>();

    private boolean enabled;
    private boolean openingEnabled;
    private int timerIntervalSeconds;
    private int timerCheckSeconds;
    private String timerCrateId;
    private int timerAmount;
    private String noKeyMessage;
    private String receivedKeyMessage;
    private String rewardMessage;
    private String cratePlacedMessage;
    private String crateRemovedMessage;
    private String openingDisabledMessage;
    private String invalidCrateMessage;

    public CratesConfig(RavenCore plugin) {
        this.plugin = plugin;
        this.moduleFolder = new File(plugin.getDataFolder(), "crates");
    }

    public void load() {
        ensureDefaults();
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(new File(moduleFolder, "config.yml"));
        enabled = settings.getBoolean("enabled", true);
        openingEnabled = settings.getBoolean("opening-enabled", plugin.getServerRole().name().equalsIgnoreCase("SPAWN"));
        timerIntervalSeconds = Math.max(60, settings.getInt("automatic-key-all.time", 3600));
        timerCheckSeconds = Math.max(5, settings.getInt("automatic-key-all.check-seconds", 5));
        ConfigurationSection autoKeys = settings.getConfigurationSection("automatic-key-all.keys");
        if (autoKeys != null && !autoKeys.getKeys(false).isEmpty()) {
            timerCrateId = autoKeys.getKeys(false).iterator().next();
            timerAmount = Math.max(1, autoKeys.getInt(timerCrateId, 1));
        } else {
            timerCrateId = settings.getString("auto-key.crate-id", "common_crate");
            timerAmount = Math.max(1, settings.getInt("auto-key.amount", 1));
        }
        noKeyMessage = settings.getString("messages.no-key", "&#FF4D4DYou do not have a key for this crate.");
        receivedKeyMessage = settings.getString("messages.received-key", "&#33FF66You received &#FFFFFF%amount%x %crate% &#33FF66key.");
        rewardMessage = settings.getString("messages.reward", "&#33FF66You received &#FFFFFF%reward%&#33FF66.");
        cratePlacedMessage = settings.getString("messages.crate-placed", "&#33FF66Placed crate &#FFFFFF%crate%&#33FF66.");
        crateRemovedMessage = settings.getString("messages.crate-removed", "&#FFB84DRemoved crate &#FFFFFF%crate%&#FFB84D.");
        openingDisabledMessage = settings.getString("messages.opening-disabled", "&#FF4D4DCrates can only be opened at spawn.");
        invalidCrateMessage = settings.getString("messages.invalid-crate", "&#FF4D4DThat crate is not configured.");

        crates.clear();
        File cratesFolder = new File(moduleFolder, "crates");
        File[] files = cratesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            CrateDefinition definition = loadCrate(file);
            if (definition != null) crates.put(definition.id().toLowerCase(), definition);
        }
    }

    private CrateDefinition loadCrate(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = config.getString("id");
        if (id == null) {
            String raw = config.getString("icon.nbt.crate_id", "");
            id = raw.contains(":") ? raw.split(":", 2)[1] : file.getName().replace(".yml", "");
        }
        ItemStack block = readItem(config.getConfigurationSection("block"), Material.ENDER_CHEST, "&d" + id);
        if (config.isConfigurationSection("icon")) {
            block = readItem(config.getConfigurationSection("icon"), Material.CHEST, "&d" + id);
        }
        ItemStack filler = readOptionalItem(config.getConfigurationSection("inventory.filler"));
        if (filler == null) filler = readOptionalItem(config.getConfigurationSection("inventory.background-icon"));
        List<CrateReward> rewards = new ArrayList<>();
        ConfigurationSection rewardSection = config.getConfigurationSection("rewards");
        if (rewardSection != null) {
            for (String key : rewardSection.getKeys(false)) {
                ConfigurationSection section = rewardSection.getConfigurationSection(key);
                if (section == null) continue;
                ConfigurationSection itemSection = section.getConfigurationSection("item");
                ItemStack display = readItem(itemSection, Material.STONE, key);
                ItemStack item = readOptionalItem(itemSection);
                if (item == null) item = display.clone();
                List<String> commands = section.getStringList("commands");
                if (commands.isEmpty() && itemSection != null) commands = itemSection.getStringList("commands");
                boolean giveItem = section.getBoolean("give-item", section.getBoolean("give-icon", true));
                rewards.add(new CrateReward(itemSection == null ? section.getInt("slot", -1) : itemSection.getInt("slot", -1),
                        giveItem, display, item, commands));
            }
        }
        if (rewards.isEmpty()) {
            plugin.getLogger().warning("[Crates] Crate " + id + " has no rewards; skipping.");
            return null;
        }
        int size = config.getInt("inventory.size", 27);
        if (size < 9 || size > 54 || size % 9 != 0) size = 27;
        List<HologramConfig> holograms = new ArrayList<>();
        ConfigurationSection hologramSection = config.getConfigurationSection("hologram");
        if (hologramSection != null) {
            for (String key : hologramSection.getKeys(false)) {
                ConfigurationSection section = hologramSection.getConfigurationSection(key);
                if (section == null) continue;
                holograms.add(new HologramConfig(section.getDouble("height", 1.5),
                        section.getStringList("lines").stream().map(ChatFormatter::color).toList()));
            }
        }
        return new CrateDefinition(id, config.getString("display-name", file.getName().replace(".yml", "")), block,
                ChatFormatter.color(config.getString("inventory.name", config.getString("inventory.title", "&8" + id))),
                size, filler, holograms, rewards);
    }

    private ItemStack readOptionalItem(ConfigurationSection section) {
        if (section == null) return null;
        ItemBuilder builder = ItemReader.readItem(section, plugin);
        return builder == null ? null : builder.build();
    }

    private ItemStack readItem(ConfigurationSection section, Material fallback, String name) {
        ItemStack item = readOptionalItem(section);
        return item == null ? ItemBuilder.of(fallback).name(name).build() : item;
    }

    private void ensureDefaults() {
        File definitions = new File(moduleFolder, "crates");
        if (!definitions.exists()) definitions.mkdirs();
        saveResourceIfMissing("crates/config.yml");
        saveResourceIfMissing("crates/lang.yml");
        saveResourceIfMissing("crates/Gui/confirm-gui.yml");
        saveResourceIfMissing("crates/crates/prime-crate.yml");
        saveResourceIfMissing("crates/crates/common-crate.yml");
        saveResourceIfMissing("crates/crates/gold-crate.yml");
        saveResourceIfMissing("crates/crates/crimson-crate.yml");
        saveResourceIfMissing("crates/crates/amethyst-crate.yml");
    }

    private void saveResourceIfMissing(String path) {
        if (new File(plugin.getDataFolder(), path).exists()) return;
        plugin.saveResource(path, false);
    }

    public boolean enabled() { return enabled; }
    public boolean openingEnabled() { return openingEnabled; }
    public int timerIntervalSeconds() { return timerIntervalSeconds; }
    public int timerCheckSeconds() { return timerCheckSeconds; }
    public String timerCrateId() { return timerCrateId; }
    public int timerAmount() { return timerAmount; }
    public String noKeyMessage() { return noKeyMessage; }
    public String receivedKeyMessage() { return receivedKeyMessage; }
    public String rewardMessage() { return rewardMessage; }
    public String cratePlacedMessage() { return cratePlacedMessage; }
    public String crateRemovedMessage() { return crateRemovedMessage; }
    public String openingDisabledMessage() { return openingDisabledMessage; }
    public String invalidCrateMessage() { return invalidCrateMessage; }
    public Map<String, CrateDefinition> crates() { return Collections.unmodifiableMap(crates); }
    public CrateDefinition crate(String id) { return id == null ? null : crates.get(id.toLowerCase()); }
    public File crateFile(String id) {
        if (id == null) return null;
        File folder = new File(moduleFolder, "crates");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return null;
        for (File file : files) {
            CrateDefinition definition = loadCrate(file);
            if (definition != null && definition.id().equalsIgnoreCase(id)) return file;
        }
        return null;
    }
}
