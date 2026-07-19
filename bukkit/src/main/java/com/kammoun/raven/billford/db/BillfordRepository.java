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

/**
 * All Billford MySQL access. Presets are read/written wholesale; rotation state is
 * advanced with an atomic compare-and-set ({@link #tryAdvance}) so that when several
 * servers hit the rotation deadline at once, exactly one wins and the rest resync.
 */
public class BillfordRepository {

    public record State(String currentPreset, long lastChange) {}

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
             PreparedStatement ps = c.prepareStatement("SELECT current_preset, last_change FROM ravencore_billford_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new State(rs.getString("current_preset"), rs.getLong("last_change"));
            }
        }
        return new State(null, 0L);
    }

    /** Unconditional state write, used by an explicit admin switch (last writer wins). */
    public void setState(String currentPreset, long lastChange) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ravencore_billford_state SET current_preset = ?, last_change = ? WHERE id = 1")) {
            ps.setString(1, currentPreset);
            ps.setLong(2, lastChange);
            ps.executeUpdate();
        }
    }

    /**
     * Compare-and-set used by the rotation task: advances to {@code newPreset} only if the
     * row still carries {@code expectedLastChange}. Returns true if this call won the race.
     */
    public boolean tryAdvance(String newPreset, long expectedLastChange, long newLastChange) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ravencore_billford_state SET current_preset = ?, last_change = ? WHERE id = 1 AND last_change = ?")) {
            ps.setString(1, newPreset);
            ps.setLong(2, newLastChange);
            ps.setLong(3, expectedLastChange);
            return ps.executeUpdate() == 1;
        }
    }
}
