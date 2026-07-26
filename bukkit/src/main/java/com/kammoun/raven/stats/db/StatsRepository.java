package com.kammoun.raven.stats.db;

import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.stats.model.StatsDelta;
import com.kammoun.raven.stats.model.StatsSnapshot;
import com.kammoun.raven.stats.model.StatsType;
import com.kammoun.raven.stats.model.StatsWrite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class StatsRepository {
    private static final String SELECT = "SELECT * FROM ravencore_player_stats";
    private final MySQLDBManager database;

    public StatsRepository(MySQLDBManager database) {
        this.database = database;
    }

    public StatsSnapshot loadOrCreate(UUID uuid, String playerName) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO ravencore_player_stats (uuid, player_name) VALUES (?, ?) "
                             + "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.executeUpdate();
        }
        return findByUuid(uuid).orElseGet(() -> StatsSnapshot.empty(uuid, playerName));
    }

    public Optional<StatsSnapshot> findByUuid(UUID uuid) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    public Optional<StatsSnapshot> findByName(String name) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT + " WHERE LOWER(player_name) = LOWER(?) LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    public void applyDelta(UUID uuid, String name, StatsDelta delta) throws SQLException {
        if (delta.isEmpty()) return;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(deltaSql())) {
            bindDelta(statement, uuid, name, delta);
            statement.executeUpdate();
        }
    }

    /**
     * Writes every drained player delta through one connection and transaction.
     * Chunked executeBatch calls bound packet/memory size; rollback guarantees
     * that restoring all drained deltas cannot double-apply a partial batch.
     */
    public void applyDeltas(List<StatsWrite> writes, int batchSize) throws SQLException {
        if (writes.isEmpty()) return;
        try (Connection connection = database.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(deltaSql())) {
                int pendingBatch = 0;
                for (StatsWrite write : writes) {
                    bindDelta(statement, write.uuid(), write.playerName(), write.delta());
                    statement.addBatch();
                    if (++pendingBatch >= batchSize) {
                        statement.executeBatch();
                        statement.clearBatch();
                        pendingBatch = 0;
                    }
                }
                if (pendingBatch > 0) statement.executeBatch();
                connection.commit();
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /**
     * Reloads all local players using chunked IN queries on one connection,
     * replacing the previous one-query-per-player refresh.
     */
    public Map<UUID, StatsSnapshot> findByUuids(List<UUID> uuids, int batchSize) throws SQLException {
        Map<UUID, StatsSnapshot> results = new HashMap<>(uuids.size());
        if (uuids.isEmpty()) return results;
        try (Connection connection = database.getConnection()) {
            for (int start = 0; start < uuids.size(); start += batchSize) {
                List<UUID> chunk = uuids.subList(start, Math.min(uuids.size(), start + batchSize));
                String markers = chunk.stream().map(ignored -> "?").collect(Collectors.joining(","));
                try (PreparedStatement statement = connection.prepareStatement(
                        SELECT + " WHERE uuid IN (" + markers + ")")) {
                    for (int index = 0; index < chunk.size(); index++) {
                        statement.setString(index + 1, chunk.get(index).toString());
                    }
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            StatsSnapshot snapshot = read(result);
                            results.put(snapshot.uuid(), snapshot);
                        }
                    }
                }
            }
        }
        return results;
    }

    private String deltaSql() {
        return "INSERT INTO ravencore_player_stats (uuid, player_name, kills, deaths,"
                + "blocks_broken, blocks_placed, mob_kills, play_time, items_crafted, jumps,"
                + "chat_messages, money_spent, money_made) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name),"
                + "kills=kills+VALUES(kills), deaths=deaths+VALUES(deaths),"
                + "blocks_broken=blocks_broken+VALUES(blocks_broken),"
                + "blocks_placed=blocks_placed+VALUES(blocks_placed),"
                + "mob_kills=mob_kills+VALUES(mob_kills), play_time=play_time+VALUES(play_time),"
                + "items_crafted=items_crafted+VALUES(items_crafted), jumps=jumps+VALUES(jumps),"
                + "chat_messages=chat_messages+VALUES(chat_messages),"
                + "money_spent=money_spent+VALUES(money_spent), money_made=money_made+VALUES(money_made)";
    }

    private void bindDelta(PreparedStatement statement, UUID uuid, String name, StatsDelta delta)
            throws SQLException {
        statement.setString(1, uuid.toString());
        statement.setString(2, name);
        statement.setLong(3, delta.kills());
        statement.setLong(4, delta.deaths());
        statement.setLong(5, delta.blocksBroken());
        statement.setLong(6, delta.blocksPlaced());
        statement.setLong(7, delta.mobKills());
        statement.setLong(8, delta.playTime());
        statement.setLong(9, delta.itemsCrafted());
        statement.setLong(10, delta.jumps());
        statement.setLong(11, delta.chatMessages());
        statement.setDouble(12, delta.moneySpent());
        statement.setDouble(13, delta.moneyMade());
    }

    public boolean mutate(UUID uuid, StatsType type, String operation, double amount) throws SQLException {
        String expression = switch (operation) {
            case "set" -> "?";
            case "add" -> type.column() + " + ?";
            case "remove" -> "GREATEST(0, " + type.column() + " - ?)";
            default -> throw new IllegalArgumentException("Unknown stats operation");
        };
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE ravencore_player_stats SET " + type.column() + " = " + expression + " WHERE uuid = ?")) {
            if (type.decimal()) statement.setDouble(1, amount);
            else statement.setLong(1, (long) amount);
            statement.setString(2, uuid.toString());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean reset(UUID uuid) throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE ravencore_player_stats SET kills=0,deaths=0,blocks_broken=0,"
                             + "blocks_placed=0,mob_kills=0,play_time=0,items_crafted=0,jumps=0,"
                             + "chat_messages=0,money_spent=0,money_made=0 WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            return statement.executeUpdate() > 0;
        }
    }

    private StatsSnapshot read(ResultSet result) throws SQLException {
        return new StatsSnapshot(UUID.fromString(result.getString("uuid")), result.getString("player_name"),
                result.getLong("kills"), result.getLong("deaths"), result.getLong("blocks_broken"),
                result.getLong("blocks_placed"), result.getLong("mob_kills"), result.getLong("play_time"),
                result.getLong("items_crafted"), result.getLong("jumps"), result.getLong("chat_messages"),
                result.getDouble("money_spent"), result.getDouble("money_made"));
    }
}
