package com.kammoun.raven.devmode;

import org.bukkit.boss.BarColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevSnapshotTest {

    @Test
    void cpuReadsThroughTheDirectJmxApiInsteadOfReflection() {
        // The old reflective call threw IllegalAccessException on Java 21 and always rendered n/a.
        assertNotEquals("&#7A7A7An/a", DevSnapshot.readCpu());
        assertTrue(DevSnapshot.readCpu().endsWith("%"), "cpu should render as a percentage");
        assertTrue(DevSnapshot.readProcessCpu().endsWith("%"));
        assertTrue(DevSnapshot.readSystemCpu().endsWith("%"));
    }

    @Test
    void valuesCarryTheirOwnColour() {
        assertEquals("&#33FF66", DevSnapshot.colour(1D));
        assertEquals("&#FFD447", DevSnapshot.colour(0.8D));
        assertEquals("&#D90429", DevSnapshot.colour(0.4D));
        assertTrue(DevSnapshot.readCpu().startsWith("&#A855F7"), "cpu renders purple");
    }

    @Test
    void pingAveragesTheConnectedPlayers() {
        assertEquals(-1, DevSnapshot.averagePing(List.of()));
        assertEquals("&#7A7A7An/a", DevSnapshot.ping(-1));
        assertEquals("&#4CC9F042ms", DevSnapshot.ping(42));
        assertEquals("&#FF9F1C250ms", DevSnapshot.ping(250));
    }

    @Test
    void msptThresholdsColourTheBar() {
        assertEquals(BarColor.GREEN, DevSnapshot.colorForMspt(10D));
        assertEquals(BarColor.YELLOW, DevSnapshot.colorForMspt(45D));
        assertEquals(BarColor.RED, DevSnapshot.colorForMspt(80D));
    }

    @Test
    void tpsThresholdsColourTheBar() {
        assertEquals(BarColor.GREEN, DevSnapshot.colorForTps(20D));
        assertEquals(BarColor.YELLOW, DevSnapshot.colorForTps(16D));
        assertEquals(BarColor.RED, DevSnapshot.colorForTps(5D));
    }

    @Test
    void theWorseOfTpsAndMsptWins() {
        assertEquals(BarColor.RED, DevSnapshot.color(20D, 80D));
        assertEquals(BarColor.RED, DevSnapshot.color(5D, 10D));
        assertEquals(BarColor.YELLOW, DevSnapshot.color(16D, 45D));
        assertEquals(BarColor.GREEN, DevSnapshot.color(20D, 10D));
    }

    @Test
    void progressIsClampedToTheBarRange() {
        assertEquals(1D, DevSnapshot.progress(0D), 0.0001D);
        assertEquals(0D, DevSnapshot.progress(50D), 0.0001D);
        assertEquals(0D, DevSnapshot.progress(500D), 0.0001D);
        assertEquals(0.5D, DevSnapshot.progress(25D), 0.0001D);
    }

    @Test
    void uptimeIsRenderedInHoursAndMinutes() {
        assertTrue(DevSnapshot.readUptime().endsWith("m"), "uptime should read like 3h 12m");
    }
}
