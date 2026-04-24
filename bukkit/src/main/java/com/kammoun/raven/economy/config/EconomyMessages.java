package com.kammoun.raven.economy.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class EconomyMessages {

    private final FileConfiguration config;

    public EconomyMessages(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Fetches a message by dot-path, translates &-color codes, and replaces
     * any number of {key} placeholders.
     *
     * Usage: messages.format("pay.sent", "amount", "100 Coins", "player", "Steve")
     */
    public String format(String key, Object... kvPairs) {
        String raw = config.getString(key, "&c[Missing message: " + key + "]");
        String msg = ChatColor.translateAlternateColorCodes('&', raw);
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
