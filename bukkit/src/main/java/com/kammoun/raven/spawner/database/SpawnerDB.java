package com.kammoun.raven.spawner.database;

import com.kammoun.raven.library.database.RavenModuleDatabase;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.spawner.manager.SpawnerRegistry;
import com.kammoun.raven.spawner.manager.SpawnerStackManager;
import com.kammoun.raven.spawner.model.SpawnerStack;
import com.kammoun.raven.spawner.model.SpawnerType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnerDB {

    private final RavenModuleDatabase db;
    private final KModule<?> module;
    private final SpawnerRegistry registry;
    private final SpawnerStackManager stacks;

    public SpawnerDB(KModule<?> module, SpawnerRegistry registry, SpawnerStackManager stacks) throws SQLException {
        this.module = module;
        this.registry = registry;
        this.stacks = stacks;
        this.db = new RavenModuleDatabase(module);
        db.connect(this::onCreate);
    }

    private void onCreate(Connection conn) throws SQLException {
        conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS spawner_stacks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "world TEXT NOT NULL," +
                "x INTEGER NOT NULL," +
                "y INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "stack_amount INTEGER DEFAULT 1," +
                "stored_xp INTEGER DEFAULT 0," +
                "owner_uuid TEXT," +
                "last_spawn BIGINT DEFAULT 0," +
                "UNIQUE(world, x, y, z)" +
                ")"
        ).executeUpdate();

        conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS spawner_items (" +
                "stack_id INTEGER NOT NULL," +
                "material TEXT NOT NULL," +
                "amount INTEGER NOT NULL," +
                "PRIMARY KEY (stack_id, material)," +
                "FOREIGN KEY (stack_id) REFERENCES spawner_stacks(id) ON DELETE CASCADE" +
                ")"
        ).executeUpdate();
    }

    // ─── Load ────────────────────────────────────────────────────────────────

    public void loadAll() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM spawner_stacks");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String world  = rs.getString("world");
                int x = rs.getInt("x"), y = rs.getInt("y"), z = rs.getInt("z");
                String typeId = rs.getString("type");
                int stackAmt  = rs.getInt("stack_amount");
                int xp        = rs.getInt("stored_xp");
                long lastSpawn = rs.getLong("last_spawn");
                String ownerStr = rs.getString("owner_uuid");
                int id = rs.getInt("id");

                SpawnerType type = registry.get(typeId);
                if (type == null || Bukkit.getWorld(world) == null) {
                    module.getLogger().warning("[SpawnerDB] Skipping unknown type/world: " + typeId + " @ " + world);
                    continue;
                }

                Map<Material, Integer> items = loadItems(conn, id);
                Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                UUID owner = ownerStr != null ? safeUUID(ownerStr) : null;

                SpawnerStack stack = new SpawnerStack(loc, type, stackAmt, items, xp, lastSpawn, owner);
                stacks.put(stack);
            }
        } catch (SQLException e) {
            module.getLogger().severe("[SpawnerDB] Failed to load spawners: " + e.getMessage());
        }
    }

    private Map<Material, Integer> loadItems(Connection conn, int stackId) throws SQLException {
        Map<Material, Integer> result = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT material, amount FROM spawner_items WHERE stack_id = ?")) {
            ps.setInt(1, stackId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.put(Material.valueOf(rs.getString("material")), rs.getInt("amount"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return result;
    }

    // ─── Save ────────────────────────────────────────────────────────────────

    /** Saves only stacks that have been mutated since the last save. */
    public void saveDirty() {
        for (SpawnerStack stack : stacks.all()) {
            if (!stack.isDirty()) continue;
            try {
                save(stack);
                stack.clearDirty();
            } catch (SQLException e) {
                module.getLogger().warning("[SpawnerDB] Failed to save spawner: " + e.getMessage());
            }
        }
    }

    /** Saves all stacks unconditionally (used on shutdown). */
    public void saveAll() {
        for (SpawnerStack stack : stacks.all()) {
            try {
                save(stack);
                stack.clearDirty();
            } catch (SQLException e) {
                module.getLogger().warning("[SpawnerDB] Failed to save spawner on shutdown: " + e.getMessage());
            }
        }
    }

    private void save(SpawnerStack stack) throws SQLException {
        try (Connection conn = db.getConnection()) {
            Location loc = stack.getLocation();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO spawner_stacks " +
                    "(world, x, y, z, type, stack_amount, stored_xp, owner_uuid, last_spawn) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setString(5, stack.getType().getId());
                ps.setInt(6, stack.getStackAmount());
                ps.setInt(7, stack.getStoredXp());
                ps.setString(8, stack.getOwnerUUID() != null ? stack.getOwnerUUID().toString() : null);
                ps.setLong(9, stack.getLastSpawnTime());
                ps.executeUpdate();
            }

            int id = fetchId(conn, loc);
            if (id != -1) saveItems(conn, id, stack.getStoredItems());
        }
    }

    private void saveItems(Connection conn, int stackId, Map<Material, Integer> items) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM spawner_items WHERE stack_id = ?")) {
            del.setInt(1, stackId);
            del.executeUpdate();
        }
        if (items.isEmpty()) return;
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO spawner_items (stack_id, material, amount) VALUES (?, ?, ?)")) {
            for (Map.Entry<Material, Integer> e : items.entrySet()) {
                ins.setInt(1, stackId);
                ins.setString(2, e.getKey().name());
                ins.setInt(3, e.getValue());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    // ─── Delete ──────────────────────────────────────────────────────────────

    public void delete(Location loc) {
        try (Connection conn = db.getConnection()) {
            int id = fetchId(conn, loc);
            if (id == -1) return;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM spawner_stacks WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            module.getLogger().warning("[SpawnerDB] Failed to delete spawner: " + e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int fetchId(Connection conn, Location loc) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM spawner_stacks WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : -1;
            }
        }
    }

    private UUID safeUUID(String str) {
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            module.getLogger().warning("[SpawnerDB] Invalid UUID in database: " + str);
            return null;
        }
    }

    public void close() {
        db.close();
    }
}
