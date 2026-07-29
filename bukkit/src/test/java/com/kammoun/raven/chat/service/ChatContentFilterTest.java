package com.kammoun.raven.chat.service;

import com.kammoun.raven.chat.model.ChatFilterSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatContentFilterTest {

    private final ChatContentFilter filter = new ChatContentFilter();

    private final ChatFilterSettings settings = new ChatFilterSettings(
            true,
            List.of("fuck", "shit"),
            true,
            true,
            true,
            true,
            List.of("ravenmc.live"),
            true,
            3,
            4,
            2
    );

    @Test
    void blocksObfuscatedProfanity() {
        assertEquals("profanity", filter.validate("f.u.u.u.c.k", settings));
        assertEquals("profanity", filter.validate("fuuuuck", settings));
        assertEquals("profanity", filter.validate("f*ck", settings));
        assertEquals("profanity", filter.validate("sh1t", settings));
    }

    @Test
    void blocksIpAddressesAndDomains() {
        assertEquals("advertising", filter.validate("join 192.168.1.10 now", settings));
        assertEquals("advertising", filter.validate("join badserver dot net", settings));
        assertEquals("advertising", filter.validate("discord.gg/raven", settings));
    }

    @Test
    void allowsConfiguredDomains() {
        assertNull(filter.validate("site ravenmc.live", settings));
        assertNull(filter.validate("site store.ravenmc.live", settings));
    }

    @Test
    void blocksLagTestSpam() {
        assertEquals("lag-test", filter.validate("t tttttt ttttttttt", settings));
    }

    @Test
    void allowsNormalChat() {
        assertNull(filter.validate("anyone want to run rtp together?", settings));
    }
}
