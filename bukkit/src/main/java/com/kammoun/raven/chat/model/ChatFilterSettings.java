package com.kammoun.raven.chat.model;

import java.util.List;

public record ChatFilterSettings(
        boolean profanityEnabled,
        List<String> profanityWords,
        boolean advertisingEnabled,
        boolean blockIps,
        boolean blockDomains,
        boolean blockDiscordInvites,
        List<String> allowedDomains,
        boolean lagTestEnabled,
        int lagTestMinTokens,
        int lagTestMinRepeatedLength,
        int lagTestMinRepeatedTokens
) {
}
