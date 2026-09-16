package com.kammoun.raven.punishment.db;

import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.punishment.model.PunishmentRecord;
import com.kammoun.raven.punishment.model.PunishmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PunishmentRepository {

    private final MySQLDBManager database;

    public PunishmentRepository(MySQLDBManager database) {
        this.database = database;
    }

    public void createTable() throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS ravencore_punishments (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         target_uuid VARCHAR(36) NULL,
                         target_name VARCHAR(16) NOT NULL,
                         type VARCHAR(16) NOT NULL,
                         actor VARCHAR(64) NOT NULL,
                         reason VARCHAR(255) NOT NULL,
                         created_at BIGINT NOT NULL,
                         expires_at BIGINT NULL,
                         active BOOLEAN NOT NULL DEFAULT TRUE,
                         removed_by VARCHAR(64) NULL,
                         removed_at BIGINT NULL,
                         INDEX idx_target_type (target_uuid, target_name, type, active),
                         INDEX idx_active_type (type, active, expires_at)
                     )
                     """)) {
            statement.executeUpdate();
        }
    }

    public PunishmentRecord create(UUID targetUuid, String targetName, PunishmentType type,
                                   String actor, String reason, Long expiresAt) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ravencore_punishments
                     (target_uuid, target_name, type, actor, reason, created_at, expires_at, active)
                     VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                     """, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, targetUuid == null ? null : targetUuid.toString());
            statement.setString(2, targetName);
            statement.setString(3, type.name());
            statement.setString(4, actor);
            statement.setString(5, reason);
            statement.setLong(6, now);
            if (expiresAt == null) statement.setObject(7, null);
            else statement.setLong(7, expiresAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1L;
                return new PunishmentRecord(id, targetUuid, targetName, type, actor, reason, now, expiresAt);
            }
        }
    }

    public Optional<PunishmentRecord> findActive(UUID targetUuid, String targetName, PunishmentType type) throws SQLException {
        long now = System.currentTimeMillis();
        String sql = """
                SELECT id, target_uuid, target_name, type, actor, reason, created_at, expires_at
                FROM ravencore_punishments
                WHERE active = TRUE
                  AND type = ?
                  AND (expires_at IS NULL OR expires_at > ?)
                  AND (%s)
                ORDER BY created_at DESC
                LIMIT 1
                """.formatted(targetUuid == null ? "LOWER(target_name) = LOWER(?)" : "(target_uuid = ? OR LOWER(target_name) = LOWER(?))");
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setLong(2, now);
            if (targetUuid == null) {
                statement.setString(3, targetName);
            } else {
                statement.setString(3, targetUuid.toString());
                statement.setString(4, targetName);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    public boolean deactivate(UUID targetUuid, String targetName, PunishmentType type, String actor) throws SQLException {
        String sql = """
                UPDATE ravencore_punishments
                SET active = FALSE, removed_by = ?, removed_at = ?
                WHERE active = TRUE
                  AND type = ?
                  AND (%s)
                """.formatted(targetUuid == null ? "LOWER(target_name) = LOWER(?)" : "(target_uuid = ? OR LOWER(target_name) = LOWER(?))");
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actor);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, type.name());
            if (targetUuid == null) {
                statement.setString(4, targetName);
            } else {
                statement.setString(4, targetUuid.toString());
                statement.setString(5, targetName);
            }
            return statement.executeUpdate() > 0;
        }
    }

    public List<PunishmentRecord> history(String targetName, int limit) throws SQLException {
        List<PunishmentRecord> records = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, target_uuid, target_name, type, actor, reason, created_at, expires_at
                     FROM ravencore_punishments
                     WHERE LOWER(target_name) = LOWER(?)
                     ORDER BY created_at DESC
                     LIMIT ?
                     """)) {
            statement.setString(1, targetName);
            statement.setInt(2, Math.max(1, Math.min(25, limit)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(read(result));
                }
            }
        }
        return records;
    }

    private PunishmentRecord read(ResultSet result) throws SQLException {
        String uuid = result.getString("target_uuid");
        long expires = result.getLong("expires_at");
        boolean expiresNull = result.wasNull();
        return new PunishmentRecord(
                result.getLong("id"),
                uuid == null ? null : UUID.fromString(uuid),
                result.getString("target_name"),
                PunishmentType.valueOf(result.getString("type")),
                result.getString("actor"),
                result.getString("reason"),
                result.getLong("created_at"),
                expiresNull ? null : expires
        );
    }
}
