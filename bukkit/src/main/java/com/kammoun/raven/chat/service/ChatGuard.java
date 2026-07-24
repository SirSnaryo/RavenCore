package com.kammoun.raven.chat.service;

import com.kammoun.raven.chat.config.ChatConfig;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatGuard {

    private final Map<UUID, Long> lastChatAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();

    public String validate(Player player, String message, ChatConfig config) {
        if (config.maxLength() > 0
                && message.length() > config.maxLength()
                && !player.hasPermission("raven.chat.bypass.length")) {
            return "too-long";
        }

        long last = lastChatAt.getOrDefault(player.getUniqueId(), 0L);
        long remaining = config.cooldownMillis() - (System.currentTimeMillis() - last);
        if (config.cooldownMillis() > 0 && remaining > 0 && !player.hasPermission("raven.chat.bypass.cooldown")) {
            return "cooldown";
        }

        String previous = lastMessages.get(player.getUniqueId());
        if (config.sameMessageCancel()
                && previous != null
                && previous.equalsIgnoreCase(message)
                && !player.hasPermission("raven.chat.bypass.same")) {
            return "same-message";
        }

        if (config.capsEnabled() && hasTooManyCaps(message, config.capsThreshold())
                && !player.hasPermission("raven.chat.bypass.caps")) {
            return "caps";
        }

        if (config.floodEnabled()
                && config.floodPattern() != null
                && config.floodPattern().matcher(message).find()
                && !player.hasPermission("raven.chat.bypass.flood")) {
            return "flood";
        }

        for (Pattern pattern : config.blockedPatterns()) {
            if (pattern.matcher(message).find() && !player.hasPermission("raven.chat.bypass.filter")) {
                return "blocked";
            }
        }

        return null;
    }

    public void record(Player player, String message) {
        lastChatAt.put(player.getUniqueId(), System.currentTimeMillis());
        lastMessages.put(player.getUniqueId(), message);
    }

    public void forget(Player player) {
        lastChatAt.remove(player.getUniqueId());
        lastMessages.remove(player.getUniqueId());
    }

    public void clear() {
        lastChatAt.clear();
        lastMessages.clear();
    }

    private boolean hasTooManyCaps(String message, double threshold) {
        int upper = 0;
        int letters = 0;
        for (char c : message.toCharArray()) {
            if (!Character.isLetter(c)) {
                continue;
            }
            letters++;
            if (Character.isUpperCase(c)) {
                upper++;
            }
        }
        return letters >= 5 && ((double) upper / letters) > threshold;
    }
}
