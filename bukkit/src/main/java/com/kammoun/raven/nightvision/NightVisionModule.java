package com.kammoun.raven.nightvision;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.nightvision.command.NightVisionCommand;
import com.kammoun.raven.nightvision.db.NightVisionRepository;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;

public class NightVisionModule extends KModule<RavenCore> {

    private NightVisionService service;

    public NightVisionModule() {
        super("nightvision");
    }

    @Override
    public void onModuleEnable() {
        if (api.getMySqlManager() == null || !api.getMySqlManager().isConnected()) {
            getLogger().warning("[NightVision] MySQL not available, module disabled.");
            return;
        }

        NightVisionRepository repository = new NightVisionRepository(api.getMySqlManager());
        try {
            repository.init();
        } catch (SQLException e) {
            getLogger().warning("[NightVision] Failed to init table: " + e.getMessage());
            return;
        }

        String msgOn  = ChatFormatter.color(getConfig().getString("messages.enabled",  "&#33FF66Night vision &aenabled."));
        String msgOff = ChatFormatter.color(getConfig().getString("messages.disabled", "&#FF2B2BNight vision &cdisabled."));

        service = new NightVisionService(repository, getLogger());
        service.loadAll();

        registerCommand(new NightVisionCommand(service, msgOn, msgOff), "nv");

        onEvent(PlayerJoinEvent.class, e -> service.onJoin(e.getPlayer()));
        onEvent(PlayerQuitEvent.class, e -> service.onQuit(e.getPlayer()));

        getLogger().info("[NightVision] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        getLogger().info("[NightVision] Disabled.");
    }
}
