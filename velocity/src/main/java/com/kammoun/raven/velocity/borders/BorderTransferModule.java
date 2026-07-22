package com.kammoun.raven.velocity.borders;

import com.kammoun.raven.library.module.VModule;
import com.kammoun.raven.library.redis.event.BorderCrossEvent;
import com.kammoun.raven.velocity.RavenVelocity;

import java.util.UUID;
import java.util.logging.Logger;

public class BorderTransferModule extends VModule {

    private final RavenVelocity ravenVelocity;

    public BorderTransferModule(RavenVelocity ravenVelocity) {
        super("borders");
        this.ravenVelocity = ravenVelocity;
    }

    @Override
    public void onModuleEnable() {
        ravenVelocity.getNetworkEventBus().subscribe(BorderCrossEvent.class, this::onBorderCross);
        getLogger().info("[Borders] Velocity border transfer module enabled.");
    }

    @Override
    public void onModuleDisable() {
        ravenVelocity.getNetworkEventBus().unsubscribe(BorderCrossEvent.class);
    }

    private void onBorderCross(BorderCrossEvent event) {
        if (event.getPlayerUuid() == null || event.getTargetServer() == null) return;

        UUID uuid;
        try {
            uuid = UUID.fromString(event.getPlayerUuid());
        } catch (IllegalArgumentException e) {
            Logger.getLogger("RavenCore/borders").warning("[Borders] Invalid UUID: " + event.getPlayerUuid());
            return;
        }

        ravenVelocity.getServer().getScheduler()
                .buildTask(ravenVelocity, () ->
                        ravenVelocity.getServer().getPlayer(uuid).ifPresent(player ->
                                ravenVelocity.getServer().getServer(event.getTargetServer()).ifPresentOrElse(
                                        target -> player.createConnectionRequest(target).connect()
                                                .thenAccept(result -> {
                                                    if (!result.isSuccessful()) {
                                                        Logger.getLogger("RavenCore/borders").warning(
                                                                "[Borders] Failed to transfer " + player.getUsername()
                                                                        + " to " + event.getTargetServer()
                                                                        + ": " + result.getStatus());
                                                    }
                                                }),
                                        () -> Logger.getLogger("RavenCore/borders").warning(
                                                "[Borders] Unknown target server: " + event.getTargetServer())
                                )
                        )
                ).schedule();
    }
}
