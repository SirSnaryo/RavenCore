package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

/**
 * Carries the authoritative Billford rotation state after it advances on one server —
 * either an automatic rotation or an admin {@code /billfordpreset switch}. Every other
 * server applies the versioned preset, interval and last-change state directly (no DB read)
 * so the whole network shows the same active trade. When {@code announce} is set the
 * receivers also broadcast the "preset changed" message locally.
 */
@Getter
@Setter
public class BillfordStateUpdatedEvent extends CrossServerEvent {
    private String currentPreset;
    private long lastChange;
    private long rotationIntervalMillis;
    private long stateVersion;
    private boolean announce;
}
