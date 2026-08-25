package com.kammoun.raven.leaderboard.papi;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.leaderboard.service.LeaderboardPlaceholderCache;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.library.util.NumberUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * %leaderboard_&lt;type&gt;_&lt;name|value|raw&gt;_&lt;position&gt;%
 */
public final class LeaderboardPlaceholders extends KPlaceholderExpansion {

    public LeaderboardPlaceholders(RavenCore plugin, LeaderboardPlaceholderCache cache) {
        super(plugin, "leaderboard");
        List<LeaderboardType> types = new ArrayList<>(List.of(LeaderboardType.values()));
        // longest key first, otherwise the "money_" prefix swallows "money_spent_name_1"
        types.sort(Comparator.comparingInt((LeaderboardType type) -> type.key().length()).reversed());
        for (LeaderboardType type : types) {
            registerPrefix(type.key() + "_", (player, params) -> resolve(cache, type, params));
        }
    }

    private static String resolve(LeaderboardPlaceholderCache cache, LeaderboardType type, String params) {
        int split = params.lastIndexOf('_');
        if (split < 1) return null;
        int position = NumberUtils.parseInt(params.substring(split + 1));
        if (position < 1) return null;
        return switch (params.substring(0, split)) {
            case "name" -> cache.name(type, position);
            case "value" -> cache.value(type, position);
            case "raw" -> cache.raw(type, position);
            default -> null;
        };
    }
}
