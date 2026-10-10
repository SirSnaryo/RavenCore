package com.kammoun.raven.endportal;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.endportal.config.EndPortalConfig;
import com.kammoun.raven.endportal.config.EndPortalMessages;
import com.kammoun.raven.endportal.config.EndPortalSounds;
import com.kammoun.raven.endportal.listener.EndPortalBlockListener;
import com.kammoun.raven.endportal.listener.EndPortalEscapeListener;
import com.kammoun.raven.endportal.listener.EndPortalPortalListener;
import com.kammoun.raven.endportal.listener.EndPortalRespawnListener;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.ServerRole;
import lombok.Getter;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Links every survival server's End portal to the one dedicated End backend, and
 * sends players leaving the End back into a random overworld cluster.
 */
@Getter
public final class EndPortalModule extends KModule<RavenCore> {

    private EndPortalConfig endPortalConfig;
    private EndPortalService service;

    public EndPortalModule() {
        super("endportal");
    }

    @Override
    public void onModuleEnable() {
        if (api.getEndModule() != null && !api.getEndModule().isPortalsEnabled()) {
            getLogger().info("[EndPortal] Disabled by end/config.yml (portals.enabled: false).");
            return;
        }
        endPortalConfig = EndPortalConfig.load(getConfig());
        service = new EndPortalService(api, endPortalConfig,
                new EndPortalMessages(api), new EndPortalSounds(api));

        registerEvent(new EndPortalPortalListener(service));
        registerEvent(new EndPortalRespawnListener(api, service));
        if (endPortalConfig.detectPortalBlocks()) {
            registerEvent(new EndPortalBlockListener(service));
        }
        if (api.getServerRole() == ServerRole.END) {
            registerEvent(new EndPortalEscapeListener(service));
        }
        onEvent(PlayerQuitEvent.class, event -> service.release(event.getPlayer().getUniqueId()));

        getLogger().info("[EndPortal] Enabled — end-server=" + endPortalConfig.endServer()
                + " return-clusters=" + (endPortalConfig.returnClusters().isEmpty()
                ? "<all registered>" : endPortalConfig.returnClusters()));
    }

    @Override
    public void onModuleDisable() {
        if (service != null) service.stop();
    }

    @Override
    public void reload() {
        reloadConfig();
        if (service == null) {
            getLogger().info("[EndPortal] Disabled by end/config.yml (portals.enabled: false).");
            return;
        }
        getLogger().info("[EndPortal] Configuration changes apply after a server restart.");
    }
}
