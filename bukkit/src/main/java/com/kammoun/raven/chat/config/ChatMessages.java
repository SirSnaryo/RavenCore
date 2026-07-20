package com.kammoun.raven.chat.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ChatMessages {

    private final FileConfiguration config;

    public ChatMessages(FileConfiguration config) {
        this.config = config;
    }

    public String get(String key) {
        String raw = config.getString(key, "&#D90429[Missing message: " + key + "]");
        return ChatFormatter.color(raw);
    }

    public void send(Player player, String key) {
        player.sendMessage(get(key));
    }
}
