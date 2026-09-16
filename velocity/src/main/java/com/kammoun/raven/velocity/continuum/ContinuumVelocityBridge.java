package com.kammoun.raven.velocity.continuum;

import com.kammoun.raven.library.continuum.ContinuumEntityIdPoolStore;
import com.kammoun.raven.library.continuum.ContinuumTransferStore;
import com.kammoun.raven.library.redis.event.BorderCrossEvent;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.velocity.RavenVelocity;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public final class ContinuumVelocityBridge {

    private static final String API_CLASS = "com.velocitypowered.api.proxy.player.ClientWorldSwitches";

    private final RavenVelocity plugin;
    private final Logger logger;
    private final ContinuumTransferStore store;
    private final ContinuumEntityIdPoolStore entityIdPool;
    private final Method clientEntityIdMethod;
    private boolean enabled;
    private boolean autoDetect;
    private boolean networkEntityIdPoolEnabled;
    private int networkEntityIdMin;
    private int networkEntityIdMax;
    private int networkEntityIdTtlSeconds;
    private int networkEntityIdDisconnectRetainSeconds;
    private boolean debug;

    public ContinuumVelocityBridge(RavenVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.store = new ContinuumTransferStore(
                plugin.getRedisMessenger().getConnection().getPool(),
                logger,
                plugin.getInt("continuum.transfer-metadata-ttl-seconds", 30)
        );
        this.entityIdPool = new ContinuumEntityIdPoolStore(
                plugin.getRedisMessenger().getConnection().getPool(),
                logger
        );
        this.clientEntityIdMethod = findClientEntityIdMethod();
        reload();
    }

    public void reload() {
        this.enabled = plugin.getBoolean("continuum.enabled", false);
        this.autoDetect = plugin.getBoolean("continuum.auto-detect", true);
        this.networkEntityIdPoolEnabled = plugin.getBoolean("continuum.network-entity-id-pool.enabled", true);
        this.networkEntityIdMin = plugin.getInt("continuum.network-entity-id-pool.min-id", 1_000_000_000);
        this.networkEntityIdMax = plugin.getInt("continuum.network-entity-id-pool.max-id", 1_100_000_000);
        this.networkEntityIdTtlSeconds = plugin.getInt("continuum.network-entity-id-pool.ttl-seconds", 300);
        this.networkEntityIdDisconnectRetainSeconds = plugin.getInt("continuum.network-entity-id-pool.disconnect-retain-seconds", 60);
        this.debug = plugin.getBoolean("continuum.debug", false);
        if (enabled && !networkEntityIdPoolEnabled && clientEntityIdMethod == null) {
            logger.warning("[Continuum] Enabled, but no network entity-id pool or Continuum ClientWorldSwitches API is available. Using normal transfers.");
        } else if (enabled && !isProxyConfigured()) {
            logger.warning("[Continuum] Enabled, but remove-reconfig/keep-client-world-on-switch are not both enabled. Using normal transfers.");
        }
    }

    public void prepare(UUID playerUuid, String targetServer, String dimensionKey) {
        attach(playerUuid, targetServer, dimensionKey, metadata -> {});
    }

    public void attach(UUID playerUuid, CrossServerTeleportEvent event) {
        if (event == null || event.getTargetServer() == null) {
            return;
        }
        attach(playerUuid, event.getTargetServer(), event.getWorld(), metadata -> {
            event.setContinuumClientEntityId(metadata.clientEntityId());
            event.setContinuumDimensionKey(metadata.dimensionKey());
        });
    }

    public void attach(UUID playerUuid, BorderCrossEvent event) {
        if (event == null || event.getTargetServer() == null) {
            return;
        }
        attach(playerUuid, event.getTargetServer(), event.getDimension(), metadata -> {
            event.setContinuumClientEntityId(metadata.clientEntityId());
            event.setContinuumDimensionKey(metadata.dimensionKey());
        });
    }

    private void attach(UUID playerUuid, String targetServer, String dimensionKey, MetadataConsumer consumer) {
        if (!canUse()) {
            return;
        }
        int clientEntityId = clientEntityId(playerUuid);
        if (clientEntityId <= 0) {
            debug("No client entity id available for " + playerUuid);
            return;
        }
        if (store.store(playerUuid, clientEntityId, targetServer, dimensionKey)) {
            consumer.accept(new Metadata(clientEntityId, dimensionKey));
            debug("Stored client entity id " + clientEntityId + " for " + playerUuid + " -> " + targetServer);
        }
    }

    public void retain(UUID playerUuid) {
        if (!enabled || !networkEntityIdPoolEnabled) {
            return;
        }
        entityIdPool.retain(playerUuid, networkEntityIdDisconnectRetainSeconds);
    }

    private boolean canUse() {
        return enabled
                && (!autoDetect || isProxyConfigured())
                && (networkEntityIdPoolEnabled || clientEntityIdMethod != null);
    }

    private int clientEntityId(UUID playerUuid) {
        if (networkEntityIdPoolEnabled) {
            int assigned = entityIdPool.assign(playerUuid, networkEntityIdMin, networkEntityIdMax, networkEntityIdTtlSeconds);
            if (assigned > 0) {
                return assigned;
            }
        }
        if (clientEntityIdMethod == null) {
            return 0;
        }
        try {
            Object value = clientEntityIdMethod.invoke(null, playerUuid);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException exception) {
            debug("Failed to read client entity id: " + exception.getMessage());
            return 0;
        }
    }

    private Method findClientEntityIdMethod() {
        try {
            Class<?> type = Class.forName(API_CLASS);
            return type.getMethod("clientEntityId", UUID.class);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private boolean isProxyConfigured() {
        Object configuration = plugin.getServer().getConfiguration();
        return readBoolean(configuration, "isRemoveReconfig")
                && readBoolean(configuration, "isKeepClientWorldOnSwitch");
    }

    private boolean readBoolean(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Boolean enabled && enabled;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void debug(String message) {
        if (debug) {
            logger.info("[Continuum] " + message);
        }
    }

    private record Metadata(int clientEntityId, String dimensionKey) {}

    @FunctionalInterface
    private interface MetadataConsumer {
        void accept(Metadata metadata);
    }
}
