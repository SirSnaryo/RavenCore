package com.kammoun.raven.spawn.db;

import com.kammoun.raven.library.database.KTables;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class LocationsTable implements KTables {

    private final MySQLDBManager db;

    public LocationsTable(MySQLDBManager db) {
        this.db = db;
    }

    @Override
    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS ravencore_locations (" +
                "server_name  VARCHAR(32)            NOT NULL," +
                "type         ENUM('SPAWN', 'AFK')   NOT NULL," +
                "world        VARCHAR(64)            NOT NULL," +
                "x            DOUBLE                 NOT NULL," +
                "y            DOUBLE                 NOT NULL," +
                "z            DOUBLE                 NOT NULL," +
                "yaw          FLOAT                  NOT NULL," +
                "pitch        FLOAT                  NOT NULL," +
                "max_players  INT                    NOT NULL DEFAULT 0," +
                "enabled      BOOLEAN                NOT NULL DEFAULT 1," +
                "updated_at   TIMESTAMP              NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (server_name, type)" +
                ")";
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create ravencore_locations table", e);
        }
    }

    @Override
    public void dropTable() {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS ravencore_locations");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to drop ravencore_locations table", e);
        }
    }
}
