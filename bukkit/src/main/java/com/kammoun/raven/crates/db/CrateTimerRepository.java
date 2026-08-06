package com.kammoun.raven.crates.db;

import com.kammoun.raven.library.database.MySQLDBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public final class CrateTimerRepository {
    private static final String TIMER_ID = "global";
    private final MySQLDBManager database;

    public CrateTimerRepository(MySQLDBManager database) {
        this.database = database;
    }

    public TimerState getOrCreate(long nowMillis, int intervalSeconds) {
        long initialNext = nowMillis + intervalSeconds * 1000L;
        try (Connection connection = database.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT IGNORE INTO ravencore_crate_timer (id, next_reward_at, cycle_id)
                    VALUES (?, ?, 0)
                    """)) {
                insert.setString(1, TIMER_ID);
                insert.setLong(2, initialNext);
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT next_reward_at, cycle_id FROM ravencore_crate_timer WHERE id = ?")) {
                select.setString(1, TIMER_ID);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) return new TimerState(rs.getLong("next_reward_at"), rs.getLong("cycle_id"));
                }
            }
            return new TimerState(initialNext, 0);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read crate timer", e);
        }
    }

    public AdvanceResult tryAdvance(long observedNextRewardAt, long nowMillis, int intervalSeconds) {
        long next = nowMillis + intervalSeconds * 1000L;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE ravencore_crate_timer
                     SET next_reward_at = ?, cycle_id = cycle_id + 1
                     WHERE id = ? AND next_reward_at = ? AND next_reward_at <= ?
                     """)) {
            statement.setLong(1, next);
            statement.setString(2, TIMER_ID);
            statement.setLong(3, observedNextRewardAt);
            statement.setLong(4, nowMillis);
            if (statement.executeUpdate() != 1) return null;
            TimerState state = getOrCreate(nowMillis, intervalSeconds);
            return new AdvanceResult(state.cycleId(), state.nextRewardAt());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to advance crate timer", e);
        }
    }

    public boolean claimPlayerCycle(UUID uuid, long cycleId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO ravencore_crate_cycle_claims (uuid, cycle_id)
                     VALUES (?, ?)
                     """)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, cycleId);
            return statement.executeUpdate() == 1;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to claim crate cycle", e);
        }
    }

    public record TimerState(long nextRewardAt, long cycleId) {
    }

    public record AdvanceResult(long cycleId, long nextRewardAt) {
    }
}
