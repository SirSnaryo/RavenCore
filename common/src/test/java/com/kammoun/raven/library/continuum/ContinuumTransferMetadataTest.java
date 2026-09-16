package com.kammoun.raven.library.continuum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuumTransferMetadataTest {

    @Test
    void acceptsFreshMetadataForTargetServer() {
        ContinuumTransferMetadata metadata = new ContinuumTransferMetadata(
                41,
                "smp-world-na",
                "minecraft:overworld",
                System.currentTimeMillis()
        );

        assertTrue(metadata.usableFor("SMP-WORLD-NA", 30_000L));
    }

    @Test
    void rejectsMetadataForWrongServerOrExpiredRecord() {
        ContinuumTransferMetadata wrongServer = new ContinuumTransferMetadata(
                41,
                "smp-world-eu",
                "minecraft:overworld",
                System.currentTimeMillis()
        );
        ContinuumTransferMetadata expired = new ContinuumTransferMetadata(
                41,
                "smp-world-na",
                "minecraft:overworld",
                System.currentTimeMillis() - 60_000L
        );

        assertFalse(wrongServer.usableFor("smp-world-na", 30_000L));
        assertFalse(expired.usableFor("smp-world-na", 30_000L));
    }
}
