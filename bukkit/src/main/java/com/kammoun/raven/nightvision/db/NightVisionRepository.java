package com.kammoun.raven.nightvision.db;

import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NightVisionRepository {

    private static final String CREATE = """
            CREATE TABLE IF NOT EXISTS raven_nightvision (
              uuid VARCHAR(36) PRIMARY KEY,
              enabled BOOLEAN NOT NULL DEFAULT TRUE
            )
            """;

    private static final String LOAD_ALL = "SELECT uuid FROM raven_nightvision WHERE enabled = TRUE";

    private static final String UPSERT = """
            INSERT INTO raven_nightvision (uuid, enabled) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)
            """;

    private final MySQLDBManager db;

    public NightVisionRepository(MySQLDBManager db) {
        this.db = db;
    }

    public void init() throws SQLException {
        try (Connection conn = db.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(CREATE);
        }
    }

    public Set<UUID> loadEnabled() throws SQLException {
        Set<UUID> result = new HashSet<>();
        try (Connection conn = db.getConnection();
             PreparedStatement st = conn.prepareStatement(LOAD_ALL);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) result.add(UUID.fromString(rs.getString("uuid")));
        }
        return result;
    }

    public void save(UUID uuid, boolean enabled) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement st = conn.prepareStatement(UPSERT)) {
            st.setString(1, uuid.toString());
            st.setBoolean(2, enabled);
            st.executeUpdate();
        }
    }
}
