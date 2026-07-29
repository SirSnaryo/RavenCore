package com.kammoun.raven.chat.config;

import com.kammoun.raven.chat.model.AutoMessage;
import com.kammoun.raven.chat.model.ChatFilterSettings;
import com.kammoun.raven.chat.model.ChatFormat;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record ChatConfig(
        String permission,
        String toggleBypassPermission,
        String serverTag,
        int maxLength,
        long cooldownMillis,
        boolean sameMessageCancel,
        boolean capsEnabled,
        double capsThreshold,
        boolean floodEnabled,
        Pattern floodPattern,
        List<Pattern> blockedPatterns,
        ChatFilterSettings filterSettings,
        boolean linksEnabled,
        boolean linkClickEnabled,
        String linkFormat,
        String mentionSelfFormat,
        String mentionOtherFormat,
        String defaultChatFormat,
        List<ChatFormat> chatFormats,
        boolean autoMessagesEnabled,
        String autoMessageLockKey,
        int autoMessageLockTtlSeconds,
        long autoMessageIntervalTicks,
        List<AutoMessage> autoMessages
) {

    private static final String DEFAULT_FORMAT =
            "%luckperms_prefix%<#FFFFFF>%player% <#7A7A7A>\u00BB <#FFFFFF><message>";
    private static final List<String> DEFAULT_PROFANITY_WORDS = List.of(
            "fuck", "shit", "bitch", "asshole", "dick", "pussy", "cunt",
            "كسمك", "كس", "قحبة", "شرموط"
    );
    private static final List<String> DEFAULT_ALLOWED_DOMAINS = List.of("ravenmc.live");

    public static ChatConfig from(FileConfiguration config, String defaultServerTag) {
        String defaultFormat = normalizeFormat(config.getString(
                "default-chat-format",
                config.getString("format", DEFAULT_FORMAT)
        ));

        return new ChatConfig(
                config.getString("permission", "raven.chat"),
                config.getString("bypass-toggle-permission", ""),
                config.getString("server-tag", defaultServerTag),
                config.getInt("limits.max-length", 256),
                Math.max(0L, config.getLong("limits.cooldown-millis", 1000L)),
                config.getBoolean("rules.same-message-cancel", true),
                config.getBoolean("rules.caps.enabled", true),
                config.getDouble("rules.caps.threshold", 0.75D),
                config.getBoolean("rules.flood.enabled", true),
                compile(config.getString("rules.flood.regex", "(.)\\1{4,}")),
                compileAll(config.getStringList("rules.blocked-patterns")),
                loadFilterSettings(config),
                config.getBoolean("links.enabled", true),
                config.getBoolean("links.clickable", true),
                config.getString("links.format", "<#4CC9F0>%url%"),
                config.getString("mentions.self-format", "<#FFD447>%player%"),
                config.getString("mentions.other-format", "<#4CC9F0>%player%"),
                defaultFormat,
                loadFormats(config, defaultFormat),
                config.getBoolean("auto-messages.enabled", false),
                config.getString("auto-messages.redis-lock-key", "raven:chat:auto:lock"),
                Math.max(2, config.getInt("auto-messages.lock-ttl-seconds", 8)),
                Math.max(20L, config.getLong("auto-messages.interval-ticks", 2400L)),
                loadAutoMessages(config)
        );
    }

    private static ChatFilterSettings loadFilterSettings(FileConfiguration config) {
        return new ChatFilterSettings(
                config.getBoolean("rules.profanity.enabled", true),
                stringListOrDefault(config, "rules.profanity.words", DEFAULT_PROFANITY_WORDS),
                config.getBoolean("rules.advertising.enabled", true),
                config.getBoolean("rules.advertising.block-ips", true),
                config.getBoolean("rules.advertising.block-domains", true),
                config.getBoolean("rules.advertising.block-discord-invites", true),
                stringListOrDefault(config, "rules.advertising.allowed-domains", DEFAULT_ALLOWED_DOMAINS),
                config.getBoolean("rules.lag-test.enabled", true),
                Math.max(2, config.getInt("rules.lag-test.min-tokens", 3)),
                Math.max(2, config.getInt("rules.lag-test.min-repeated-token-length", 4)),
                Math.max(1, config.getInt("rules.lag-test.min-repeated-tokens", 2))
        );
    }

    private static List<String> stringListOrDefault(FileConfiguration config, String path, List<String> fallback) {
        return config.contains(path) ? List.copyOf(config.getStringList(path)) : fallback;
    }

    public String resolvedServerTag(String fallback) {
        return serverTag == null || serverTag.isBlank() ? fallback : serverTag;
    }

    private static List<ChatFormat> loadFormats(FileConfiguration config, String defaultFormat) {
        List<ChatFormat> formats = new ArrayList<>();
        for (Map<?, ?> map : configuredFormatMaps(config)) {
            String format = normalizeFormat(stringValue(map, "format", ""));
            if (!format.isBlank() && !format.equals(defaultFormat)) {
                formats.add(new ChatFormat(
                        stringValue(map, "permission", ""),
                        intValue(map, "priority", 0),
                        format
                ));
            }
        }
        formats.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        return List.copyOf(formats);
    }

    private static List<Map<?, ?>> configuredFormatMaps(FileConfiguration config) {
        List<Map<?, ?>> formats = config.getMapList("chat-formats");
        return formats.isEmpty() ? config.getMapList("formats") : formats;
    }

    private static List<AutoMessage> loadAutoMessages(FileConfiguration config) {
        List<AutoMessage> messages = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList("auto-messages.messages")) {
            String message = stringValue(map, "message", "");
            String actionBar = stringValue(map, "action-bar", "");
            String sound = stringValue(map, "sound", "broadcast");
            if (!message.isBlank() || !actionBar.isBlank()) {
                messages.add(new AutoMessage(message, actionBar, sound));
            }
        }
        return List.copyOf(messages);
    }

    private static String normalizeFormat(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_FORMAT;
        }
        return value.replace("%message%", "<message>");
    }

    private static List<Pattern> compileAll(List<String> values) {
        List<Pattern> patterns = new ArrayList<>();
        for (String value : values) {
            Pattern pattern = compile(value);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        return List.copyOf(patterns);
    }

    private static Pattern compile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(value, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private static String stringValue(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int intValue(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
