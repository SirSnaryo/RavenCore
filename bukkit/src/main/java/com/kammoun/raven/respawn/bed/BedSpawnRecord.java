package com.kammoun.raven.respawn.bed;

import java.util.UUID;

public record BedSpawnRecord(UUID playerUuid, String serverId, String world,
                             int x, int y, int z) {
}
