package com.kammoun.raven.economy.balance;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.library.tasks.SchedulerUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class EconomyBalanceMirror {
    private static final String CREATE = """
            CREATE TABLE IF NOT EXISTS ravencore_economy_balances (
              uuid VARCHAR(36) NOT NULL,
              player_name VARCHAR(32) NULL,
              balance DOUBLE NOT NULL DEFAULT 0,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (uuid),
              INDEX idx_ravencore_economy_balance (balance)
            )
            """;
    private static final String UPSERT = """
            INSERT INTO ravencore_economy_balances (uuid, player_name, balance)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
              player_name = COALESCE(VALUES(player_name), player_name),
              balance = VALUES(balance)
            """;

    private final RavenCore plugin;
    private final MySQLDBManager database;
    private final Logger logger;
    private final int flushIntervalTicks;
    private final int batchSize;
    private final Map<UUID, PendingBalance> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean flushing = new AtomicBoolean();
    private SchedulerUtil.Task flushTask;

    public EconomyBalanceMirror(RavenCore plugin, MySQLDBManager database,
                                int flushIntervalTicks, int batchSize) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
        this.flushIntervalTicks = Math.max(20, flushIntervalTicks);
        this.batchSize = Math.max(25, Math.min(500, batchSize));
    }

    public void start() throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE)) {
            statement.executeUpdate();
        }
        flushTask = SchedulerUtil.runAsyncTimer(this::requestFlush, flushIntervalTicks, flushIntervalTicks);
    }

    public void enqueue(UUID uuid, String playerName, double balance) {
        if (uuid == null || Double.isNaN(balance) || Double.isInfinite(balance)) return;
        pending.compute(uuid, (key, existing) -> {
            String name = playerName == null || playerName.isBlank()
                    ? existing == null ? null : existing.playerName()
                    : playerName;
            return new PendingBalance(uuid, name, balance);
        });
    }

    public void requestFlush() {
        if (!flushing.compareAndSet(false, true)) return;
        SchedulerUtil.runAsync(() -> {
            try {
                flush();
            } finally {
                flushing.set(false);
            }
        });
    }

    public void shutdown() {
        if (flushTask != null) flushTask.cancel();
        if (!pending.isEmpty()) {
            try {
                flush();
            } catch (Exception e) {
                logger.warning("[Economy] Final balance mirror flush failed: " + e.getMessage());
            }
        }
    }

    private void flush() {
        List<PendingBalance> drained = drain();
        if (drained.isEmpty()) return;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            int queued = 0;
            for (PendingBalance balance : drained) {
                statement.setString(1, balance.uuid().toString());
                statement.setString(2, balance.playerName());
                statement.setDouble(3, balance.balance());
                statement.addBatch();
                if (++queued >= batchSize) {
                    statement.executeBatch();
                    statement.clearBatch();
                    queued = 0;
                }
            }
            if (queued > 0) statement.executeBatch();
        } catch (Exception e) {
            drained.forEach(balance -> enqueue(balance.uuid(), balance.playerName(), balance.balance()));
            logger.warning("[Economy] Balance mirror flush failed for " + drained.size()
                    + " players; queued for retry: " + e.getMessage());
        }
    }

    private List<PendingBalance> drain() {
        List<PendingBalance> drained = new ArrayList<>(pending.size());
        for (UUID uuid : new ArrayList<>(pending.keySet())) {
            PendingBalance balance = pending.remove(uuid);
            if (balance != null) drained.add(balance);
        }
        return drained;
    }

    private record PendingBalance(UUID uuid, String playerName, double balance) {
    }
}
