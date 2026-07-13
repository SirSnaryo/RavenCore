package com.kammoun.raven.economy;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.economy.command.BalanceCommand;
import com.kammoun.raven.economy.command.EcoCommand;
import com.kammoun.raven.economy.command.PayCommand;
import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.economy.service.EconomyService;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.redis.event.PaymentNotificationEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;

import java.io.File;
import java.util.UUID;

public class EconomyModule extends KModule<RavenCore> {

    private EconomyService economyService;
    private RavenEconomy economy;

    public EconomyModule() {
        super("economy");
    }

    @Override
    public void onModuleEnable() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("[Economy] Vault not found — economy module will not load.");
            return;
        }

        double startingBalance = getConfig().getDouble("starting-balance", 0.0);
        String singular = getConfig().getString("currency.singular", "Coin");
        String plural = getConfig().getString("currency.plural", "Coins");
        int digits = getConfig().getInt("currency.fractional-digits", 2);

        economyService = new EconomyService(
                api,
                api.getRedisMessenger().getConnection().getPool(),
                api.getNetworkEventBus(),
                api.getServerId(),
                startingBalance
        );
        economy = new RavenEconomy(economyService, singular, plural, digits);
        EconomyMessages messages = new EconomyMessages(loadMessages());

        Bukkit.getServicesManager().register(Economy.class, economy, api, ServicePriority.Highest);

        Bukkit.getOnlinePlayers().forEach(p ->
                Bukkit.getAsyncScheduler().runNow(api, task -> economyService.loadPlayer(p.getUniqueId()))
        );

        onEvent(AsyncPlayerPreLoginEvent.class, event -> {
            if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                economyService.loadPlayer(event.getUniqueId());
            }
        });

        onEvent(PlayerQuitEvent.class, event ->
                Bukkit.getAsyncScheduler().runNow(api, task ->
                        economyService.unloadPlayer(event.getPlayer().getUniqueId())
                )
        );

        api.getNetworkEventBus().subscribe(PaymentNotificationEvent.class, event -> {
            Player recipient = Bukkit.getPlayer(UUID.fromString(event.getRecipientUuid()));
            if (recipient != null) {
                recipient.sendMessage(event.getMessage());
            }
        });

        registerCommand(new BalanceCommand(economyService, economy, api.getNetworkPlayerManager(), messages), "balance");
        registerCommand(new PayCommand(api, economyService, economy,
                api.getNetworkPlayerManager(), api.getNetworkEventBus(), messages), "pay");
        registerCommand(new EcoCommand(economyService, economy, api.getNetworkPlayerManager(), messages), "eco");

        getLogger().info("[Economy] Registered as Vault economy provider.");
    }

    @Override
    public void onModuleDisable() {
        api.getNetworkEventBus().unsubscribe(PaymentNotificationEvent.class);
        if (economyService != null) {
            Bukkit.getOnlinePlayers().forEach(p ->
                    economyService.unloadPlayer(p.getUniqueId())
            );
            economyService.shutdown();
        }
        if (economy != null) {
            Bukkit.getServicesManager().unregister(Economy.class, economy);
        }
        getLogger().info("[Economy] Disabled.");
    }

    public EconomyService getEconomyService() { return economyService; }
    public RavenEconomy getEconomy() { return economy; }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
}
