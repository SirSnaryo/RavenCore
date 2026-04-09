package com.kammoun.raven.library.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SQLiteDBManager extends KDBManager {

    private final JavaPlugin plugin;
    @Getter
    private HikariDataSource dataSource;

    public SQLiteDBManager(JavaPlugin plugin, FileConfiguration config) {
        super(config);
        this.plugin = plugin;
    }

    @Override
    protected void loadDatabaseInfo(FileConfiguration config) {
        this.dbName = config.getString("database.file", "database.db");
    }

    @Override
    public void connect() {
        plugin.getDataFolder().mkdirs();
        File dbFile = new File(plugin.getDataFolder(), dbName);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikariConfig.setMaximumPoolSize(1); // SQLite does not support concurrent writes
        hikariConfig.setConnectionTestQuery("SELECT 1");
        dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Returns a connection from the pool. Caller must close it after use.
     */
    @Override
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to acquire SQLite connection", e);
        }
    }

    /**
     * Returns a PreparedStatement from a pooled connection.
     * Close the statement after use to return the connection to the pool.
     */
    @Override
    public PreparedStatement getPreparedStatement(String sql) throws SQLException {
        if (!isConnected() || sql == null || sql.isEmpty()) return null;
        return dataSource.getConnection().prepareStatement(sql);
    }

}
