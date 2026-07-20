package com.kammoun.raven.teleport.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.util.PlaceholderHelper;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TeleportConfig {

    private static final Pattern TAG_PATTERN = Pattern.compile("<(click|hover):([^:]+):(['\"]?)(.*?)\\3>(.*?)</\\1>", Pattern.DOTALL);

    private final FileConfiguration config;
    private final Map<TeleportMessage, String> messages = new EnumMap<>(TeleportMessage.class);
    private final Map<String, KSound> sounds = new HashMap<>();
    private final Map<String, String> worldNicknames = new HashMap<>();

    public TeleportConfig(FileConfiguration config) {
        this.config = config;
        reload();
    }

    public void reload() {
        messages.clear();
        sounds.clear();
        worldNicknames.clear();

        for (TeleportMessage message : TeleportMessage.values()) {
            messages.put(message, config.getString("messages." + message.path(), message.fallback()));
        }

        ConfigurationSection soundsSection = config.getConfigurationSection("sounds");
        if (soundsSection != null) {
            for (String key : soundsSection.getKeys(false)) {
                ConfigurationSection soundSection = soundsSection.getConfigurationSection(key);
                if (soundSection != null) {
                    KSound sound = KSound.getSoundFromConfig(soundSection);
                    if (sound != null) {
                        sounds.put(key, sound);
                    }
                    continue;
                }

                String raw = soundsSection.getString(key);
                if (raw == null) continue;
                try {
                    sounds.put(key, new KSound(Sound.valueOf(raw.toUpperCase()), 1.0f, 1.0f));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection nicknames = config.getConfigurationSection("world-nicknames");
        if (nicknames != null) {
            for (String key : nicknames.getKeys(false)) {
                worldNicknames.put(key, nicknames.getString(key, key));
            }
        }
    }

    public boolean confirmMenusEnabled() {
        return config.getBoolean("confirm-menu.enabled", true);
    }

    public long clearTimeSeconds() {
        return config.getLong("data.clear-time", 120L);
    }

    public long requestCooldownSeconds() {
        return config.getLong("data.request-cooldown", 5L);
    }

    public int countdownSeconds() {
        return config.getInt("teleport-countdown.seconds", 5);
    }

    public String worldNickname(String world) {
        return worldNicknames.getOrDefault(world, world);
    }

    public String raw(TeleportMessage key, String... placeholders) {
        return PlaceholderHelper.parsePlaceholders(messages.getOrDefault(key, key.fallback()), placeholders);
    }

    public void send(Player player, TeleportMessage key, String... placeholders) {
        player.sendMessage(ChatFormatter.color(raw(key, placeholders)));
    }

    public void actionBar(Player player, TeleportMessage key, String... placeholders) {
        player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatFormatter.color(raw(key, placeholders)))
        );
    }

    public void rich(Player player, TeleportMessage key, String... placeholders) {
        player.spigot().sendMessage(parseComponents(ChatFormatter.color(raw(key, placeholders))).toArray(BaseComponent[]::new));
    }

    public void sound(Player player, String key) {
        KSound sound = sounds.get(key);
        if (sound != null) {
            sound.playSound(player);
        }
    }

    private List<BaseComponent> parseComponents(String text) {
        List<BaseComponent> result = new ArrayList<>();
        Matcher matcher = TAG_PATTERN.matcher(text);
        int lastIndex = 0;

        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                result.addAll(Arrays.asList(TextComponent.fromLegacyText(text.substring(lastIndex, matcher.start()))));
            }

            String type = matcher.group(1);
            String action = matcher.group(2);
            String value = matcher.group(4);
            String content = matcher.group(5);
            List<BaseComponent> innerComponents = parseComponents(content);

            for (BaseComponent component : innerComponents) {
                try {
                    if (type.equalsIgnoreCase("click")) {
                        component.setClickEvent(new ClickEvent(ClickEvent.Action.valueOf(action.toUpperCase()), value));
                    } else if (type.equalsIgnoreCase("hover")) {
                        component.setHoverEvent(new HoverEvent(
                                HoverEvent.Action.valueOf(action.toUpperCase()),
                                TextComponent.fromLegacyText(ChatFormatter.color(value))
                        ));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            result.addAll(innerComponents);
            lastIndex = matcher.end();
        }

        if (lastIndex < text.length()) {
            result.addAll(Arrays.asList(TextComponent.fromLegacyText(text.substring(lastIndex))));
        }
        return result;
    }
}
