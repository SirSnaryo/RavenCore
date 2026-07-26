package com.kammoun.raven.billford.db;

import com.kammoun.raven.billford.model.BillfordPreset;
import com.kammoun.raven.library.database.MySQLDBManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All Billford MySQL access. Presets are read/written wholesale; rotation state is
 * advanced with an atomic compare-and-set ({@link #tryAdvance}) so that when several
 * servers hit the rotation deadline at once, exactly one wins and the rest resync.
 */
public class BillfordRepository {

    public record State(String currentPreset, long lastChange,
                        long rotationIntervalMillis, long version) {}

    private final MySQLDBManager db;
    private final JavaPlugin plugin;

    public BillfordRepository(MySQLDBManager db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    // ─── Presets ───────────────────────────────────────────────────────────────

    public List<BillfordPreset> findAllPresets() throws SQLException {
        List<BillfordPreset> results = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT name, enabled, data FROM ravencore_billford_presets");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(BillfordPreset.deserialize(
                        rs.getString("name"), rs.getBoolean("enabled"), rs.getString("data"), plugin));
            }
        }
        return results;
    }

    public void upsertPreset(BillfordPreset preset) throws SQLException {
        String sql = "INSERT INTO ravencore_billford_presets (name, enabled, data) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), data = VALUES(data)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, preset.getName());
            ps.setBoolean(2, preset.isEnabled());
            ps.setString(3, preset.serializeData());
            ps.executeUpdate();
        }
    }

    public boolean deletePreset(String name) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ravencore_billford_presets WHERE name = ?")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        }
    }

    // ─── Rotation state ──────────────────────────────────────────────────────────

    public State loadState() throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT current_preset, last_change, rotation_interval, state_version "
                             + "FROM ravencore_billford_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new State(rs.getString("current_preset"), rs.getLong("last_change"),
                        rs.getLong("rotation_interval"), rs.getLong("state_version"));
            }
        }
        return new State(null, 0L, 0L, 0L);
    }

    /** Unconditional state write, used by an explicit admin switch (last writer wins). */
    public State setState(String currentPreset, long lastChange) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ravencore_billford_state SET current_preset = ?, last_change = ?, "
                             + "state_version = state_version + 1 WHERE id = 1")) {
            ps.setString(1, currentPreset);
            ps.setLong(2, lastChange);
            if (ps.executeUpdate() != 1) throw new SQLException("Billford state row is missing");
        }
        return loadState();
    }

    public State setRotationInterval(long intervalMillis) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ravencore_billford_state SET rotation_interval = ?, "
                             + "state_version = state_version + 1 WHERE id = 1")) {
            ps.setLong(1, intervalMillis);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Billford state row is missing");
            }
        }
        return loadState();
    }

    /**
     * Compare-and-set used by the rotation task: advances to {@code newPreset} only if the
     * row still carries {@code expectedLastChange}. Returns the resulting authoritative state
     * if this call won the race.
     */
    public Optional<State> tryAdvance(String newPreset, long expectedLastChange,
                                      long newLastChange) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ravencore_billford_state SET current_preset = ?, last_change = ?, "
                             + "state_version = state_version + 1 WHERE id = 1 AND last_change = ?")) {
            ps.setString(1, newPreset);
            ps.setLong(2, newLastChange);
            ps.setLong(3, expectedLastChange);
            if (ps.executeUpdate() != 1) return Optional.empty();
        }
        return Optional.of(loadState());
    }
}
