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
        long itemsCrafted,
        long jumps,
        long chatMessages,
        double moneySpent,
        double moneyMade
) {
    public static StatsSnapshot empty(UUID uuid, String playerName) {
        return new StatsSnapshot(uuid, playerName, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0D, 0D);
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
            case ITEMS_CRAFTED -> itemsCrafted;
            case JUMPS -> jumps;
            case CHAT_MESSAGES -> chatMessages;
            case MONEY_SPENT -> moneySpent;
            case MONEY_MADE -> moneyMade;
        };
    }
}
