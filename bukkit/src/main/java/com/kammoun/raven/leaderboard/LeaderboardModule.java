package com.kammoun.raven.leaderboard;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.leaderboard.command.LeaderboardCommand;
import com.kammoun.raven.leaderboard.config.LeaderboardConfig;
import com.kammoun.raven.leaderboard.config.LeaderboardMessages;
import com.kammoun.raven.leaderboard.db.LeaderboardRepository;
import com.kammoun.raven.leaderboard.gui.LeaderboardMenu;
import com.kammoun.raven.leaderboard.gui.LeaderboardPage;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.leaderboard.service.LeaderboardService;
import com.kammoun.raven.library.command.UnavailableCommand;
import com.kammoun.raven.library.module.KModule;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class LeaderboardModule extends KModule<RavenCore> {
    private LeaderboardConfig leaderboardConfig;
    private LeaderboardMessages messages;
    private LeaderboardService service;

    public LeaderboardModule() {
        super("leaderboard");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().warning("[Leaderboard] MySQL is unavailable. Module disabled.");
            registerUnavailableCommand("&#D90429Leaderboards are unavailable because MySQL is not connected.");
            return;
        }
        saveDefaultConfig("gui/menu.yml");
        saveDefaultConfig("gui/page.yml");
        restoreBlockLeaderboards();
        migrateSearchMessages();
        restoreGuiSection("gui/page.yml", "Items.Search", "Items.Search-Active");
        leaderboardConfig = new LeaderboardConfig(getConfig());
        messages = new LeaderboardMessages(getConfig());
        try {
            LeaderboardRepository repository = new LeaderboardRepository(api.getMySqlManager());
            repository.init();
            service = new LeaderboardService(repository, leaderboardConfig, getLogger());
            service.start();
        } catch (Exception e) {
            getLogger().warning("[Leaderboard] Failed to initialize: " + e.getMessage());
            registerUnavailableCommand("&#D90429Leaderboards are unavailable right now.");
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
        messages = new LeaderboardMessages(getConfig());
        if (service != null) service.updateConfig(leaderboardConfig);
    }

    public void openMenu(Player player) {
        new LeaderboardMenu(api, this).open(player);
    }

    public void openPage(Player player, LeaderboardType type, int page) {
        openPage(player, type, page, null);
    }

    public void openPage(Player player, LeaderboardType type, int page, String filter) {
        if (service == null || !service.enabled(type)) {
            openMenu(player);
            return;
        }
        new LeaderboardPage(api, this, type, page, filter).open(player);
    }

    // Appended as raw text so the existing config comments and key order survive.
    private void migrateSearchMessages() {
        if (getConfig().isConfigurationSection("messages")) return;
        File file = new File(getDataFolder(), "config.yml");
        if (!file.isFile()) return;
        try {
            String current = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String block = """
                    messages:
                      # %query% is the text the player typed on the search sign.
                      search-no-results: '&#D90429No player on this leaderboard matches &#FFD447%query%&#D90429.'
                      search-cleared: '&#33FF66Search cleared.'
                      search-unavailable: '&#D90429Search is not available on this server version.'
                      # Shown in the search item lore through %filter% while no search is active.
                      search-none: '&#7A7A7ANone'
                    """;
            Files.writeString(file.toPath(),
                    current.endsWith("\n") ? current + "\n" + block : current + "\n\n" + block,
                    StandardCharsets.UTF_8);
            reloadConfig();
            getLogger().info("[Leaderboard] Added search messages to config.yml.");
        } catch (IOException e) {
            getLogger().warning("[Leaderboard] Could not add search messages: " + e.getMessage());
        }
    }

    private void restoreBlockLeaderboards() {
        restoreEnabledTypes();
        restoreGuiSection("gui/menu.yml", "Items.blocks-broken", "Items.blocks-placed");
        restoreGuiSection("gui/page.yml", "Titles.blocks-broken", "Titles.blocks-placed");
    }

    private void registerUnavailableCommand(String message) {
        registerCommand(new UnavailableCommand("leaderboard", "raven.leaderboard", true, message), "leaderboard");
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
