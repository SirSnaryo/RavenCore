package com.kammoun.raven.crates.db;

import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.Statement;

public final class CrateTables {
    private final MySQLDBManager database;

    public CrateTables(MySQLDBManager database) {
        this.database = database;
    }

    public void create() {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ravencore_crate_keys (
                      uuid CHAR(36) NOT NULL,
                      crate_id VARCHAR(64) NOT NULL,
                      amount INT NOT NULL DEFAULT 0,
                      PRIMARY KEY (uuid, crate_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ravencore_crate_locations (
                      server_id VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x INT NOT NULL,
                      y INT NOT NULL,
                      z INT NOT NULL,
                      crate_id VARCHAR(64) NOT NULL,
                      PRIMARY KEY (server_id, world, x, y, z)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ravencore_crate_timer (
                      id VARCHAR(32) NOT NULL PRIMARY KEY,
                      next_reward_at BIGINT NOT NULL,
                      cycle_id BIGINT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ravencore_crate_cycle_claims (
                      uuid CHAR(36) NOT NULL,
                      cycle_id BIGINT NOT NULL,
                      PRIMARY KEY (uuid, cycle_id)
                    )
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create crate tables", e);
        }
    }
}
