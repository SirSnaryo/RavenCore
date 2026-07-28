package com.kammoun.raven.leaderboard.model;

import java.util.Locale;

public enum LeaderboardType {
    MONEY("money", "Money", true, false),
    KILLS("kills", "Kills", false, true),
    DEATHS("deaths", "Deaths", false, true),
    MOB_KILLS("mob_kills", "Mobs Killed", false, true),
    BLOCKS_BROKEN("blocks_broken", "Blocks Broken", false, true),
    BLOCKS_PLACED("blocks_placed", "Blocks Placed", false, true),
    PLAY_TIME("play_time", "Play Time", false, true),
    MONEY_SPENT("money_spent", "Money Spent", true, true),
    MONEY_MADE("money_made", "Money Made", true, true);

    private final String key;
    private final String displayName;
    private final boolean decimal;
    private final boolean statsColumn;

    LeaderboardType(String key, String displayName, boolean decimal, boolean statsColumn) {
        this.key = key;
        this.displayName = displayName;
        this.decimal = decimal;
        this.statsColumn = statsColumn;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public boolean decimal() { return decimal; }
    public boolean statsColumn() { return statsColumn; }

    public static LeaderboardType parse(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("mobs_killed")) normalized = "mob_kills";
        if (normalized.equals("playtime")) normalized = "play_time";
        if (normalized.equals("sells_made") || normalized.equals("sell")) normalized = "money_made";
        for (LeaderboardType type : values()) {
            if (type.key.equals(normalized) || type.name().equalsIgnoreCase(normalized)) return type;
        }
        return null;
    }
}
