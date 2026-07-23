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
     * any number of %key% placeholders.
     *
     * Usage: messages.format("set.success", "type", "SPAWN", "server", "lobby")
     */
    public String format(String key, Object... kvPairs) {
        String raw = config.getString(key, "&#D90429[Missing message: " + key + "]");
        String msg = ChatFormatter.color(raw);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            String name = String.valueOf(kvPairs[i]);
            String value = String.valueOf(kvPairs[i + 1]);
            msg = msg.replace("%" + name + "%", value).replace("{" + name + "}", value);
        }
        return msg;
    }

    /** Fetches a message with no placeholders. */
    public String get(String key) {
        return format(key);
    }
}
