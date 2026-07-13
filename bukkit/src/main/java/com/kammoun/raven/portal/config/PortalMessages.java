package com.kammoun.raven.portal.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.CommandSender;

public class PortalMessages {

    private final FileConfiguration config;

    public PortalMessages(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Fetches a message by dot-path, translates &-color codes, and replaces
     * any number of {key} placeholders.
     *
     * Usage: messages.format("border.deny-message", "server", "smp-world-eu")
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

    /** Sends a message, unless it's configured empty (used to silence a message). */
    public void send(CommandSender target, String key, Object... kvPairs) {
        String msg = format(key, kvPairs);
        if (!msg.isEmpty()) target.sendMessage(msg);
    }
}
