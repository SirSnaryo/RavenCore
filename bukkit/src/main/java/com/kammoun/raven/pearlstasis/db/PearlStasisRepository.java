package com.kammoun.raven.pearlstasis.db;

import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.library.pearlstasis.PearlStasisRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

public final class PearlStasisRepository {

    private final MySQLDBManager database;

    public PearlStasisRepository(MySQLDBManager database) {
        this.database = database;
    }

    public long savePending(PearlStasisRecord record) throws SQLException {
        String sql = "INSERT INTO ravencore_pearl_stasis_pending "
                + "(player_uuid, target_server, world, x, y, z, yaw, pitch, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, record.playerUuid().toString());
            statement.setString(2, record.targetServer());
            statement.setString(3, record.world());
            statement.setDouble(4, record.x());
            statement.setDouble(5, record.y());
            statement.setDouble(6, record.z());
            statement.setFloat(7, record.yaw());
            statement.setFloat(8, record.pitch());
            statement.setTimestamp(9, new Timestamp(record.createdAt() > 0 ? record.createdAt() : System.currentTimeMillis()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return 0L;
    }

    public Optional<PearlStasisRecord> consumeOldestPending(UUID playerUuid, String targetServer, long maxAgeMillis) throws SQLException {
        String selectSql = "SELECT id, player_uuid, target_server, world, x, y, z, yaw, pitch, created_at "
                + "FROM ravencore_pearl_stasis_pending "
                + "WHERE player_uuid = ? AND target_server = ? "
                + (maxAgeMillis > 0 ? "AND created_at >= ? " : "")
                + "ORDER BY id ASC LIMIT 1";

        try (Connection connection = database.getConnection();
             PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
            selectStatement.setString(1, playerUuid.toString());
            selectStatement.setString(2, targetServer);
            if (maxAgeMillis > 0) {
                selectStatement.setTimestamp(3, new Timestamp(System.currentTimeMillis() - maxAgeMillis));
            }

            long recordId = -1L;
            PearlStasisRecord record = null;
            try (ResultSet result = selectStatement.executeQuery()) {
                if (result.next()) {
                    recordId = result.getLong("id");
                    record = new PearlStasisRecord(
                            recordId,
                            playerUuid,
                            result.getString("target_server"),
                            result.getString("world"),
                            result.getDouble("x"),
                            result.getDouble("y"),
                            result.getDouble("z"),
                            result.getFloat("yaw"),
                            result.getFloat("pitch"),
                            result.getTimestamp("created_at").getTime()
                    );
                }
            }

            if (record != null && recordId > 0) {
                String deleteSql = "DELETE FROM ravencore_pearl_stasis_pending WHERE id = ?";
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setLong(1, recordId);
                    deleteStatement.executeUpdate();
                }
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public boolean hasPending(UUID playerUuid, long maxAgeMillis) throws SQLException {
        String sql = "SELECT 1 FROM ravencore_pearl_stasis_pending WHERE player_uuid = ? "
                + (maxAgeMillis > 0 ? "AND created_at >= ? " : "")
                + "LIMIT 1";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            if (maxAgeMillis > 0) {
                statement.setTimestamp(2, new Timestamp(System.currentTimeMillis() - maxAgeMillis));
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public int countPending(UUID playerUuid, String targetServer, long maxAgeMillis) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ravencore_pearl_stasis_pending WHERE player_uuid = ? AND target_server = ? "
                + (maxAgeMillis > 0 ? "AND created_at >= ?" : "");
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, targetServer);
            if (maxAgeMillis > 0) {
                statement.setTimestamp(3, new Timestamp(System.currentTimeMillis() - maxAgeMillis));
            }
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt(1);
                }
            }
        }
        return 0;
    }

    public int deleteExpired(long maxAgeMillis) throws SQLException {
        if (maxAgeMillis <= 0) {
            return 0;
        }
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - maxAgeMillis);
        String sql = "DELETE FROM ravencore_pearl_stasis_pending WHERE created_at < ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, cutoff);
            return statement.executeUpdate();
        }
    }
}
