package com.kammoun.raven.scoreboard.stats;

import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class ScoreboardStatsRepository {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS ravencore_scoreboard_stats (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "kills INT NOT NULL DEFAULT 0, " +
                    "deaths INT NOT NULL DEFAULT 0, " +
                    "playtime_seconds BIGINT NOT NULL DEFAULT 0, " +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ")";

    private static final String SELECT =
            "SELECT kills, deaths, playtime_seconds FROM ravencore_scoreboard_stats WHERE uuid = ?";

    private static final String UPSERT =
            "INSERT INTO ravencore_scoreboard_stats (uuid, kills, deaths, playtime_seconds) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE kills = VALUES(kills), deaths = VALUES(deaths), " +
                    "playtime_seconds = VALUES(playtime_seconds)";

    private final MySQLDBManager db;

    public ScoreboardStatsRepository(MySQLDBManager db) {
        this.db = db;
    }

    public void init() throws SQLException {
        try (Connection connection = db.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE);
        }
    }

    public ScoreboardStats load(UUID uuid) throws SQLException {
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return ScoreboardStats.empty();
                }
                return new ScoreboardStats(
                        result.getInt("kills"),
                        result.getInt("deaths"),
                        result.getLong("playtime_seconds")
                );
            }
        }
    }

    public void save(UUID uuid, ScoreboardStats stats) throws SQLException {
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, stats.kills());
            statement.setInt(3, stats.deaths());
            statement.setLong(4, stats.playtimeSeconds());
            statement.executeUpdate();
        }
    }
}
