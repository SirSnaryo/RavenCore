package com.kammoun.raven.leaderboard.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public final class LeaderboardMessages {
    private final FileConfiguration config;

    public LeaderboardMessages(FileConfiguration config) {
        this.config = config;
    }

    public String get(String key, String... replacements) {
        String value = config.getString("messages." + key, "&#D90429[Missing message: " + key + "]");
        for (int i = 0; i + 1 < replacements.length; i += 2) value = value.replace(replacements[i], replacements[i + 1]);
        return value;
    }

    public void send(CommandSender sender, String key, String... replacements) {
        String message = get(key, replacements);
        if (!message.isBlank()) sender.sendMessage(ChatFormatter.color(message));
    }
}
