package com.kammoun.raven.spawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.redis.event.LocationsUpdatedEvent;
import com.kammoun.raven.spawn.cache.LocationCache;
import com.kammoun.raven.spawn.command.AfkCommand;
import com.kammoun.raven.spawn.command.SetAfkCommand;
import com.kammoun.raven.spawn.command.SetSpawnCommand;
import com.kammoun.raven.spawn.command.SpawnCommand;
import com.kammoun.raven.spawn.config.SpawnMessages;
import com.kammoun.raven.spawn.countdown.TeleportCountdown;
import com.kammoun.raven.spawn.db.LocationRepository;
import com.kammoun.raven.spawn.db.LocationsTable;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class SpawnModule extends KModule<RavenCore> {

    private LocationRepository repository;
    private LocationCache cache;
    private SpawnMessages messages;
    private TeleportCountdown countdown;
    private ScheduledTask refreshTask;

    public SpawnModule() {
        super("spawn");
    }

    @Override
    public void onModuleEnable() {
        migrateDefaultCountdownSound();
        repository = new LocationRepository(api.getMySqlManager());

        // Deliberate exception to "JDBC off main thread": this is one-time startup DDL,
        // and must complete before the first cache.reload() below to avoid racing the
        // table's own creation.
        new LocationsTable(api.getMySqlManager()).createTable();

        cache = new LocationCache(repository, api);
        int defaultMaxPlayers = getConfig().getInt("default-max-players", 20);
        messages = new SpawnMessages(loadMessages());

        cache.reload();

        int countdownSeconds = getConfig().getInt("teleport-countdown.seconds", 5);
        countdown = new TeleportCountdown(api, messages, countdownSeconds,
                readSound("teleport-countdown.sounds.tick", Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f),
                readSound("teleport-countdown.sounds.denied", Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f));
        countdown.start();

        registerCommand(new SetSpawnCommand(api, repository, cache, messages, defaultMaxPlayers), "setspawn");
        registerCommand(new SetAfkCommand(api, repository, cache, messages, defaultMaxPlayers), "setafk");
        registerCommand(new SpawnCommand(api, getBungeeHelper(), cache, api.getNetworkPlayerManager(), messages, countdown), "spawn");
        registerCommand(new AfkCommand(api, getBungeeHelper(), cache, api.getNetworkPlayerManager(), messages, countdown), "afk");

        onEvent(PlayerJoinEvent.class, event ->
                LocationTransferHelper.consumeOnJoin(api, api.getRedisMessenger().getConnection().getPool(), event.getPlayer()));

        onEvent(PlayerMoveEvent.class, countdown::onMove);

        api.getNetworkEventBus().subscribe(LocationsUpdatedEvent.class, event -> {
            if (api.getServerId().equals(event.getSourceServer())) return; // already reloaded locally by the writer
            cache.reload();
        });

        refreshTask = api.getServer().getAsyncScheduler()
                .runAtFixedRate(api, task -> cache.reload(), 60, 60, TimeUnit.SECONDS);

        getLogger().info("[Spawn] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (refreshTask != null) refreshTask.cancel();
        if (countdown != null) countdown.stop();
        api.getNetworkEventBus().unsubscribe(LocationsUpdatedEvent.class);
        getLogger().info("[Spawn] Disabled.");
    }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }

    private KSound readSound(String path, Sound fallback, float pitch, float volume) {
        if (!getConfig().isConfigurationSection(path)) {
            return new KSound(fallback, pitch, volume);
        }
        KSound sound = KSound.getSoundFromConfig(getConfig().getConfigurationSection(path));
        return sound != null ? sound : new KSound(fallback, pitch, volume);
    }

    private void migrateDefaultCountdownSound() {
        String base = "teleport-countdown.sounds.tick";
        String sound = getConfig().getString(base + ".sound", "");
        double pitch = getConfig().getDouble(base + ".pitch", 0.0D);
        double volume = getConfig().getDouble(base + ".volume", 0.0D);
        if (!"ENTITY_ENDERMAN_TELEPORT".equalsIgnoreCase(sound)
                || Math.abs(pitch - 1.9D) > 0.001D
                || Math.abs(volume - 0.65D) > 0.001D) {
            return;
        }
        getConfig().set(base + ".sound", "BLOCK_NOTE_BLOCK_BASS");
        getConfig().set(base + ".pitch", 0.5D);
        getConfig().set(base + ".volume", 1.0D);
        saveConfig();
    }
}
