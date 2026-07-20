package com.kammoun.raven.clearinventory.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.configuration.file.FileConfiguration;

public class ClearInventoryMessages {

    private final FileConfiguration config;

    public ClearInventoryMessages(FileConfiguration config) {
        this.config = config;
    }

    public String format(String key, Object... kvPairs) {
        String raw = config.getString(key, "&#D90429[Missing message: " + key + "]");
        String msg = ChatFormatter.color(raw);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            msg = msg.replace("{" + kvPairs[i] + "}", String.valueOf(kvPairs[i + 1]));
        }
        return msg;
    }

    public String get(String key) {
        return format(key);
    }
}
