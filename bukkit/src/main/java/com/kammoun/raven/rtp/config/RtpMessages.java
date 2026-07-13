package com.kammoun.raven.rtp.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.configuration.file.FileConfiguration;

public class RtpMessages {

    private final FileConfiguration config;

    public RtpMessages(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Fetches a message by dot-path, translates &-color codes, and replaces
     * any number of {key} placeholders.
     *
     * Usage: messages.format("cooldown", "seconds", "3")
     */
    public String format(String key, Object... kvPairs) {
        String raw = config.getString(key, "&c[Missing message: " + key + "]");
        String msg = ChatFormatter.color(raw);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            msg = msg.replace("{" + kvPairs[i] + "}", String.valueOf(kvPairs[i + 1]));
        }
        return msg;
    }

    /** Fetches a message with no placeholders. */
    public String get(String key) {
        return format(key);
    }
}
