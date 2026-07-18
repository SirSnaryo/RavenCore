package com.kammoun.raven.scoreboard.placeholder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardPlaceholdersTest {

    @Test
    void formatsPlaytimeAsCompactDaysAndHours() {
        long ticks = (2L * 24L * 60L * 60L * 20L)
                + (17L * 60L * 60L * 20L)
                + (32L * 60L * 20L);

        assertEquals("2d 17h", ScoreboardPlaceholders.formatPlaytime(ticks));
    }

    @Test
    void formatsPingWithMillisecondsSuffix() {
        assertEquals("45ms", ScoreboardPlaceholders.formatPing(45));
    }
}
