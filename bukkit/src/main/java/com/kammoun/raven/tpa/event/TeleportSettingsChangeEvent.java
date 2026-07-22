package com.kammoun.raven.tpa.event;

import com.kammoun.raven.tpa.model.TeleportPreference;
import com.kammoun.raven.tpa.model.TeleportSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class TeleportSettingsChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TeleportPreference preference;
    private final TeleportSettings settings;

    public TeleportSettingsChangeEvent(Player player, TeleportPreference preference, TeleportSettings settings) {
        this.player = player;
        this.preference = preference;
        this.settings = settings;
    }

    public Player getPlayer() {
        return player;
    }

    public TeleportPreference getPreference() {
        return preference;
    }

    public TeleportSettings getSettings() {
        return settings;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
