package com.kammoun.raven.pearlstasis.db;

import com.kammoun.raven.library.database.KTables;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class PearlStasisTable implements KTables {

    private final MySQLDBManager database;

    public PearlStasisTable(MySQLDBManager database) {
        this.database = database;
    }

    @Override
    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS ravencore_pearl_stasis_pending ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "player_uuid CHAR(36) NOT NULL,"
                + "target_server VARCHAR(64) NOT NULL,"
                + "world VARCHAR(64) NOT NULL,"
                + "x DOUBLE NOT NULL,"
                + "y DOUBLE NOT NULL,"
                + "z DOUBLE NOT NULL,"
                + "yaw FLOAT NOT NULL,"
                + "pitch FLOAT NOT NULL,"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "INDEX idx_player_server (player_uuid, target_server),"
                + "INDEX idx_created_at (created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create ravencore_pearl_stasis_pending table", e);
        }
    }

    @Override
    public void dropTable() {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS ravencore_pearl_stasis_pending");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to drop ravencore_pearl_stasis_pending table", e);
        }
    }
}
