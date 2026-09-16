package com.kammoun.raven.staffmode;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.platform.sound.KSound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffModeService {

    private final Set<UUID> sessions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final FileConfiguration config;
    private final KSound toggleSound;
    private final KSound denySound;

    public StaffModeService(FileConfiguration config) {
        this.config = config;
        this.toggleSound = sound("sounds.toggle");
        this.denySound = sound("sounds.deny");
    }

    public boolean isEnabled(Player player) {
        return sessions.contains(player.getUniqueId());
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public boolean toggle(Player player) {
        if (isEnabled(player)) {
            disable(player);
            return false;
        }
        enable(player);
        return true;
    }

    public void enable(Player player) {
        UUID uuid = player.getUniqueId();
        if (!sessions.add(uuid)) return;

        player.sendMessage(ChatFormatter.color(message("messages.enabled", "&#33FF66Staff Mode enabled.")));
        play(toggleSound, player);
    }

    public void disable(Player player) {
        boolean removed = sessions.remove(player.getUniqueId());
        vanished.remove(player.getUniqueId());
        if (!removed) return;

        player.sendMessage(ChatFormatter.color(message("messages.disabled", "&#D90429Staff Mode disabled.")));
        play(toggleSound, player);
    }

    public void deny(Player player) {
        player.sendMessage(ChatFormatter.color(message("messages.enable-first", "&#D90429Enable Staff Mode first.")));
        play(denySound, player);
    }

    public void clear() {
        sessions.clear();
        vanished.clear();
    }

    public void forget(Player player) {
        sessions.remove(player.getUniqueId());
        vanished.remove(player.getUniqueId());
    }

    private String message(String path, String fallback) {
        return config == null ? fallback : config.getString(path, fallback);
    }

    private KSound sound(String path) {
        if (config == null) return null;
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? null : KSound.getSoundFromConfig(section);
    }

    private void play(KSound sound, Player player) {
        if (sound != null) {
            sound.playSound(player);
        }
    }
}
