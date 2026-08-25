package com.kammoun.raven.library.skin.cache;

import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.library.skin.SkinStorage;
import com.kammoun.raven.library.skin.model.SkinData;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class MySQLSkinStorage implements SkinStorage {
    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS ravencore_player_skins (
              uuid VARCHAR(36) NOT NULL,
              texture_value TEXT NOT NULL,
              signature TEXT NULL,
              last_seen BIGINT NOT NULL,
              PRIMARY KEY (uuid),
              INDEX idx_ravencore_skins_last_seen (last_seen)
            )
            """;
    private static final String SELECT_BY_UUID =
            "SELECT uuid, texture_value, signature, last_seen FROM ravencore_player_skins WHERE uuid = ?";
    private static final String UPSERT = """
            INSERT INTO ravencore_player_skins (uuid, texture_value, signature, last_seen)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              texture_value = VALUES(texture_value),
              signature = VALUES(signature),
              last_seen = VALUES(last_seen)
            """;
    private static final String DELETE_BY_UUID = "DELETE FROM ravencore_player_skins WHERE uuid = ?";
    private static final String EXISTS_BY_UUID = "SELECT 1 FROM ravencore_player_skins WHERE uuid = ? LIMIT 1";

    private final MySQLDBManager database;
    private final Logger logger;
    private final ExecutorService executor;

    public MySQLSkinStorage(@NotNull MySQLDBManager database, @NotNull Logger logger) {
        this.database = database;
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "RavenCore-SkinCache-MySQL");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(CREATE_TABLE)) {
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize MySQL skin storage", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<SkinData>> load(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_BY_UUID)) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(new SkinData(
                                UUID.fromString(result.getString("uuid")),
                                result.getString("texture_value"),
                                result.getString("signature"),
                                result.getLong("last_seen")
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.warning("[SkinCache] Failed to load skin for " + uuid + ": " + e.getMessage());
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(@NotNull SkinData skinData) {
        return CompletableFuture.runAsync(() -> {
            if (skinData.isDefault()) return;
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                statement.setString(1, skinData.uuid().toString());
                statement.setString(2, skinData.textureValue());
                statement.setString(3, skinData.signature());
                statement.setLong(4, skinData.lastSeen());
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.warning("[SkinCache] Failed to save skin for " + skinData.uuid() + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> delete(@NotNull UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(DELETE_BY_UUID)) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.warning("[SkinCache] Failed to delete skin for " + uuid + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> exists(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(EXISTS_BY_UUID)) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            } catch (SQLException e) {
                logger.warning("[SkinCache] Failed to check skin for " + uuid + ": " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        });
    }
}
