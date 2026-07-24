package com.kammoun.raven.home.db;

import com.kammoun.raven.library.database.KTables;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class HomeTable implements KTables {
    private final MySQLDBManager database;

    public HomeTable(MySQLDBManager database) {
        this.database = database;
    }

    @Override
    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS ravencore_homes ("
                + "player_uuid CHAR(36) NOT NULL, home_name VARCHAR(32) NOT NULL,"
                + "server_id VARCHAR(64) NOT NULL, world VARCHAR(64) NOT NULL,"
                + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,"
                + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (player_uuid, home_name), INDEX idx_home_server (server_id))";
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create ravencore_homes table", e);
        }
    }

    @Override
    public void dropTable() {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS ravencore_homes");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to drop ravencore_homes table", e);
        }
    }
}
