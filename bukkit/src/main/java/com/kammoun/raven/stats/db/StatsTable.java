package com.kammoun.raven.stats.db;

import com.kammoun.raven.library.database.KTables;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class StatsTable implements KTables {
    private final MySQLDBManager database;

    public StatsTable(MySQLDBManager database) {
        this.database = database;
    }

    @Override
    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS ravencore_player_stats ("
                + "uuid VARCHAR(36) NOT NULL, player_name VARCHAR(32) NOT NULL,"
                + "kills BIGINT NOT NULL DEFAULT 0, deaths BIGINT NOT NULL DEFAULT 0,"
                + "blocks_broken BIGINT NOT NULL DEFAULT 0, blocks_placed BIGINT NOT NULL DEFAULT 0,"
                + "mob_kills BIGINT NOT NULL DEFAULT 0, play_time BIGINT NOT NULL DEFAULT 0,"
                + "money_spent DOUBLE NOT NULL DEFAULT 0, money_made DOUBLE NOT NULL DEFAULT 0,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (uuid),"
                + "INDEX idx_ravencore_stats_name (player_name),"
                + "INDEX idx_ravencore_stats_kills (kills),"
                + "INDEX idx_ravencore_stats_deaths (deaths),"
                + "INDEX idx_ravencore_stats_mob_kills (mob_kills),"
                + "INDEX idx_ravencore_stats_blocks_broken (blocks_broken),"
                + "INDEX idx_ravencore_stats_blocks_placed (blocks_placed),"
                + "INDEX idx_ravencore_stats_play_time (play_time),"
                + "INDEX idx_ravencore_stats_money_spent (money_spent),"
                + "INDEX idx_ravencore_stats_money_made (money_made))";
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException error) {
            throw new RuntimeException("Failed to create stats table", error);
        }
    }

    @Override
    public void dropTable() {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS ravencore_player_stats");
        } catch (SQLException error) {
            throw new RuntimeException("Failed to drop stats table", error);
        }
    }
}
