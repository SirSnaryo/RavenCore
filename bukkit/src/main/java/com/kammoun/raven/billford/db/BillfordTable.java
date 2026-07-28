package com.kammoun.raven.billford.db;

import com.kammoun.raven.library.database.KTables;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Network-wide Billford schema: {@code ravencore_billford_presets} holds every shared
 * trade preset, and {@code ravencore_billford_state} is a single row (id = 1) tracking
 * the active preset and when it last changed, so rotation stays in lock-step across servers.
 */
public class BillfordTable implements KTables {

    private final MySQLDBManager db;
    private final long bootstrapIntervalMillis;

    public BillfordTable(MySQLDBManager db, long bootstrapIntervalMillis) {
        this.db = db;
        this.bootstrapIntervalMillis = bootstrapIntervalMillis;
    }

    @Override
    public void createTable() {
        String presets = "CREATE TABLE IF NOT EXISTS ravencore_billford_presets (" +
                "name        VARCHAR(64)   NOT NULL," +
                "enabled     BOOLEAN       NOT NULL DEFAULT 1," +
                "data        MEDIUMTEXT    NOT NULL," +
                "updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (name)" +
                ")";
        String state = "CREATE TABLE IF NOT EXISTS ravencore_billford_state (" +
                "id              TINYINT      NOT NULL DEFAULT 1," +
                "current_preset  VARCHAR(64)  NULL," +
                "last_change     BIGINT       NOT NULL DEFAULT 0," +
                "rotation_interval BIGINT     NULL," +
                "state_version   BIGINT       NOT NULL DEFAULT 0," +
                "PRIMARY KEY (id)" +
                ")";
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(presets);
            s.executeUpdate(state);
            // First backend to initialize the network wins the bootstrap value. After this row
            // exists, the interval is changed only through the network-wide admin command.
            s.executeUpdate("INSERT IGNORE INTO ravencore_billford_state "
                    + "(id, current_preset, last_change, rotation_interval) VALUES "
                    + "(1, NULL, 0, " + bootstrapIntervalMillis + ")");
            s.executeUpdate("UPDATE ravencore_billford_state SET rotation_interval = "
                    + bootstrapIntervalMillis + " WHERE id = 1 AND rotation_interval IS NULL");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create Billford tables", e);
        }
    }

    @Override
    public void dropTable() {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS ravencore_billford_presets");
            s.executeUpdate("DROP TABLE IF EXISTS ravencore_billford_state");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to drop Billford tables", e);
        }
    }
}
