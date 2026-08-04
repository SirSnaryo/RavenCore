package com.kammoun.raven.spawn.db;

import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.library.location.NetworkLocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LocationRepository {

    private static final String UPSERT_SQL =
            "INSERT INTO ravencore_locations " +
            "(server_name, type, world, x, y, z, yaw, pitch, max_players, enabled) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            // max_players/enabled are intentionally omitted from this clause: their bound
            // values above only take effect on a brand-new row. On conflict (a re-run of
            // /setspawn or /setafk on an already-configured server) only the location
            // columns are refreshed, so an op's previously tuned max_players/enabled is
            // never silently reset back to the default.
            "ON DUPLICATE KEY UPDATE " +
            "world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), " +
            "yaw = VALUES(yaw), pitch = VALUES(pitch)";

    private static final String FIND_ALL_SQL =
            "SELECT server_name, type, world, x, y, z, yaw, pitch, max_players, enabled, updated_at " +
            "FROM ravencore_locations";

    private final MySQLDBManager db;

    public LocationRepository(MySQLDBManager db) {
        this.db = db;
    }

    public void upsert(NetworkLocation location, int defaultMaxPlayers) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, location.getServerName());
            ps.setString(2, location.getType().name());
            ps.setString(3, location.getWorld());
            ps.setDouble(4, location.getX());
            ps.setDouble(5, location.getY());
            ps.setDouble(6, location.getZ());
            ps.setFloat(7, location.getYaw());
            ps.setFloat(8, location.getPitch());
            ps.setInt(9, defaultMaxPlayers);
            ps.setBoolean(10, true);
            ps.executeUpdate();
        }
    }

    public List<NetworkLocation> findAll() throws SQLException {
        List<NetworkLocation> results = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp updatedAt = rs.getTimestamp("updated_at");
                results.add(NetworkLocation.builder()
                        .serverName(rs.getString("server_name"))
                        .type(LocationType.valueOf(rs.getString("type")))
                        .world(rs.getString("world"))
                        .x(rs.getDouble("x"))
                        .y(rs.getDouble("y"))
                        .z(rs.getDouble("z"))
                        .yaw(rs.getFloat("yaw"))
                        .pitch(rs.getFloat("pitch"))
                        .maxPlayers(rs.getInt("max_players"))
                        .enabled(rs.getBoolean("enabled"))
                        .updatedAt(updatedAt != null ? updatedAt.getTime() : 0L)
                        .build());
            }
        }
        return results;
    }

    public Optional<NetworkLocation> findSpawn(String preferredServer) throws SQLException {
        String server = preferredServer == null ? "" : preferredServer.trim();
        String sql = "SELECT server_name, type, world, x, y, z, yaw, pitch, max_players, enabled, updated_at "
                + "FROM ravencore_locations WHERE type=? AND enabled=1"
                + (server.isBlank() ? " ORDER BY server_name LIMIT 1" : " AND server_name=? LIMIT 1");
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, LocationType.SPAWN.name());
            if (!server.isBlank()) {
                ps.setString(2, server);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        }
    }

    private static NetworkLocation read(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return NetworkLocation.builder()
                .serverName(rs.getString("server_name"))
                .type(LocationType.valueOf(rs.getString("type")))
                .world(rs.getString("world"))
                .x(rs.getDouble("x"))
                .y(rs.getDouble("y"))
                .z(rs.getDouble("z"))
                .yaw(rs.getFloat("yaw"))
                .pitch(rs.getFloat("pitch"))
                .maxPlayers(rs.getInt("max_players"))
                .enabled(rs.getBoolean("enabled"))
                .updatedAt(updatedAt != null ? updatedAt.getTime() : 0L)
                .build();
    }
}
