package com.kammoun.raven.punishment.model;

import java.util.UUID;

public record PunishmentRecord(
        long id,
        UUID targetUuid,
        String targetName,
        PunishmentType type,
        String actor,
        String reason,
        long createdAt,
        Long expiresAt
) {
    public boolean permanent() {
        return expiresAt == null;
    }
}
