package com.kammoun.raven.chat.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ChatMessages {

    private final FileConfiguration config;

    public ChatMessages(FileConfiguration config) {
        this.config = config;
    }

    public String get(String key) {
        String raw = config.getString(key, "&c[Missing message: " + key + "]");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public void send(Player player, String key) {
        player.sendMessage(get(key));
    }
}
