package com.kammoun.raven.respawn.db;

import com.kammoun.raven.library.database.KTables;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class BedSpawnTable implements KTables {
    private final MySQLDBManager database;

    public BedSpawnTable(MySQLDBManager database) {
        this.database = database;
    }

    @Override
    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS ravencore_bed_spawns ("
                + "player_uuid CHAR(36) NOT NULL,"
                + "server_id VARCHAR(64) NOT NULL,"
                + "world VARCHAR(64) NOT NULL,"
                + "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (player_uuid),"
                + "INDEX idx_bed_location (server_id, world, x, y, z)"
                + ")";
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create ravencore_bed_spawns table", e);
        }
    }

    @Override
    public void dropTable() {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS ravencore_bed_spawns");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to drop ravencore_bed_spawns table", e);
        }
    }
}
