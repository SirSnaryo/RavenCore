package com.kammoun.raven.leaderboard.db;

import com.kammoun.raven.leaderboard.model.LeaderboardEntry;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LeaderboardRepository {
    private static final Set<String> STATS_COLUMNS = Set.of(
            "kills", "deaths", "mob_kills", "blocks_broken", "blocks_placed",
            "play_time", "money_spent", "money_made");
    private final MySQLDBManager database;

    public LeaderboardRepository(MySQLDBManager database) {
        this.database = database;
    }

    public void init() throws SQLException {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ravencore_economy_balances (
                      uuid VARCHAR(36) NOT NULL,
                      player_name VARCHAR(32) NULL,
                      balance DOUBLE NOT NULL DEFAULT 0,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (uuid),
                      INDEX idx_ravencore_economy_balance (balance)
                    )
                    """);
        }
    }

    public List<LeaderboardEntry> top(LeaderboardType type, int limit) throws SQLException {
        if (type == LeaderboardType.MONEY) return topMoney(limit);
        if (!type.statsColumn() || !STATS_COLUMNS.contains(type.key())) return List.of();
        String column = type.key();
        String sql = "SELECT uuid, player_name, " + column + " AS value "
                + "FROM ravencore_player_stats WHERE " + column + " > 0 "
                + "ORDER BY " + column + " DESC LIMIT ?";
        return query(sql, limit);
    }

    private List<LeaderboardEntry> topMoney(int limit) throws SQLException {
        String sql = "SELECT b.uuid, COALESCE(b.player_name, s.player_name) AS player_name, b.balance AS value "
                + "FROM ravencore_economy_balances b "
                + "LEFT JOIN ravencore_player_stats s ON s.uuid = b.uuid "
                + "WHERE b.balance > 0 ORDER BY b.balance DESC LIMIT ?";
        return query(sql, limit);
    }

    private List<LeaderboardEntry> query(String sql, int limit) throws SQLException {
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                int rank = 1;
                while (result.next()) {
                    String name = result.getString("player_name");
                    if (name == null || name.isBlank()) name = "Unknown";
                    entries.add(new LeaderboardEntry(rank++,
                            UUID.fromString(result.getString("uuid")),
                            name,
                            result.getDouble("value")));
                }
            }
        }
        return List.copyOf(entries);
    }

}
