package com.kammoun.raven.combat.model;

import java.util.UUID;

public record CombatTag(UUID opponentUuid, long expiresAt) {
    public boolean active() {
        return expiresAt > System.currentTimeMillis();
    }

    public int remainingSeconds() {
        return Math.max(0, (int) Math.ceil((expiresAt - System.currentTimeMillis()) / 1000.0));
    }
}
