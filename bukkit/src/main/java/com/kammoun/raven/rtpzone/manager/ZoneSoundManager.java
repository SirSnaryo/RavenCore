package com.kammoun.raven.rtpzone.manager;

import com.kammoun.raven.library.platform.sound.KSound;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Plays the zone's enter/countdown/exit/teleport cues, all sourced from config. */
public class ZoneSoundManager {

    private final KSound enter;
    private final KSound countdown;
    private final KSound exit;
    private final KSound teleported;

    public ZoneSoundManager(FileConfiguration config) {
        this.enter      = read(config, "sounds.enter",      Sound.BLOCK_BEACON_POWER_SELECT);
        this.countdown  = read(config, "sounds.countdown",  Sound.BLOCK_BEACON_AMBIENT);
        this.exit       = read(config, "sounds.exit",       Sound.BLOCK_BEACON_DEACTIVATE);
        this.teleported = read(config, "sounds.teleported", Sound.BLOCK_END_GATEWAY_SPAWN);
    }

    private KSound read(FileConfiguration config, String path, Sound fallback) {
        KSound sound = KSound.getSoundFromConfig(config.getConfigurationSection(path));
        return sound != null ? sound : new KSound(fallback, 0.5f, 1f);
    }

    public void playEnter(Player p)      { enter.playSound(p); }
    public void playCountdown(Player p)  { countdown.playSound(p); }
    public void playExit(Player p)       { exit.playSound(p); }
    public void playTeleported(Player p) { teleported.playSound(p); }
}
