package com.kammoun.raven.stats.model;

import java.util.UUID;

public record StatsSnapshot(
        UUID uuid,
        String playerName,
        long kills,
        long deaths,
        long blocksBroken,
        long blocksPlaced,
        long mobKills,
        long playTime,
        double moneySpent,
        double moneyMade
) {
    public static StatsSnapshot empty(UUID uuid, String playerName) {
        return new StatsSnapshot(uuid, playerName, 0, 0, 0, 0, 0, 0, 0D, 0D);
    }

    public double kd() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    public double value(StatsType type) {
        return switch (type) {
            case KILLS -> kills;
            case DEATHS -> deaths;
            case BLOCKS_BROKEN -> blocksBroken;
            case BLOCKS_PLACED -> blocksPlaced;
            case MOB_KILLS -> mobKills;
            case PLAY_TIME -> playTime;
            case MONEY_SPENT -> moneySpent;
            case MONEY_MADE -> moneyMade;
        };
    }
}
