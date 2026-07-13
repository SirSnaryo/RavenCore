package com.kammoun.raven.library.database;

import com.kammoun.raven.library.module.KModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Per-module SQLite database, backed by its own connection pool under the module's data folder.
 * Unlike {@link SQLiteDBManager}, which points at one plugin-wide file, each module gets its own
 * {@code data.db} — used for module-local state that doesn't belong in the network-wide MySQL store.
 */
public class RavenModuleDatabase {

    private final KModule<?> module;
    private HikariDataSource dataSource;

    public RavenModuleDatabase(KModule<?> module) {
        this.module = module;
    }

    public void connect(SqlConsumer<Connection> onCreate) throws SQLException {
        module.getDataFolder().mkdirs();
        File dbFile = new File(module.getDataFolder(), "data.db");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite does not support concurrent writes
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName(module.getName() + "-pool");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection()) {
            onCreate.accept(conn);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
