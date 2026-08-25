package com.kammoun.raven.library.pearlstasis;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PearlStasisRecordTest {

    private final Gson gson = new Gson();

    @Test
    void testRecordSerializationAndDeserialization() {
        UUID uuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        PearlStasisRecord record = new PearlStasisRecord(
                123L,
                uuid,
                "smp-world-na",
                "world",
                100.5,
                64.0,
                -200.5,
                90.0f,
                0.0f,
                now
        );

        String json = gson.toJson(record);
        PearlStasisRecord deserialized = gson.fromJson(json, PearlStasisRecord.class);

        assertEquals(123L, deserialized.id());
        assertEquals(uuid, deserialized.playerUuid());
        assertEquals("smp-world-na", deserialized.targetServer());
        assertEquals("world", deserialized.world());
        assertEquals(100.5, deserialized.x());
        assertEquals(64.0, deserialized.y());
        assertEquals(-200.5, deserialized.z());
        assertEquals(90.0f, deserialized.yaw());
        assertEquals(0.0f, deserialized.pitch());
        assertEquals(now, deserialized.createdAt());
    }

    @Test
    void testMarkerKeyFormat() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String key = PearlStasisKeys.markerKey(uuid);
        assertEquals("raven:pearl:has_pending:11111111-2222-3333-4444-555555555555", key);
    }
}
