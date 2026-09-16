package com.kammoun.raven.library.redis.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContinuumTransferMetadataTest {

    @Test
    void teleportEventCarriesOptionalContinuumMetadata() {
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();

        event.setContinuumClientEntityId(5821);
        event.setContinuumDimensionKey("minecraft:overworld");

        assertEquals(5821, event.getContinuumClientEntityId());
        assertEquals("minecraft:overworld", event.getContinuumDimensionKey());
    }

    @Test
    void borderCrossEventCarriesOptionalContinuumMetadata() {
        BorderCrossEvent event = new BorderCrossEvent();

        event.setContinuumClientEntityId(73);
        event.setContinuumDimensionKey("minecraft:the_nether");

        assertEquals(73, event.getContinuumClientEntityId());
        assertEquals("minecraft:the_nether", event.getContinuumDimensionKey());
    }
}
