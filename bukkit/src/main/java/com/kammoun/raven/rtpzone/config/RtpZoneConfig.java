package com.kammoun.raven.rtpzone.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.platform.title.KTitle;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Typed view of rtpzone/config.yml. Mirrors the old DonutCore rtpzone config, but the
 * destinations themselves are NOT defined here — the zone reuses the /rtp module's
 * {@code overworld-worlds} server list and its shared location pools, so a zone teleport
 * lands on a real, pre-validated safe spot without this module ever sampling terrain.
 */
@Getter
public final class RtpZoneConfig {

    /** Seconds a player must stand in the zone before the group is teleported. */
    private final int timer;
    /** WorldGuard region ids that act as the zone (matched case-insensitively, any world). */
    private final List<String> regions;
    /** Shown every second during the countdown; carries the {time} placeholder. */
    private final KTitle countdownTitle;
    /** Shown once as the player is whisked away. */
    private final KTitle teleportedTitle;
    /** Sent when no safe destination could be popped from the pool. */
    private final String poolEmptyMessage;

    private RtpZoneConfig(int timer, List<String> regions, KTitle countdownTitle,
                          KTitle teleportedTitle, String poolEmptyMessage) {
        this.timer = timer;
        this.regions = regions;
        this.countdownTitle = countdownTitle;
        this.teleportedTitle = teleportedTitle;
        this.poolEmptyMessage = poolEmptyMessage;
    }

    public static RtpZoneConfig load(FileConfiguration config) {
        int timer = Math.max(1, config.getInt("timer", 30));
        List<String> regions = config.getStringList("regions");

        KTitle countdown = title(config.getConfigurationSection("titles.countdown"),
                "&#FF2B2B&lRTP ZONE", "&#FFFFFFTeleporting in &#FFD447{time}&#FFFFFFs");
        KTitle teleported = title(config.getConfigurationSection("titles.teleported"),
                "&#FF2B2B&lRTP ZONE", "&#33FF66Whoosh!");

        String poolEmpty = ChatFormatter.color(config.getString("messages.pool-empty",
                "&#D90429Couldn't find a safe destination right now — try again shortly."));

        return new RtpZoneConfig(timer, regions, countdown, teleported, poolEmpty);
    }

    private static KTitle title(ConfigurationSection section, String defaultHeader, String defaultFooter) {
        if (section != null) return KTitle.fromConfig(section);
        return new KTitle(defaultHeader, defaultFooter).fadeIn(0).stay(25).fadeOut(5);
    }
}
