package com.kammoun.raven.globalcontrol.config;

import org.bukkit.entity.EntityType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public record GlobalConfig(
        boolean enabled,
        Suppression suppression,
        GameRules gameRules,
        SleepActionBar sleepActionBar,
        DeathMessages deathMessages,
        BossSounds bossSounds
) {

    public static GlobalConfig load(FileConfiguration config, Logger logger) {
        return new GlobalConfig(
                config.getBoolean("enabled", true),
                new Suppression(
                        config.getBoolean("suppression.join-message", true),
                        config.getBoolean("suppression.quit-message", true),
                        config.getBoolean("suppression.advancement-announcements", true),
                        config.getBoolean("suppression.vanilla-death-messages", true)
                ),
                new GameRules(
                        config.getBoolean("game-rules.apply-on-enable", true),
                        config.getBoolean("game-rules.apply-on-world-load", true),
                        config.getBoolean("game-rules.announce-advancements", false),
                        config.getBoolean("game-rules.show-death-messages", false)
                ),
                new SleepActionBar(
                        config.getBoolean("sleep-actionbar.clear", true),
                        positive(config.getInt("sleep-actionbar.duration-ticks", 100), 100),
                        positive(config.getInt("sleep-actionbar.every-ticks", 1), 1)
                ),
                new DeathMessages(
                        config.getBoolean("death-messages.enabled", true),
                        nonNegative(config.getDouble("death-messages.radius-blocks", 500.0D), 500.0D),
                        config.getString("death-messages.player-kill-format",
                                "<shadow:#000000:1><#D90429>\\u2620 <#FFFFFF>%victim% <#7A7A7A>was killed by <#FF5555>%killer%</shadow>"),
                        config.getString("death-messages.generic-format",
                                "<shadow:#000000:1><#D90429>\\u2620 <#FFFFFF>%victim% <#7A7A7A>died.</shadow>")
                ),
                new BossSounds(
                        config.getBoolean("boss-sounds.restrict-global-death-sounds", true),
                        readEntityTypes(config, logger),
                        nonNegative(config.getDouble("boss-sounds.radius-blocks", 128.0D), 128.0D),
                        config.getBoolean("boss-sounds.replay-local-sound", true),
                        positiveFloat((float) config.getDouble("boss-sounds.volume-scale", 1.0D), 1.0F)
                )
        );
    }

    private static Set<EntityType> readEntityTypes(FileConfiguration config, Logger logger) {
        EnumSet<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (String value : config.getStringList("boss-sounds.entities")) {
            try {
                types.add(EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("[Global] Unknown boss-sounds entity type: " + value);
            }
        }
        if (types.isEmpty()) {
            types.add(EntityType.ENDER_DRAGON);
            types.add(EntityType.WITHER);
        }
        return EnumSet.copyOf(types);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double nonNegative(double value, double fallback) {
        return value >= 0.0D ? value : fallback;
    }

    private static float positiveFloat(float value, float fallback) {
        return value > 0.0F ? value : fallback;
    }

    public record Suppression(
            boolean joinMessage,
            boolean quitMessage,
            boolean advancementAnnouncements,
            boolean vanillaDeathMessages
    ) {}

    public record GameRules(
            boolean applyOnEnable,
            boolean applyOnWorldLoad,
            boolean announceAdvancements,
            boolean showDeathMessages
    ) {}

    public record SleepActionBar(
            boolean clear,
            int durationTicks,
            int everyTicks
    ) {}

    public record DeathMessages(
            boolean enabled,
            double radiusBlocks,
            String playerKillFormat,
            String genericFormat
    ) {
        public double radiusSquared() {
            return radiusBlocks * radiusBlocks;
        }
    }

    public record BossSounds(
            boolean restrictGlobalDeathSounds,
            Set<EntityType> entities,
            double radiusBlocks,
            boolean replayLocalSound,
            float volumeScale
    ) {
        public boolean tracks(EntityType type) {
            return entities.contains(type);
        }

        public double radiusSquared() {
            return radiusBlocks * radiusBlocks;
        }
    }
}
