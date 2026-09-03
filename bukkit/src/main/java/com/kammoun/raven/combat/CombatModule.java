package com.kammoun.raven.combat;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.combat.command.CombatAdminCommand;
import com.kammoun.raven.combat.config.CombatConfig;
import com.kammoun.raven.combat.listener.CombatListener;
import com.kammoun.raven.combat.service.CombatService;
import com.kammoun.raven.combat.service.ProtectedRegionChecker;
import com.kammoun.raven.combat.store.CombatStore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.redis.event.CombatTagEvent;
import com.kammoun.raven.library.redis.event.CombatUntagEvent;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.UUID;

@Getter
public final class CombatModule extends KModule<RavenCore> {
    private CombatConfig combatConfig;
    private CombatService service;
    private boolean serverStopping;

    public CombatModule() {
        super("combat");
    }

    @Override
    public void onModuleEnable() {
        loadState();
        registerEvent(new CombatListener(api, service, combatConfig, new ProtectedRegionChecker()));
        CombatAdminCommand command = new CombatAdminCommand(this);
        registerCommand(command, "combatmanager");
        api.getNetworkEventBus().subscribe(CombatTagEvent.class, service::applyRemote);
        api.getNetworkEventBus().subscribe(CombatUntagEvent.class, service::removeRemote);
        api.getServer().getOnlinePlayers().forEach(service::load);
        getLogger().info("[Combat] Cross-server combat enabled.");
    }

    @Override
    public void onModuleDisable() {
        serverStopping = true;
        api.getNetworkEventBus().unsubscribe(CombatTagEvent.class);
        api.getNetworkEventBus().unsubscribe(CombatUntagEvent.class);
        if (service != null) service.clearLocal();
    }

    @Override
    public void reload() {
        reloadConfig();
        getLogger().info("[Combat] Configuration changes apply after a server restart.");
    }

    public boolean isInCombat(Player player) {
        return service != null && service.isTagged(player);
    }

    public boolean canTransfer(Player player, boolean sendMessage) {
        if (!isInCombat(player)) return true;
        if (sendMessage) combatConfig.send(player, "transfer-blocked");
        return false;
    }

    public void markNetworkTransfer(UUID playerUuid) {
        if (service != null) service.markNetworkTransfer(playerUuid);
    }

    public boolean isNetworkTransfer(UUID playerUuid) {
        return service != null && service.isNetworkTransfer(playerUuid);
    }

    public void unloadLocal(UUID playerUuid) {
        if (service != null) service.unloadLocal(playerUuid);
    }

    private void loadState() {
        combatConfig = CombatConfig.load(getConfig());
        CombatStore store = new CombatStore(api.getRedisMessenger().getConnection().getPool());
        service = new CombatService(api, combatConfig, store);
    }
}
