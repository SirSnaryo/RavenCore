package com.kammoun.raven.teleport.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpaRequestTest {

    @Test
    void expiresOnlyAfterConfiguredSeconds() {
        TpaRequest request = new TpaRequest(
                UUID.randomUUID(),
                "Requester",
                "smp-a",
                UUID.randomUUID(),
                "Target",
                "smp-b",
                TpaRequest.Type.TPA,
                Instant.parse("2026-07-20T00:00:00Z").toEpochMilli()
        );

        assertFalse(request.isExpired(120, Instant.parse("2026-07-20T00:01:59Z").toEpochMilli()));
        assertTrue(request.isExpired(120, Instant.parse("2026-07-20T00:02:01Z").toEpochMilli()));
    }
}
