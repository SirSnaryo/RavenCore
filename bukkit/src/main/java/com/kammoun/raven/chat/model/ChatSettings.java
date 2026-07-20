package com.kammoun.raven.chat.model;

import java.util.UUID;

public record ChatSettings(UUID uuid, boolean chatEnabled, boolean broadcastEnabled) {

    public static ChatSettings defaults(UUID uuid) {
        return new ChatSettings(uuid, true, true);
    }

    public ChatSettings withChat(boolean enabled) {
        return new ChatSettings(uuid, enabled, broadcastEnabled);
    }

    public ChatSettings withBroadcast(boolean enabled) {
        return new ChatSettings(uuid, chatEnabled, enabled);
    }
}
