package com.kammoun.raven.home.db;

import com.kammoun.raven.home.model.HomeRecord;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class HomeRepository {
    private final MySQLDBManager database;

    public HomeRepository(MySQLDBManager database) {
        this.database = database;
    }

    public void upsert(HomeRecord home) throws SQLException {
        String sql = "INSERT INTO ravencore_homes "
                + "(player_uuid, home_name, server_id, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE server_id=VALUES(server_id), world=VALUES(world), x=VALUES(x), "
                + "y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, home);
            statement.executeUpdate();
        }
    }

    public Optional<HomeRecord> find(UUID playerUuid, String name) throws SQLException {
        String sql = "SELECT server_id, world, x, y, z, yaw, pitch FROM ravencore_homes "
                + "WHERE player_uuid=? AND home_name=?";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(playerUuid, name, result)) : Optional.empty();
            }
        }
    }

    public Optional<HomeRecord> findFirst(UUID playerUuid) throws SQLException {
        String sql = "SELECT home_name, server_id, world, x, y, z, yaw, pitch FROM ravencore_homes "
                + "WHERE player_uuid=? ORDER BY home_name LIMIT 1";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(read(playerUuid, result.getString("home_name"), result))
                        : Optional.empty();
            }
        }
    }

    public List<HomeRecord> findAll(UUID playerUuid) throws SQLException {
        List<HomeRecord> homes = new ArrayList<>();
        String sql = "SELECT home_name, server_id, world, x, y, z, yaw, pitch FROM ravencore_homes "
                + "WHERE player_uuid=? ORDER BY home_name";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) homes.add(read(playerUuid, result.getString("home_name"), result));
            }
        }
        return homes;
    }

    public int count(UUID playerUuid) throws SQLException {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM ravencore_homes WHERE player_uuid=?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public boolean delete(UUID playerUuid, String name) throws SQLException {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ravencore_homes WHERE player_uuid=? AND home_name=?")) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        }
    }

    private static void bind(PreparedStatement statement, HomeRecord home) throws SQLException {
        statement.setString(1, home.playerUuid().toString());
        statement.setString(2, home.name());
        statement.setString(3, home.serverId());
        statement.setString(4, home.world());
        statement.setDouble(5, home.x());
        statement.setDouble(6, home.y());
        statement.setDouble(7, home.z());
        statement.setFloat(8, home.yaw());
        statement.setFloat(9, home.pitch());
    }

    private static HomeRecord read(UUID uuid, String name, ResultSet result) throws SQLException {
        return new HomeRecord(uuid, name, result.getString("server_id"), result.getString("world"),
                result.getDouble("x"), result.getDouble("y"), result.getDouble("z"),
                result.getFloat("yaw"), result.getFloat("pitch"));
    }
}
