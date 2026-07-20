package com.kammoun.raven.spawn.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.configuration.file.FileConfiguration;

public class SpawnMessages {

    private final FileConfiguration config;

    public SpawnMessages(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Fetches a message by dot-path, translates &-color codes, and replaces
     * any number of {key} placeholders.
     *
     * Usage: messages.format("set.success", "type", "SPAWN", "server", "lobby")
     */
    public String format(String key, Object... kvPairs) {
        String raw = config.getString(key, "&#D90429[Missing message: " + key + "]");
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
