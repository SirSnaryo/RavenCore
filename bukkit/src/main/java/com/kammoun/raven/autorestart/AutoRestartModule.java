package com.kammoun.raven.autorestart;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.redis.event.ServerRestartEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;

public class AutoRestartModule extends KModule<RavenCore> {

    public AutoRestartModule() {
        super("autorestart");
    }

    @Override
    public void onModuleEnable() {
        api.getNetworkEventBus().subscribe(ServerRestartEvent.class, event -> {
            String target = event.getTargetServer();
            if (target != null && !target.isBlank() && !target.equalsIgnoreCase("ALL")
                    && !target.equalsIgnoreCase(api.getServerId())) {
                return;
            }
            getLogger().info("[AutoRestart] Restart requested by " + event.getSourceServer() + " — stopping server.");
            SchedulerUtil.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop"));
        });
        getLogger().info("[AutoRestart] Receiver enabled.");
    }

    @Override
    public void onModuleDisable() {
        api.getNetworkEventBus().unsubscribe(ServerRestartEvent.class);
        getLogger().info("[AutoRestart] Disabled.");
    }
}
