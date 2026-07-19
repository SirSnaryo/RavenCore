package com.kammoun.raven.billford.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.stream.Collectors;

public class BillfordMessages {

    private final FileConfiguration config;

    public BillfordMessages(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Fetches a message by dot-path, translates &-color codes, and replaces any number
     * of {key} placeholders. Usage: {@code format("preset.created", "preset", name)}.
     */
    public String format(String key, Object... kvPairs) {
        String raw = config.getString(key, "&c[Missing message: " + key + "]");
        String msg = ChatFormatter.color(raw);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            msg = msg.replace("{" + kvPairs[i] + "}", String.valueOf(kvPairs[i + 1]));
        }
        return msg;
    }

    public String get(String key) {
        return format(key);
    }

    /** Returns a color-translated list for multi-line entries (e.g. command help). */
    public List<String> getList(String key) {
        return config.getStringList(key).stream()
                .map(ChatFormatter::color)
                .collect(Collectors.toList());
    }

    public void send(CommandSender to, String key, Object... kvPairs) {
        String msg = format(key, kvPairs);
        if (!msg.isEmpty()) to.sendMessage(msg);
    }
}
