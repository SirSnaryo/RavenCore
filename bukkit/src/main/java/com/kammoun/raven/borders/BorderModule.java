package com.kammoun.raven.borders;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.borders.config.BorderConfig;
import com.kammoun.raven.borders.listener.BorderMovementListener;
import com.kammoun.raven.borders.listener.PlayerListener;
import com.kammoun.raven.borders.registery.BorderRegistry;
import com.kammoun.raven.borders.utils.BorderTransferStore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;


@Getter
public class BorderModule extends KModule<RavenCore> {

    private BorderConfig borderConfig;
    private BorderRegistry registry;
    private BorderTransferStore transferStore;

    public BorderModule() {
        super("borders");
    }

    @Override
    public void onModuleEnable() {
        borderConfig = new BorderConfig(api.getConfig());
        var pool = api.getRedisMessenger().getConnection().getPool();
        registry = new BorderRegistry(pool, getLogger());
        transferStore = new BorderTransferStore(pool, getLogger());

        registerEvent(new BorderMovementListener(api, borderConfig, registry, transferStore, api.getNetworkEventBus()));
        registerEvent(new PlayerListener(this));

        getLogger().info("[Borders] Enabled — cluster=" + borderConfig.cluster()
                + " cell=(" + borderConfig.cellX() + "," + borderConfig.cellZ() + ")");
    }

    @Override
    public void onModuleDisable() {}

    @Override
    public void reload() {
        borderConfig = new BorderConfig(api.getConfig());
    }
}
