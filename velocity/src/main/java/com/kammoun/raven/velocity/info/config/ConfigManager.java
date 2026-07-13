package com.kammoun.raven.velocity.info.config;

import com.kammoun.raven.velocity.info.InfoModule;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ConfigManager {

    private final InfoModule module;

    private final Map<InfoMessage, List<Component>> messages = new LinkedHashMap<>();
    private Component linkSentActionBar;
    private boolean playSound;
    private Sound sound;

    public ConfigManager(InfoModule module) {
        this.module = module;
        load();
    }

    public void load() {
        messages.clear();
        for (InfoMessage key : InfoMessage.values()) {
            messages.put(key, module.getStringList(key.getPath()).stream()
                    .map(this::translate)
                    .toList());
        }

        linkSentActionBar = translate(module.getString("link-sent", ""));

        playSound = module.getBoolean("play-sound", true);
        String soundKey = module.getString("sound", "minecraft:entity.player.levelup");
        sound = Sound.sound(Key.key(soundKey), Sound.Source.MASTER, 1.0f, 1.0f);
    }

    public void sendMessage(Player player, InfoMessage key) {
        for (Component line : messages.getOrDefault(key, List.of())) {
            player.sendMessage(line);
        }
    }

    public void sendLinkSentActionBar(Player player) {
        player.sendActionBar(linkSentActionBar);
        if (playSound) {
            player.playSound(sound);
        }
    }

    private Component translate(String legacyText) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
    }
}
