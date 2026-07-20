package com.kammoun.raven.chat.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

public class ChatMessages {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("no-permission", "&#D90429You don't have permission to use chat."),
            Map.entry("too-long", "&#D90429Your message is too long."),
            Map.entry("cooldown", "&#FFD447Slow down before sending another message."),
            Map.entry("same-message", "&#D90429Please do not repeat the same message."),
            Map.entry("caps", "&#D90429Please avoid excessive caps."),
            Map.entry("flood", "&#D90429Please avoid repeated-character spam."),
            Map.entry("blocked", "&#D90429That message cannot be sent."),
            Map.entry("chat-toggle-on", "&#33FF66Public chat enabled."),
            Map.entry("chat-toggle-off", "&#D90429Public chat hidden. You can still send messages."),
            Map.entry("chat-toggle-on-actionbar", "&#33FF66Public chat visible."),
            Map.entry("chat-toggle-off-actionbar", "&#D90429Public chat hidden."),
            Map.entry("broadcast-toggle-on", "&#33FF66Auto broadcasts enabled."),
            Map.entry("broadcast-toggle-off", "&#D90429Auto broadcasts hidden."),
            Map.entry("broadcast-toggle-on-actionbar", "&#33FF66Broadcasts visible."),
            Map.entry("broadcast-toggle-off-actionbar", "&#D90429Broadcasts hidden.")
    );

    private final FileConfiguration config;

    public ChatMessages(FileConfiguration config) {
        this.config = config;
    }

    public String get(String key) {
        String raw = config.getString(key, DEFAULTS.getOrDefault(key, "&#D90429[Missing message: " + key + "]"));
        return ChatFormatter.color(raw);
    }

    public String format(String key, String... placeholders) {
        String raw = config.getString(key, DEFAULTS.getOrDefault(key, "&#D90429[Missing message: " + key + "]"));
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                raw = raw.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return ChatFormatter.color(raw);
    }

    public void send(Player player, String key) {
        player.sendMessage(get(key));
    }

    public void send(Player player, String key, String... placeholders) {
        player.sendMessage(format(key, placeholders));
    }
}
