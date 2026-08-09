package com.kammoun.raven.stats;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.command.UnavailableCommand;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.library.redis.event.BalanceUpdateEvent;
import com.kammoun.raven.stats.command.StatsCommand;
import com.kammoun.raven.stats.command.StatsManagerCommand;
import com.kammoun.raven.stats.config.StatsConfig;
import com.kammoun.raven.stats.db.StatsRepository;
import com.kammoun.raven.stats.db.StatsTable;
import com.kammoun.raven.stats.gui.StatsMenu;
import com.kammoun.raven.stats.listener.StatsListener;
import com.kammoun.raven.stats.model.StatsType;
import com.kammoun.raven.stats.papi.StatsPlaceholders;
import com.kammoun.raven.stats.ravs.RavsBalanceProvider;
import com.kammoun.raven.stats.ravs.RavsBalanceResolver;
import com.kammoun.raven.stats.service.StatsService;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Getter
public final class StatsModule extends KModule<RavenCore> {
    private StatsConfig statsConfig;
    private StatsService service;
    private RavsBalanceResolver ravs;

    public StatsModule() {
        super("stats");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().warning("[Stats] MySQL is unavailable. Module disabled.");
            registerUnavailableCommands("&#D90429Stats are unavailable because MySQL is not connected.");
            return;
        }
        saveDefaultConfig("gui/stats-menu.yml");
        removeRemovedStatsConfig();
        restoreBlockStatsConfig();
        restoreBlockStatsMenuItems();
        removeRemovedStatsMenuItems();
        try {
            statsConfig = new StatsConfig(getConfig());
            new StatsTable(api.getMySqlManager()).createTable();
            service = new StatsService(api, new StatsRepository(api.getMySqlManager()), statsConfig);
            ravs = new RavsBalanceResolver(loadRavsProvider());
            service.start();
            registerEvent(new StatsListener(service));
            registerCommand(new StatsCommand(service, ravs, api.getNetworkPlayerManager(), () -> new StatsMenu(api)), "stats");
            registerCommand(new StatsManagerCommand(service, this::reload), "statsmanager");
        } catch (Exception e) {
            if (service != null) {
                service.shutdown();
                service = null;
            }
            getLogger().warning("[Stats] Failed to initialize: " + e.getMessage());
            registerUnavailableCommands("&#D90429Stats are unavailable right now.");
            return;
        }

        api.getNetworkEventBus().subscribe(BalanceUpdateEvent.class, event -> {
            try {
                UUID uuid = UUID.fromString(event.getUuid());
                if (api.getServer().getPlayer(uuid) == null || event.getDelta() == 0D) return;
                service.increment(uuid, event.getDelta() > 0 ? StatsType.MONEY_MADE : StatsType.MONEY_SPENT,
                        Math.abs(event.getDelta()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        if (KPlaceholderExpansion.isAvailable()) {
            new StatsPlaceholders(api, service, ravs).register();
        }
        getLogger().info("[Stats] Enabled with shared MySQL persistence.");
    }

    @Override
    public void onModuleDisable() {
        if (service != null) service.shutdown();
        if (ravs != null) ravs.clear();
        getLogger().info("[Stats] Disabled.");
    }

    @Override
    public void reload() {
        if (service == null) return;
        reloadConfig();
        statsConfig = new StatsConfig(getConfig());
        service.updateConfig(statsConfig);
    }

    private RavsBalanceProvider loadRavsProvider() {
        if (!api.getServer().getPluginManager().isPluginEnabled("Ravs")) {
            getLogger().warning("[Stats] Ravs is unavailable; Ravs balances will display as 0.");
            return RavsBalanceProvider.unavailable();
        }
        try {
            return (RavsBalanceProvider) Class.forName(
                    "com.kammoun.raven.stats.ravs.RavsApiBalanceProvider").getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("[Stats] Ravs API could not be loaded; balances will display as 0: "
                    + e.getMessage());
            return RavsBalanceProvider.unavailable();
        }
    }

    private void registerUnavailableCommands(String message) {
        registerCommand(new UnavailableCommand("stats", "raven.stats", true, message), "stats");
        registerCommand(new UnavailableCommand("statsmanager", "raven.stats.admin", false, message), "statsmanager");
    }

    private void removeRemovedStatsConfig() {
        String[] removed = {
                "tracking.items-crafted",
                "tracking.jumps",
                "tracking.chat-messages"
        };
        boolean changed = false;
        for (String path : removed) {
            if (!getConfig().contains(path)) continue;
            getConfig().set(path, null);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("[Stats] Removed obsolete tracking options.");
        }
    }

    private void restoreBlockStatsConfig() {
        boolean changed = false;
        if (!getConfig().contains("tracking.blocks-broken")) {
            getConfig().set("tracking.blocks-broken", true);
            changed = true;
        }
        if (!getConfig().contains("tracking.blocks-placed")) {
            getConfig().set("tracking.blocks-placed", true);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("[Stats] Restored blocks broken/placed tracking options.");
        }
    }

    private void removeRemovedStatsMenuItems() {
        File file = new File(getDataFolder(), "gui/stats-menu.yml");
        if (!file.isFile()) return;
        YamlConfiguration menu = YamlConfiguration.loadConfiguration(file);
        String[] removed = {
                "Items.crafted",
                "Items.jumps",
                "Items.chat"
        };
        boolean changed = false;
        for (String path : removed) {
            if (!menu.contains(path)) continue;
            menu.set(path, null);
            changed = true;
        }
        if (!changed) return;
        try {
            menu.save(file);
            getLogger().info("[Stats] Removed obsolete stats menu items.");
        } catch (IOException e) {
            getLogger().warning("[Stats] Could not remove obsolete stats menu items: " + e.getMessage());
        }
    }

    private void restoreBlockStatsMenuItems() {
        File file = new File(getDataFolder(), "gui/stats-menu.yml");
        if (!file.isFile()) return;
        YamlConfiguration menu = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults;
        try (java.io.InputStream stream = api.getResource("stats/gui/stats-menu.yml")) {
            if (stream == null) return;
            defaults = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            getLogger().warning("[Stats] Could not read bundled stats-menu.yml: " + e.getMessage());
            return;
        }

        boolean changed = false;
        for (String key : new String[]{"blocks-placed", "blocks-broken"}) {
            String path = "Items." + key;
            if (menu.contains(path) || !defaults.contains(path)) continue;
            copyConfigurationSection(defaults, menu, path);
            changed = true;
        }
        if (!changed) return;
        try {
            menu.save(file);
            getLogger().info("[Stats] Restored blocks broken/placed stats menu items.");
        } catch (IOException e) {
            getLogger().warning("[Stats] Could not restore stats menu items: " + e.getMessage());
        }
    }

    private void copyConfigurationSection(YamlConfiguration source, YamlConfiguration target, String path) {
        org.bukkit.configuration.ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) continue;
            target.set(path + "." + key, section.get(key));
        }
    }
}
