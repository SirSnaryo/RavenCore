package com.kammoun.raven.placeholder;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavenPlaceholdersTest {

    @Test
    void expectedKeysAreRegistered() {
        RavenPlaceholders placeholders = new RavenPlaceholders(null);
        Set<String> keys = placeholders.keys();

        assertTrue(keys.contains("player"));
        assertTrue(keys.contains("player_name"));
        assertTrue(keys.contains("world"));
        assertTrue(keys.contains("server"));
        assertTrue(keys.contains("server_display"));
        assertTrue(keys.contains("role"));
        assertTrue(keys.contains("server_role"));
        assertTrue(keys.contains("online"));
        assertTrue(keys.contains("network_online"));
        assertTrue(keys.contains("balance"));
        assertTrue(keys.contains("balance_formatted"));
        assertTrue(keys.contains("balance_compact"));
        assertTrue(keys.contains("balance_full"));
        assertTrue(keys.contains("balance_raw"));
        assertTrue(keys.contains("money"));
        assertTrue(keys.contains("money_formatted"));
        assertTrue(keys.contains("ravs"));
        assertTrue(keys.contains("ravs_balance"));
        assertTrue(keys.contains("ravs_balance_formatted"));
        assertTrue(keys.contains("kills"));
        assertTrue(keys.contains("deaths"));
        assertTrue(keys.contains("blocks_broken"));
        assertTrue(keys.contains("blocks_placed"));
        assertTrue(keys.contains("mob_kills"));
        assertTrue(keys.contains("kd"));
        assertTrue(keys.contains("money_spent"));
        assertTrue(keys.contains("money_made"));
        assertTrue(keys.contains("playtime"));
        assertTrue(keys.contains("play_time"));
        assertTrue(keys.contains("team"));
        assertTrue(keys.contains("ping"));
        assertTrue(keys.contains("tps"));
        assertTrue(keys.contains("mspt"));
        assertTrue(keys.contains("cpu"));
        assertTrue(keys.contains("low_tps_region"));
        assertTrue(keys.contains("mid_tps_region"));
        assertTrue(keys.contains("region_tps_low"));
        assertTrue(keys.contains("region_tps_mid"));
    }

    @Test
    void formatsPlaytimeSeconds() {
        assertEquals("0m", RavenPlaceholders.formatPlaytimeSeconds(0));
        assertEquals("0m", RavenPlaceholders.formatPlaytimeSeconds(45));
        assertEquals("1m", RavenPlaceholders.formatPlaytimeSeconds(60));
        assertEquals("2m", RavenPlaceholders.formatPlaytimeSeconds(125));
        assertEquals("1h 1m", RavenPlaceholders.formatPlaytimeSeconds(3665));
        assertEquals("1d 1h", RavenPlaceholders.formatPlaytimeSeconds(90000));
    }

    @Test
    void formatsPing() {
        assertEquals("0ms", RavenPlaceholders.formatPing(-5));
        assertEquals("0ms", RavenPlaceholders.formatPing(0));
        assertEquals("45ms", RavenPlaceholders.formatPing(45));
    }
}
