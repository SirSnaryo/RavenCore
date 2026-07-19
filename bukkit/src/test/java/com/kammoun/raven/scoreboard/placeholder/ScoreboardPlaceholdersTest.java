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

    @Test
    void formatsMoneyWithoutCurrencyName() {
        assertEquals("1$", ScoreboardPlaceholders.formatCompactMoney(1));
        assertEquals("999$", ScoreboardPlaceholders.formatCompactMoney(999));
        assertEquals("1k", ScoreboardPlaceholders.formatCompactMoney(1_000));
        assertEquals("1.1k", ScoreboardPlaceholders.formatCompactMoney(1_100));
        assertEquals("1.9k", ScoreboardPlaceholders.formatCompactMoney(1_900));
        assertEquals("10k", ScoreboardPlaceholders.formatCompactMoney(10_000));
        assertEquals("1m", ScoreboardPlaceholders.formatCompactMoney(1_000_000));
        assertEquals("1.1m", ScoreboardPlaceholders.formatCompactMoney(1_100_000));
    }
}
