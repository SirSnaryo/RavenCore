package com.kammoun.raven.utilities.listener;

import com.kammoun.raven.utilities.config.UtilitiesConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Supplier;

public final class JoinMessageListener implements Listener {
    private final Supplier<UtilitiesConfig> settings;

    public JoinMessageListener(Supplier<UtilitiesConfig> settings) {
        this.settings = settings;
    }

    // HIGHEST so we still win when another plugin sets its own join/quit broadcast.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        UtilitiesConfig config = settings.get();
        if (config == null || !config.hideJoin()) return;
        if (config.keepFirstJoin() && !event.getPlayer().hasPlayedBefore()) return;
        event.setJoinMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        UtilitiesConfig config = settings.get();
        if (config == null || !config.hideQuit()) return;
        event.setQuitMessage(null);
    }
}
