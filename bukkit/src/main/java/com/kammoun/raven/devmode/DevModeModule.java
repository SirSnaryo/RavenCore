package com.kammoun.raven.devmode;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.devmode.command.DevModeCommand;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

@Getter
public class DevModeModule extends KModule<RavenCore> {

    private DevModeService service;

    public DevModeModule() {
        super("devmode");
    }

    @Override
    public void onModuleEnable() {
        migrateConfig();
        DevModeStateStore stateStore = api.getRedisMessenger() == null
                || api.getRedisMessenger().getConnection() == null
                ? null
                : new DevModeStateStore(api, api.getRedisMessenger().getConnection().getPool());
        if (stateStore == null) {
            getLogger().warning("[DevMode] Redis is unavailable, so Developer Mode will not carry"
                    + " over when players switch servers.");
        }
        service = new DevModeService(api, getConfig(), stateStore);
        registerCommand(new DevModeCommand(service), "devmode");
        onEvent(PlayerQuitEvent.class, event -> service.forget(event.getPlayer()));
        onEvent(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            SchedulerUtil.runLater(() -> {
                if (player.isOnline()) service.restoreOnJoin(player);
            }, 1L);
        });
        getLogger().info("[DevMode] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (service != null) service.clear();
        getLogger().info("[DevMode] Disabled.");
    }

    /**
     * The Developer Mode redesign changed the HUD keys and dropped the map renderer, so installs
     * still carrying the old layout get the bundled config back instead of a half-migrated one.
     */
    private void migrateConfig() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) return;
        if (getConfig().getString("hud.actionbar", "").contains("%ping%")) return;

        try (InputStream in = api.getResource("devmode/config.yml")) {
            if (in == null) return;
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            reloadConfig();
            getLogger().info("[DevMode] Updated config.yml to the Developer Mode layout.");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "[DevMode] Could not update config.yml", e);
        }
    }
}
