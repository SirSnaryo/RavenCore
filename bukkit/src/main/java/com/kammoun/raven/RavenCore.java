package com.kammoun.raven;

import com.kammoun.raven.lastlocation.LastLocationModule;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.redis.RedisCredentials;
import com.kammoun.raven.library.redis.RedisMessenger;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class RavenCore extends JavaPlugin {

    @Getter
    private static RavenCore instance;

    private String serverId;
    private RedisCredentials redisCredentials;
    private RedisMessenger redisMessenger;
    private NetworkEventBus networkEventBus;
    private NetworkPlayerManager networkPlayerManager;
    private LastLocationModule lastLocationModule;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        String configuredId = getConfig().getString("network.server-id", "").trim();
        serverId = configuredId.isEmpty() ? getServer().getName() : configuredId;

        redisCredentials = new RedisCredentials(
                getConfig().getString("redis.host", "127.0.0.1"),
                getConfig().getInt("redis.port", 6379),
                getConfig().getString("redis.password", null),
                getConfig().getInt("redis.database", 0)
        );

        redisMessenger = new RedisMessenger(getLogger());
        redisMessenger.connect(redisCredentials);

        networkEventBus = new NetworkEventBus(redisMessenger, serverId,
                runnable -> Bukkit.getScheduler().runTask(this, runnable));
        networkPlayerManager = new NetworkPlayerManager(this, redisMessenger.getConnection().getPool(), serverId);

        lastLocationModule = new LastLocationModule();
        lastLocationModule.onEnable(this);
    }

    @Override
    public void onDisable() {
        if (lastLocationModule != null) {
            lastLocationModule.onDisable(this);
        }
        if (redisMessenger != null) {
            redisMessenger.close();
        }
    }

}
