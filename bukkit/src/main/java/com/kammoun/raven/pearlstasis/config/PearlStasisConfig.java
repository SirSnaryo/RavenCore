package com.kammoun.raven.pearlstasis.config;

import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

@Getter
public final class PearlStasisConfig {

    private final boolean enabled;
    private final boolean debug;
    private final boolean logTransfers;
    private final boolean expirePendingRecords;
    private final int ttlSeconds;
    private final Sound arrivalSound;
    private final float soundPitch;
    private final float soundVolume;

    public PearlStasisConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("pearl-stasis.enabled", true);
        this.debug = config.getBoolean("pearl-stasis.debug", false);
        this.logTransfers = config.getBoolean("pearl-stasis.log-transfers", true);
        this.expirePendingRecords = config.getBoolean("pearl-stasis.expire-pending-records", false);
        this.ttlSeconds = Math.max(0, config.getInt("pearl-stasis.pending-record-ttl-seconds", 300));

        String soundName = config.getString("pearl-stasis.sound.name", "ENTITY_ENDERMAN_TELEPORT");
        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            sound = Sound.ENTITY_ENDERMAN_TELEPORT;
        }
        this.arrivalSound = sound;
        this.soundPitch = (float) config.getDouble("pearl-stasis.sound.pitch", 1.75);
        this.soundVolume = (float) config.getDouble("pearl-stasis.sound.volume", 0.9);
    }

    public long getTtlMillis() {
        if (!expirePendingRecords || ttlSeconds <= 0) {
            return -1L;
        }
        return (long) ttlSeconds * 1000L;
    }

    public void playArrivalSound(Player player) {
        if (arrivalSound != null) {
            player.playSound(player.getLocation(), arrivalSound, soundVolume, soundPitch);
        }
    }
}
