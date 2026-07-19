package com.kammoun.raven.scoreboard.stats;

public record ScoreboardStats(int kills, int deaths, long playtimeSeconds) {

    public static ScoreboardStats empty() {
        return new ScoreboardStats(0, 0, 0L);
    }

    public ScoreboardStats withKill() {
        return new ScoreboardStats(kills + 1, deaths, playtimeSeconds);
    }

    public ScoreboardStats withDeath() {
        return new ScoreboardStats(kills, deaths + 1, playtimeSeconds);
    }

    public ScoreboardStats withPlaytimeSeconds(long seconds) {
        return new ScoreboardStats(kills, deaths, Math.max(0L, seconds));
    }

    public ScoreboardStats mergeMax(ScoreboardStats other) {
        return new ScoreboardStats(
                Math.max(kills, other.kills),
                Math.max(deaths, other.deaths),
                Math.max(playtimeSeconds, other.playtimeSeconds)
        );
    }
}
