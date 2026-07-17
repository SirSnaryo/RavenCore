package com.kammoun.raven.velocity.privatemessage.config;

import com.kammoun.raven.velocity.privatemessage.PrivateMessageModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class PrivateMessages {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final PrivateMessageModule module;

    public PrivateMessages(PrivateMessageModule module) {
        this.module = module;
    }

    public Component format(String path, String def, String... placeholders) {
        String raw = module.getString(path, def);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace(placeholders[i], placeholders[i + 1]);
        }
        return SERIALIZER.deserialize(raw);
    }
}
