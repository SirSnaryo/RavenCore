package com.kammoun.raven.endportal.config;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.config.KConfigLoader;
import com.kammoun.raven.library.platform.sound.KSound;
import org.bukkit.entity.Player;

public final class EndPortalSounds extends KConfigLoader {
    public EndPortalSounds(RavenCore plugin) {
        super(plugin, "endportal/sounds.yml");
    }

    public void play(Player player, String key) {
        KSound sound = KSound.getSoundFromConfig(config.getConfigurationSection(key));
        if (sound != null) sound.playSound(player);
    }
}
