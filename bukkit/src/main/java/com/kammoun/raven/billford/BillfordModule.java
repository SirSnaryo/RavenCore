package com.kammoun.raven.billford;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.billford.command.BillfordCommand;
import com.kammoun.raven.billford.command.BillfordPresetCommand;
import com.kammoun.raven.billford.config.BillfordConfig;
import com.kammoun.raven.billford.config.BillfordMessages;
import com.kammoun.raven.billford.db.BillfordRepository;
import com.kammoun.raven.billford.db.BillfordTable;
import com.kammoun.raven.billford.manager.BillfordManager;
import com.kammoun.raven.billford.manager.GuiManager;
import com.kammoun.raven.billford.papi.BillfordPlaceholders;
import com.kammoun.raven.billford.task.PresetRotationTask;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.library.redis.event.BillfordPresetsUpdatedEvent;
import com.kammoun.raven.library.redis.event.BillfordStateUpdatedEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Billford: a network-wide rotating item trade. Presets and the active-preset rotation live in the
 * shared MySQL store, so every server shows the same trade at the same time. Edits and rotations are
 * pushed instantly over Redis ({@link BillfordPresetsUpdatedEvent}/{@link BillfordStateUpdatedEvent}),
 * with a 60s DB reload as a safety net and periodic-poll-free coordination via a compare-and-set.
 */
@Getter
public class BillfordModule extends KModule<RavenCore> {

    private BillfordConfig billfordConfig;
    private BillfordMessages messages;
    private BillfordManager manager;
    private GuiManager guiManager;
    private PresetRotationTask rotationTask;
    private ScheduledTask refreshTask;

    public BillfordModule() {
        super("billford");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().warning("[Billford] MySQL is not connected — Billford needs it to share presets across servers. Module disabled.");
            return;
        }

        billfordConfig = new BillfordConfig(getConfig(), loadGuiConfig());
        messages = new BillfordMessages(loadMessages());

        BillfordRepository repository = new BillfordRepository(api.getMySqlManager(), api);
        // One-time startup DDL — must complete before the first cache load, so it runs inline.
        new BillfordTable(api.getMySqlManager(), billfordConfig.getIntervalMillis()).createTable();

        manager = new BillfordManager(api, repository, billfordConfig, messages);
        guiManager = new GuiManager(billfordConfig, messages, manager, api);

        registerEvent(guiManager);
        registerCommand(new BillfordCommand(guiManager), "billford");
        registerCommand(new BillfordPresetCommand(this), "billfordpreset");

        manager.reloadState();
        manager.reloadPresets();
        manager.seedDefaultIfEmpty();

        api.getNetworkEventBus().subscribe(BillfordPresetsUpdatedEvent.class, event -> {
            if (api.getServerId().equals(event.getSourceServer())) return; // writer already refreshed locally
            manager.reloadPresets();
        });
        api.getNetworkEventBus().subscribe(BillfordStateUpdatedEvent.class, event -> {
            if (api.getServerId().equals(event.getSourceServer())) return;
            manager.applyRemoteState(event.getCurrentPreset(), event.getLastChange(),
                    event.getRotationIntervalMillis(), event.getStateVersion(), event.isAnnounce());
        });

        if (KPlaceholderExpansion.isAvailable()) {
            new BillfordPlaceholders(api, manager).register();
        }

        rotationTask = new PresetRotationTask(manager);
        rotationTask.start();

        // Fallback resync in case a Redis signal was ever missed.
        refreshTask = api.getServer().getAsyncScheduler().runAtFixedRate(api, task -> {
            manager.reloadPresets();
            manager.reloadState();
        }, 60, 60, TimeUnit.SECONDS);

        getLogger().info("[Billford] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (rotationTask != null) rotationTask.stop();
        if (refreshTask != null) refreshTask.cancel();
        if (manager != null) {
            api.getNetworkEventBus().unsubscribe(BillfordPresetsUpdatedEvent.class);
            api.getNetworkEventBus().unsubscribe(BillfordStateUpdatedEvent.class);
        }
        if (guiManager != null) guiManager.closeAll();
        getLogger().info("[Billford] Disabled.");
    }

    @Override
    public void reload() {
        if (manager == null) return; // never enabled (e.g. MySQL was down)
        reloadConfig();
        billfordConfig = new BillfordConfig(getConfig(), loadGuiConfig());
        messages = new BillfordMessages(loadMessages());
        manager.updateConfig(billfordConfig, messages);
        guiManager.updateRefs(billfordConfig, messages);
        manager.reloadPresets();
        manager.reloadState();
    }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(
                new File(getDataFolder(), "messages.yml"));
        try (InputStream bundled = api.getResource("billford/messages.yml")) {
            if (bundled != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
            }
        } catch (Exception error) {
            getLogger().warning("[Billford] Failed to load bundled message defaults: "
                    + error.getMessage());
        }
        return messages;
    }

    private FileConfiguration loadGuiConfig() {
        File guiFile = new File(getDataFolder(), "gui.yml");
        if (!guiFile.isFile() && !migrateLegacyGui(guiFile)) {
            saveDefaultConfig("gui.yml");
        }

        YamlConfiguration gui = YamlConfiguration.loadConfiguration(guiFile);
        try (InputStream bundled = api.getResource("billford/gui.yml")) {
            if (bundled != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8));
                gui.setDefaults(defaults);
            }
        } catch (Exception error) {
            getLogger().warning("[Billford] Failed to load bundled GUI defaults: "
                    + error.getMessage());
        }
        return gui;
    }

    /**
     * Existing servers kept both GUI sections in config.yml. Copy them once
     * into gui.yml before loading the new bundled file so custom layouts survive
     * this split. The old sections are intentionally left untouched.
     */
    private boolean migrateLegacyGui(File guiFile) {
        ConfigurationSection main = getConfig().getConfigurationSection("gui");
        ConfigurationSection editor = getConfig().getConfigurationSection("editor");
        if (main == null && editor == null) return false;

        YamlConfiguration migrated = new YamlConfiguration();
        copySection(main, "gui", migrated);
        copySection(editor, "editor", migrated);
        try {
            migrated.save(guiFile);
            getLogger().info("[Billford] Migrated GUI layout from config.yml to gui.yml.");
            return true;
        } catch (Exception error) {
            getLogger().warning("[Billford] Could not migrate GUI configuration: "
                    + error.getMessage());
            return false;
        }
    }

    private void copySection(ConfigurationSection source, String root, YamlConfiguration target) {
        if (source == null) return;
        source.getValues(true).forEach((path, value) -> {
            if (!(value instanceof ConfigurationSection)) target.set(root + "." + path, value);
        });
    }
}
