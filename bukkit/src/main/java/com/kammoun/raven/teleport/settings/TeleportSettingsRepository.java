package com.kammoun.raven.teleport.settings;

import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.teleport.model.TeleportSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class TeleportSettingsRepository {

    private static final String CREATE = """
            CREATE TABLE IF NOT EXISTS raven_teleport_settings (
              uuid VARCHAR(36) PRIMARY KEY,
              accepts_tpa BOOLEAN NOT NULL,
              accepts_tpahere BOOLEAN NOT NULL,
              auto_accept BOOLEAN NOT NULL,
              confirm_menu BOOLEAN NOT NULL DEFAULT TRUE,
              updated_at BIGINT NOT NULL
            )
            """;

    private static final String SELECT = """
            SELECT accepts_tpa, accepts_tpahere, auto_accept, confirm_menu
            FROM raven_teleport_settings
            WHERE uuid = ?
            """;

    private static final String UPSERT = """
            INSERT INTO raven_teleport_settings
              (uuid, accepts_tpa, accepts_tpahere, auto_accept, confirm_menu, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              accepts_tpa = VALUES(accepts_tpa),
              accepts_tpahere = VALUES(accepts_tpahere),
              auto_accept = VALUES(auto_accept),
              confirm_menu = VALUES(confirm_menu),
              updated_at = VALUES(updated_at)
            """;

    private final MySQLDBManager db;

    public TeleportSettingsRepository(MySQLDBManager db) {
        this.db = db;
    }

    public void init() throws SQLException {
        try (Connection connection = db.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE);
            ensureConfirmMenuColumn(connection);
        }
    }

    public Optional<TeleportSettings> load(UUID uuid) throws SQLException {
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new TeleportSettings(
                        uuid,
                        rs.getBoolean("accepts_tpa"),
                        rs.getBoolean("accepts_tpahere"),
                        rs.getBoolean("auto_accept"),
                        rs.getBoolean("confirm_menu")
                ));
            }
        }
    }

    public void save(TeleportSettings settings) throws SQLException {
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            bind(statement, settings);
            statement.executeUpdate();
        }
    }

    public void saveAll(Collection<TeleportSettings> settings) throws SQLException {
        if (settings.isEmpty()) return;
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            connection.setAutoCommit(false);
            try {
                for (TeleportSettings setting : settings) {
                    bind(statement, setting);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void bind(PreparedStatement statement, TeleportSettings settings) throws SQLException {
        statement.setString(1, settings.uuid().toString());
        statement.setBoolean(2, settings.acceptsTpa());
        statement.setBoolean(3, settings.acceptsTpaHere());
        statement.setBoolean(4, settings.autoAccept());
        statement.setBoolean(5, settings.confirmMenu());
        statement.setLong(6, System.currentTimeMillis());
    }

    private void ensureConfirmMenuColumn(Connection connection) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "raven_teleport_settings", "confirm_menu")) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE raven_teleport_settings ADD COLUMN confirm_menu BOOLEAN NOT NULL DEFAULT TRUE");
        }
    }
}
