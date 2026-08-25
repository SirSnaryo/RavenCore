package com.kammoun.raven.crates.db;

import com.kammoun.raven.crates.model.CrateLocation;
import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class CrateLocationRepository {
    private final MySQLDBManager database;
    private final String serverId;

    public CrateLocationRepository(MySQLDBManager database, String serverId) {
        this.database = database;
        this.serverId = serverId;
    }

    public List<CrateLocation> loadAll() {
        List<CrateLocation> locations = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT world, x, y, z, crate_id
                     FROM ravencore_crate_locations
                     WHERE server_id = ?
                     """)) {
            statement.setString(1, serverId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    locations.add(new CrateLocation(rs.getString("world"), rs.getInt("x"), rs.getInt("y"),
                            rs.getInt("z"), rs.getString("crate_id")));
                }
            }
            return locations;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load crate locations", e);
        }
    }

    public void save(CrateLocation location) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ravencore_crate_locations (server_id, world, x, y, z, crate_id)
                     VALUES (?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE crate_id = VALUES(crate_id)
                     """)) {
            statement.setString(1, serverId);
            statement.setString(2, location.world());
            statement.setInt(3, location.x());
            statement.setInt(4, location.y());
            statement.setInt(5, location.z());
            statement.setString(6, location.crateId());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save crate location", e);
        }
    }

    public void delete(CrateLocation location) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM ravencore_crate_locations
                     WHERE server_id = ? AND world = ? AND x = ? AND y = ? AND z = ?
                     """)) {
            statement.setString(1, serverId);
            statement.setString(2, location.world());
            statement.setInt(3, location.x());
            statement.setInt(4, location.y());
            statement.setInt(5, location.z());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete crate location", e);
        }
    }
}
