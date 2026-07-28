package com.kammoun.raven.leaderboard.model;

import java.util.UUID;

public record LeaderboardEntry(int rank, UUID uuid, String playerName, double value) {
}
