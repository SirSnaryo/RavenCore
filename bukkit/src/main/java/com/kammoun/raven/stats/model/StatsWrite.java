package com.kammoun.raven.stats.model;

import java.util.UUID;

public record StatsWrite(UUID uuid, String playerName, StatsDelta delta, StatsSession owner) {
}
