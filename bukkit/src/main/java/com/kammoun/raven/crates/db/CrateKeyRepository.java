package com.kammoun.raven.crates.db;

import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CrateKeyRepository {
    private final MySQLDBManager database;

    public CrateKeyRepository(MySQLDBManager database) {
        this.database = database;
    }

    public int getKeys(UUID uuid, String crateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT amount FROM ravencore_crate_keys WHERE uuid = ? AND crate_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt("amount")) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read crate keys", e);
        }
    }

    public Map<String, Integer> getAllKeys(UUID uuid) {
        Map<String, Integer> keys = new HashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT crate_id, amount FROM ravencore_crate_keys WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    keys.put(rs.getString("crate_id").toLowerCase(), Math.max(0, rs.getInt("amount")));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read crate keys", e);
        }
        return keys;
    }

    public Map<UUID, Map<String, Integer>> getAllKeys(Collection<UUID> uuids) {
        Map<UUID, Map<String, Integer>> keys = new HashMap<>();
        if (uuids.isEmpty()) return keys;
        for (UUID uuid : uuids) keys.put(uuid, new HashMap<>());
        String placeholders = String.join(",", java.util.Collections.nCopies(uuids.size(), "?"));
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid, crate_id, amount FROM ravencore_crate_keys WHERE uuid IN (" + placeholders + ")")) {
            int index = 1;
            for (UUID uuid : uuids) statement.setString(index++, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Integer> player = keys.get(UUID.fromString(rs.getString("uuid")));
                    if (player == null) continue;
                    player.put(rs.getString("crate_id").toLowerCase(), Math.max(0, rs.getInt("amount")));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read crate keys", e);
        }
        return keys;
    }

    public void addKeys(UUID uuid, String crateId, int amount) {
        if (amount <= 0) return;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ravencore_crate_keys (uuid, crate_id, amount)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)
                     """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId);
            statement.setInt(3, amount);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to add crate keys", e);
        }
    }

    public boolean consumeKey(UUID uuid, String crateId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE ravencore_crate_keys
                     SET amount = amount - 1
                     WHERE uuid = ? AND crate_id = ? AND amount > 0
                     """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId);
            return statement.executeUpdate() == 1;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to consume crate key", e);
        }
    }

    public void setKeys(UUID uuid, String crateId, int amount) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ravencore_crate_keys (uuid, crate_id, amount)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE amount = VALUES(amount)
                     """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, crateId);
            statement.setInt(3, Math.max(0, amount));
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set crate keys", e);
        }
    }
}
