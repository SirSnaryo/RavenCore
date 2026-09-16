package com.kammoun.raven.devmode.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRenderThrottleTest {

    @Test
    void allowsFirstFrameThenThrottlesUntilIntervalPasses() {
        MapRenderThrottle throttle = new MapRenderThrottle(5000L);

        assertTrue(throttle.shouldRender(1000L));
        assertFalse(throttle.shouldRender(2000L));
        assertFalse(throttle.shouldRender(5999L));
        assertTrue(throttle.shouldRender(6000L));
    }
}
