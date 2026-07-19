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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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

        billfordConfig = new BillfordConfig(getConfig());
        messages = new BillfordMessages(loadMessages());

        BillfordRepository repository = new BillfordRepository(api.getMySqlManager(), api);
        // One-time startup DDL — must complete before the first cache load, so it runs inline.
        new BillfordTable(api.getMySqlManager()).createTable();

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
            manager.applyRemoteState(event.getCurrentPreset(), event.getLastChange(), event.isAnnounce());
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
        billfordConfig = new BillfordConfig(getConfig());
        messages = new BillfordMessages(loadMessages());
        manager.updateConfig(billfordConfig, messages);
        guiManager.updateRefs(billfordConfig, messages);
        manager.reloadPresets();
        manager.reloadState();
    }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
}
