package com.kammoun.raven.punishment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentDurationParserTest {

    @Test
    void parsesHumanDurations() {
        assertEquals(60_000L, PunishmentDurationParser.parseMillis("1m").orElseThrow());
        assertEquals(3_600_000L, PunishmentDurationParser.parseMillis("1h").orElseThrow());
        assertEquals(86_400_000L, PunishmentDurationParser.parseMillis("1d").orElseThrow());
        assertEquals(604_800_000L, PunishmentDurationParser.parseMillis("1w").orElseThrow());
    }

    @Test
    void treatsPermanentWordsAsNoDuration() {
        assertTrue(PunishmentDurationParser.parseMillis("perm").isEmpty());
        assertTrue(PunishmentDurationParser.parseMillis("permanent").isEmpty());
    }
}
