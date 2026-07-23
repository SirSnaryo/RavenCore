package com.kammoun.raven.respawn.db;

import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.respawn.BedSpawnRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class BedSpawnRepository {
    private final MySQLDBManager database;

    public BedSpawnRepository(MySQLDBManager database) {
        this.database = database;
    }

    public void upsert(BedSpawnRecord record) throws SQLException {
        String sql = "INSERT INTO ravencore_bed_spawns (player_uuid, server_id, world, x, y, z) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "server_id = VALUES(server_id), world = VALUES(world), x = VALUES(x), "
                + "y = VALUES(y), z = VALUES(z)";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.playerUuid().toString());
            statement.setString(2, record.serverId());
            statement.setString(3, record.world());
            statement.setInt(4, record.x());
            statement.setInt(5, record.y());
            statement.setInt(6, record.z());
            statement.executeUpdate();
        }
    }

    public Optional<BedSpawnRecord> find(UUID playerUuid) throws SQLException {
        String sql = "SELECT server_id, world, x, y, z FROM ravencore_bed_spawns WHERE player_uuid = ?";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new BedSpawnRecord(playerUuid, result.getString("server_id"),
                        result.getString("world"), result.getInt("x"), result.getInt("y"), result.getInt("z")));
            }
        }
    }

    public void delete(UUID playerUuid) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM ravencore_bed_spawns WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    public int deleteAt(String serverId, String world, int x, int y, int z) throws SQLException {
        String sql = "DELETE FROM ravencore_bed_spawns WHERE server_id = ? AND world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, serverId);
            statement.setString(2, world);
            statement.setInt(3, x);
            statement.setInt(4, y);
            statement.setInt(5, z);
            return statement.executeUpdate();
        }
    }
}
