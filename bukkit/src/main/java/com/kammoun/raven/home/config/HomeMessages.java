package com.kammoun.raven.home.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public final class HomeMessages {
    private final FileConfiguration config;

    public HomeMessages(FileConfiguration config) {
        this.config = config;
    }

    public String get(String key, String... replacements) {
        String value = config.getString("messages." + key, "&cMissing message: " + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return ChatFormatter.color(value);
    }

    public void send(CommandSender sender, String key, String... replacements) {
        String value = get(key, replacements);
        if (!value.isBlank()) sender.sendMessage(value);
    }
}
