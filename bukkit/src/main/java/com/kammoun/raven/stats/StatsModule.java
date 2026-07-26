package com.kammoun.raven.stats;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.library.redis.event.BalanceUpdateEvent;
import com.kammoun.raven.stats.command.StatsCommand;
import com.kammoun.raven.stats.command.StatsManagerCommand;
import com.kammoun.raven.stats.config.StatsConfig;
import com.kammoun.raven.stats.db.StatsRepository;
import com.kammoun.raven.stats.db.StatsTable;
import com.kammoun.raven.stats.gui.StatsMenu;
import com.kammoun.raven.stats.listener.StatsListener;
import com.kammoun.raven.stats.model.StatsType;
import com.kammoun.raven.stats.papi.StatsPlaceholders;
import com.kammoun.raven.stats.service.StatsService;
import lombok.Getter;

import java.util.UUID;

@Getter
public final class StatsModule extends KModule<RavenCore> {
    private StatsConfig statsConfig;
    private StatsService service;

    public StatsModule() {
        super("stats");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().warning("[Stats] MySQL is unavailable. Module disabled.");
            return;
        }
        saveDefaultConfig("gui/stats-menu.yml");
        statsConfig = new StatsConfig(getConfig());
        new StatsTable(api.getMySqlManager()).createTable();
        service = new StatsService(api, new StatsRepository(api.getMySqlManager()), statsConfig);
        service.start();
        registerEvent(new StatsListener(service));
        registerCommand(new StatsCommand(service,
                () -> new StatsMenu(api, statsConfig.shardsPlaceholder())), "stats");
        registerCommand(new StatsManagerCommand(service, this::reload), "statsmanager");

        api.getNetworkEventBus().subscribe(BalanceUpdateEvent.class, event -> {
            try {
                UUID uuid = UUID.fromString(event.getUuid());
                if (api.getServer().getPlayer(uuid) == null || event.getDelta() == 0D) return;
                service.increment(uuid, event.getDelta() > 0 ? StatsType.MONEY_MADE : StatsType.MONEY_SPENT,
                        Math.abs(event.getDelta()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        if (KPlaceholderExpansion.isAvailable()) {
            new StatsPlaceholders(api, service, statsConfig.shardsPlaceholder()).register();
        }
        getLogger().info("[Stats] Enabled with shared MySQL persistence.");
    }

    @Override
    public void onModuleDisable() {
        if (service != null) service.shutdown();
        getLogger().info("[Stats] Disabled.");
    }

    @Override
    public void reload() {
        if (service == null) return;
        reloadConfig();
        statsConfig = new StatsConfig(getConfig());
        service.updateConfig(statsConfig);
    }
}
