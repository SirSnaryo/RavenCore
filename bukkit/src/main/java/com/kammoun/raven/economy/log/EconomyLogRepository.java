package com.kammoun.raven.economy.log;

import com.kammoun.raven.economy.model.EconomyTransactionLog;
import com.kammoun.raven.economy.model.EconomyTransactionType;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EconomyLogRepository {

    private static final String CREATE = """
            CREATE TABLE IF NOT EXISTS ravencore_economy_transactions (
              transaction_id VARCHAR(64) PRIMARY KEY,
              type VARCHAR(32) NOT NULL,
              target_uuid VARCHAR(36),
              target_name VARCHAR(32),
              actor_uuid VARCHAR(36),
              actor_name VARCHAR(32),
              amount DOUBLE NOT NULL,
              balance_before DOUBLE NOT NULL,
              balance_after DOUBLE NOT NULL,
              reason VARCHAR(255) NOT NULL,
              raw_command VARCHAR(255) NOT NULL,
              server_id VARCHAR(64) NOT NULL,
              server_display_name VARCHAR(64) NOT NULL,
              success BOOLEAN NOT NULL,
              failure_reason VARCHAR(128),
              created_at BIGINT NOT NULL,
              INDEX idx_created_at (created_at),
              INDEX idx_amount (amount),
              INDEX idx_target_uuid (target_uuid),
              INDEX idx_actor_uuid (actor_uuid),
              INDEX idx_type (type)
            )
            """;

    private static final String INSERT = """
            INSERT IGNORE INTO ravencore_economy_transactions
              (transaction_id, type, target_uuid, target_name, actor_uuid, actor_name, amount,
               balance_before, balance_after, reason, raw_command, server_id, server_display_name,
               success, failure_reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final MySQLDBManager db;

    public EconomyLogRepository(MySQLDBManager db) {
        this.db = db;
    }

    public void init() {
        try (Connection connection = db.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create economy transaction table", e);
        }
    }

    public void insert(EconomyTransactionLog log) {
        try (Connection connection = db.getConnection(); PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, log.transactionId());
            statement.setString(2, log.type().name());
            statement.setString(3, string(log.targetUuid()));
            statement.setString(4, log.targetName());
            statement.setString(5, string(log.actorUuid()));
            statement.setString(6, log.actorName());
            statement.setDouble(7, log.amount());
            statement.setDouble(8, log.balanceBefore());
            statement.setDouble(9, log.balanceAfter());
            statement.setString(10, trim(log.reason(), 255));
            statement.setString(11, trim(log.rawCommand(), 255));
            statement.setString(12, log.serverId());
            statement.setString(13, log.serverDisplayName());
            statement.setBoolean(14, log.success());
            statement.setString(15, trim(log.failureReason(), 128));
            statement.setLong(16, log.createdAt());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to insert economy transaction log", e);
        }
    }

    public List<EconomyTransactionLog> latest(int limit, boolean biggest) {
        String order = biggest ? "ABS(amount) DESC, created_at DESC" : "created_at DESC";
        String sql = "SELECT * FROM ravencore_economy_transactions ORDER BY " + order + " LIMIT ?";
        try (Connection connection = db.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(54, limit)));
            try (ResultSet rs = statement.executeQuery()) {
                List<EconomyTransactionLog> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(read(rs));
                }
                return logs;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to query economy transaction logs", e);
        }
    }

    public List<EconomyTransactionLog> byPlayer(UUID uuid, int limit) {
        String sql = """
                SELECT * FROM ravencore_economy_transactions
                WHERE target_uuid = ? OR actor_uuid = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        try (Connection connection = db.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, uuid.toString());
            statement.setInt(3, Math.max(1, Math.min(54, limit)));
            try (ResultSet rs = statement.executeQuery()) {
                List<EconomyTransactionLog> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(read(rs));
                }
                return logs;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to query player economy logs", e);
        }
    }

    private EconomyTransactionLog read(ResultSet rs) throws Exception {
        return new EconomyTransactionLog(
                rs.getString("transaction_id"),
                EconomyTransactionType.valueOf(rs.getString("type")),
                uuid(rs.getString("target_uuid")),
                rs.getString("target_name"),
                uuid(rs.getString("actor_uuid")),
                rs.getString("actor_name"),
                rs.getDouble("amount"),
                rs.getDouble("balance_before"),
                rs.getDouble("balance_after"),
                rs.getString("reason"),
                rs.getString("raw_command"),
                rs.getString("server_id"),
                rs.getString("server_display_name"),
                rs.getBoolean("success"),
                rs.getString("failure_reason"),
                rs.getLong("created_at")
        );
    }

    private static String string(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
