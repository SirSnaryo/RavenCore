package com.kammoun.raven.rtp;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.config.KConfigLoader;
import com.kammoun.raven.library.platform.sound.KSound;
import org.bukkit.entity.Player;

public final class RtpSounds extends KConfigLoader {
    public RtpSounds(RavenCore plugin) {
        super(plugin, "rtp/sounds.yml");
    }

    public void play(Player player, String key) {
        KSound sound = KSound.getSoundFromConfig(config.getConfigurationSection(key));
        if (sound != null) sound.playSound(player);
    }
}
