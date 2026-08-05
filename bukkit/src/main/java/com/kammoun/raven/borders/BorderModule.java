package com.kammoun.raven.borders;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.borders.config.BorderConfig;
import com.kammoun.raven.borders.config.TrueWorldBorderConfig;
import com.kammoun.raven.borders.listener.BorderInteractionListener;
import com.kammoun.raven.borders.listener.BorderMovementListener;
import com.kammoun.raven.borders.listener.PlayerListener;
import com.kammoun.raven.borders.particle.BorderParticleRenderer;
import com.kammoun.raven.borders.registery.BorderRegistry;
import com.kammoun.raven.borders.utils.BorderTransferStore;
import com.kammoun.raven.borders.world.BorderSpawnController;
import com.kammoun.raven.borders.world.TrueWorldBorderController;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.BorderTransferFailedEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.UUID;

@Getter
public class BorderModule extends KModule<RavenCore> {

    private BorderConfig borderConfig;
    private BorderRegistry registry;
    private BorderTransferStore transferStore;
    private BorderParticleRenderer particleRenderer;
    private BorderMovementListener movementListener;
    private TrueWorldBorderController trueWorldBorderController;
    private BorderSpawnController borderSpawnController;

    public BorderModule() {
        super("borders");
    }

    @Override
    public void onModuleEnable() {
        borderConfig = new BorderConfig(api.getConfig());
        TrueWorldBorderConfig trueBorderConfig = loadTrueWorldBorderConfig();
        var pool = api.getRedisMessenger().getConnection().getPool();
        registry = new BorderRegistry(pool, getLogger());
        transferStore = new BorderTransferStore(pool, getLogger());
        particleRenderer = new BorderParticleRenderer(this);

        movementListener = new BorderMovementListener(
                api, borderConfig, registry, transferStore, api.getNetworkEventBus());
        registerEvent(movementListener);
        registerEvent(new BorderInteractionListener(this));
        registerEvent(new PlayerListener(this));
        registerEvent(particleRenderer);
        trueWorldBorderController = new TrueWorldBorderController(api, getLogger(), trueBorderConfig);
        borderSpawnController = new BorderSpawnController(api, getLogger(), borderConfig);
        onEvent(WorldLoadEvent.class, event -> {
            trueWorldBorderController.apply(event.getWorld());
            borderSpawnController.apply(event.getWorld());
        });
        trueWorldBorderController.applyLoadedWorlds();
        borderSpawnController.applyLoadedWorlds();
        particleRenderer.start();
        api.getNetworkEventBus().subscribe(BorderTransferFailedEvent.class, this::onTransferFailed);

        getLogger().info("[Borders] Enabled — cluster=" + borderConfig.cluster()
                + " cell=(" + borderConfig.cellX() + "," + borderConfig.cellZ() + ")");
    }

    @Override
    public void onModuleDisable() {
        if (particleRenderer != null) {
            particleRenderer.close();
        }
        if (trueWorldBorderController != null) {
            trueWorldBorderController.close();
        }
        api.getNetworkEventBus().unsubscribe(BorderTransferFailedEvent.class);
    }

    @Override
    public void reload() {
        api.reloadConfig();
        borderConfig = new BorderConfig(api.getConfig());
        if (borderSpawnController != null) {
            borderSpawnController.reload(borderConfig);
        }
        try {
            TrueWorldBorderConfig trueBorderConfig = TrueWorldBorderConfig.load(api.getConfig(), borderConfig);
            if (trueWorldBorderController != null) {
                trueWorldBorderController.reload(trueBorderConfig);
            }
        } catch (IllegalArgumentException error) {
            getLogger().severe("[WorldBorder] Reload rejected; keeping the current native border: "
                    + error.getMessage());
        }
    }

    private TrueWorldBorderConfig loadTrueWorldBorderConfig() {
        try {
            return TrueWorldBorderConfig.load(api.getConfig(), borderConfig);
        } catch (IllegalArgumentException error) {
            getLogger().severe("[WorldBorder] Native outer border disabled: " + error.getMessage());
            return TrueWorldBorderConfig.disabled();
        }
    }

    public void suppressTransfers(UUID playerUuid, long durationMillis) {
        if (movementListener != null) movementListener.suppress(playerUuid, durationMillis);
    }

    public void releaseTransfers(UUID playerUuid) {
        if (movementListener != null) movementListener.release(playerUuid);
    }

    private void onTransferFailed(BorderTransferFailedEvent event) {
        if (!api.getServerId().equals(event.getTargetServer()) || event.getPlayerUuid() == null) {
            return;
        }
        if ("ALREADY_CONNECTED".equalsIgnoreCase(event.getReason())) {
            getLogger().info("[Borders] Ignoring completed transfer reported as ALREADY_CONNECTED for "
                    + event.getPlayerUuid() + ".");
            return;
        }

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(event.getPlayerUuid());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        Player player = api.getServer().getPlayer(playerUuid);
        if (player != null) {
            SchedulerUtil.execute(player, () ->
                    player.kickPlayer(ChatFormatter.color(borderConfig.transferFailureMessage())));
        }
    }
}
