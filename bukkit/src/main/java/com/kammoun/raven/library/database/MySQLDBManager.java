package com.kammoun.raven.library.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MySQLDBManager extends KDBManager {

    private HikariDataSource dataSource;

    public MySQLDBManager(FileConfiguration config) {
        super(config);
    }

    @Override
    protected void loadDatabaseInfo(FileConfiguration config) {
        this.dbHost = config.getString("database.host", "localhost");
        this.dbPort = config.getString("database.port", "3306");
        this.dbName = config.getString("database.name", "minecraft");
        this.dbUser = config.getString("database.user", "root");
        this.dbPassword = config.getString("database.password", "");
    }

    @Override
    public void connect() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                + "?useSSL=false&autoReconnect=true&characterEncoding=utf8");
        hikariConfig.setUsername(dbUser);
        hikariConfig.setPassword(dbPassword);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30_000);
        hikariConfig.setIdleTimeout(600_000);
        hikariConfig.setMaxLifetime(1_800_000);
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
            throw new RuntimeException("Failed to acquire connection from pool", e);
        }
    }


    @Override
    public PreparedStatement getPreparedStatement(String sql) throws SQLException {
        if (!isConnected() || sql == null || sql.isEmpty()) return null;
        return dataSource.getConnection().prepareStatement(sql);
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }
}
