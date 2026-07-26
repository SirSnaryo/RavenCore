package com.kammoun.raven.stats.model;

import java.util.Locale;

public enum StatsType {
    KILLS("kills", false),
    DEATHS("deaths", false),
    BLOCKS_BROKEN("blocks_broken", false),
    BLOCKS_PLACED("blocks_placed", false),
    MOB_KILLS("mob_kills", false),
    PLAY_TIME("play_time", false),
    ITEMS_CRAFTED("items_crafted", false),
    JUMPS("jumps", false),
    CHAT_MESSAGES("chat_messages", false),
    MONEY_SPENT("money_spent", true),
    MONEY_MADE("money_made", true);

    private final String column;
    private final boolean decimal;

    StatsType(String column, boolean decimal) {
        this.column = column;
        this.decimal = decimal;
    }

    public String column() {
        return column;
    }

    public boolean decimal() {
        return decimal;
    }

    public static StatsType parse(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (StatsType type : values()) {
            if (type.column.equals(normalized) || type.name().equalsIgnoreCase(normalized)) return type;
        }
        return null;
    }
}
