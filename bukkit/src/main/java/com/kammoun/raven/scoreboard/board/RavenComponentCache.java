package com.kammoun.raven.scoreboard.board;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

final class RavenComponentCache {

    private static final int MAX_SIZE = 1_000;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Map<String, Component> cache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
            return size() > MAX_SIZE;
        }
    };

    Component component(String text) {
        String safe = text == null ? "" : text;
        synchronized (cache) {
            return cache.computeIfAbsent(safe, this::parse);
        }
    }

    private Component parse(String text) {
        if (looksLikeMiniMessage(text)) {
            try {
                return MINI_MESSAGE.deserialize(text);
            } catch (RuntimeException ignored) {
                // Fall through to legacy so one bad line cannot break the sidebar.
            }
        }
        return LEGACY.deserialize(ChatFormatter.color(text));
    }

    private boolean looksLikeMiniMessage(String text) {
        int open = text.indexOf('<');
        return open >= 0 && text.indexOf('>', open + 1) > open;
    }
}
