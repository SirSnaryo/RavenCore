package com.kammoun.raven.leaderboard;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.leaderboard.command.LeaderboardCommand;
import com.kammoun.raven.leaderboard.config.LeaderboardConfig;
import com.kammoun.raven.leaderboard.db.LeaderboardRepository;
import com.kammoun.raven.leaderboard.gui.LeaderboardMenu;
import com.kammoun.raven.leaderboard.gui.LeaderboardPage;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.leaderboard.service.LeaderboardService;
import com.kammoun.raven.library.module.KModule;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class LeaderboardModule extends KModule<RavenCore> {
    private LeaderboardConfig leaderboardConfig;
    private LeaderboardService service;

    public LeaderboardModule() {
        super("leaderboard");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().warning("[Leaderboard] MySQL is unavailable. Module disabled.");
            return;
        }
        saveDefaultConfig("gui/menu.yml");
        saveDefaultConfig("gui/page.yml");
        restoreBlockLeaderboards();
        leaderboardConfig = new LeaderboardConfig(getConfig());
        try {
            LeaderboardRepository repository = new LeaderboardRepository(api.getMySqlManager());
            repository.init();
            service = new LeaderboardService(repository, leaderboardConfig, getLogger());
            service.start();
        } catch (Exception e) {
            getLogger().warning("[Leaderboard] Failed to initialize: " + e.getMessage());
            return;
        }
        registerCommand(new LeaderboardCommand(this), "leaderboard");
        getLogger().info("[Leaderboard] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (service != null) service.shutdown();
        getLogger().info("[Leaderboard] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
        leaderboardConfig = new LeaderboardConfig(getConfig());
        if (service != null) service.updateConfig(leaderboardConfig);
    }

    public void openMenu(Player player) {
        new LeaderboardMenu(api, this).open(player);
    }

    public void openPage(Player player, LeaderboardType type, int page) {
        if (service == null || !service.enabled(type)) {
            openMenu(player);
            return;
        }
        new LeaderboardPage(api, this, type, page).open(player);
    }

    private void restoreBlockLeaderboards() {
        restoreEnabledTypes();
        restoreGuiSection("gui/menu.yml", "Items.blocks-broken", "Items.blocks-placed");
        restoreGuiSection("gui/page.yml", "Titles.blocks-broken", "Titles.blocks-placed");
    }

    private void restoreEnabledTypes() {
        List<String> enabled = new ArrayList<>(getConfig().getStringList("enabled-types"));
        boolean changed = false;
        for (String type : List.of("blocks_broken", "blocks_placed")) {
            if (enabled.stream().anyMatch(existing -> existing.equalsIgnoreCase(type))) continue;
            enabled.add(type);
            changed = true;
        }
        if (!changed) return;
        getConfig().set("enabled-types", enabled);
        saveConfig();
        getLogger().info("[Leaderboard] Restored blocks broken/placed enabled types.");
    }

    private void restoreGuiSection(String resourcePath, String... paths) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.isFile()) return;
        YamlConfiguration menu = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults;
        try (java.io.InputStream stream = api.getResource("leaderboard/" + resourcePath)) {
            if (stream == null) return;
            defaults = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            getLogger().warning("[Leaderboard] Could not read bundled " + resourcePath + ": " + e.getMessage());
            return;
        }

        boolean changed = false;
        for (String path : paths) {
            if (menu.contains(path) || !defaults.contains(path)) continue;
            copyPath(defaults, menu, path);
            changed = true;
        }
        if (!changed) return;
        try {
            menu.save(file);
            getLogger().info("[Leaderboard] Restored blocks broken/placed entries in " + resourcePath + ".");
        } catch (IOException e) {
            getLogger().warning("[Leaderboard] Could not save " + resourcePath + ": " + e.getMessage());
        }
    }

    private void copyPath(YamlConfiguration source, YamlConfiguration target, String path) {
        org.bukkit.configuration.ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) {
            target.set(path, source.get(path));
            return;
        }
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) continue;
            target.set(path + "." + key, section.get(key));
        }
    }
}
