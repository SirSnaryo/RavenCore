package com.kammoun.raven.economy;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.economy.command.BalanceCommand;
import com.kammoun.raven.economy.command.EcoCommand;
import com.kammoun.raven.economy.command.PayMessageCommand;
import com.kammoun.raven.economy.command.PayCommand;
import com.kammoun.raven.economy.command.PayToggleCommand;
import com.kammoun.raven.economy.confirm.EconomyConfirmationMenu;
import com.kammoun.raven.economy.confirm.EconomyConfirmationService;
import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.gui.EconomyLogsHolder;
import com.kammoun.raven.economy.gui.EconomyLogsMenu;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.economy.log.EconomyLogRepository;
import com.kammoun.raven.economy.papi.EconomyPlaceholders;
import com.kammoun.raven.economy.service.EconomyService;
import com.kammoun.raven.economy.settings.EconomySettingsService;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.redis.event.PaymentNotificationEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;

import java.io.File;
import java.util.UUID;

public class EconomyModule extends KModule<RavenCore> {

    private EconomyService economyService;
    private RavenEconomy economy;
    private EconomySettingsService settingsService;
    private EconomyConfirmationService confirmationService;
    private EconomyLogsMenu logsMenu;
    private EconomyLogRepository logRepository;

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
        saveDefaultConfig("pay-confirm-menu.yml");

        if (api.getMySqlManager() != null && api.getMySqlManager().isConnected()) {
            try {
                logRepository = new EconomyLogRepository(api.getMySqlManager());
                logRepository.init();
            } catch (Exception e) {
                logRepository = null;
                getLogger().warning("[Economy] Failed to initialize transaction logs; logs GUI disabled: " + e.getMessage());
            }
        } else {
            getLogger().warning("[Economy] MySQL unavailable; transaction logs GUI will be disabled.");
        }

        economyService = new EconomyService(
                api,
                api.getRedisMessenger().getConnection().getPool(),
                api.getNetworkEventBus(),
                api.getServerId(),
                api.getServerDisplayName(),
                startingBalance,
                logRepository
        );
        economy = new RavenEconomy(economyService, singular, plural, digits);
        settingsService = new EconomySettingsService(api.getRedisMessenger().getConnection().getPool());
        EconomyMessages messages = new EconomyMessages(loadMessages());
        confirmationService = new EconomyConfirmationService(
                api,
                economyService,
                economy,
                settingsService,
                api.getNetworkEventBus(),
                messages,
                sound("sounds.confirm-open"),
                sound("sounds.pay-success"),
                sound("sounds.cancel"),
                sound("sounds.fail"),
                getConfig().getBoolean("confirm.enabled", true),
                getConfig().getDouble("confirm.large-amount-threshold", 100_000D),
                getConfig().getInt("confirm.expires-seconds", 30)
        );
        EconomyConfirmationMenu confirmationMenu = new EconomyConfirmationMenu(api, "economy/pay-confirm-menu.yml", confirmationService);
        confirmationService.menu(confirmationMenu);
        if (logRepository != null) {
            logsMenu = new EconomyLogsMenu(api, logRepository, economy, getLogger());
        }

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
            if (recipient != null && settingsService.paymentMessages(recipient.getUniqueId())) {
                recipient.sendMessage(event.getMessage());
            }
        });

        onEvent(InventoryClickEvent.class, event -> {
            if (logsMenu == null) return;
            if (event.getInventory().getHolder() instanceof EconomyLogsHolder) {
                logsMenu.handleClick(event);
            }
        });

        registerCommand(new BalanceCommand(economyService, economy, api.getNetworkPlayerManager(), messages), "balance");
        registerCommand(new PayCommand(api, confirmationService, api.getNetworkPlayerManager(), messages), "pay");
        registerCommand(new PayToggleCommand(settingsService, messages), "paytoggle");
        registerCommand(new PayMessageCommand(settingsService, messages), "paymsg");
        registerCommand(new EcoCommand(economyService, economy, api.getNetworkPlayerManager(), messages, logsMenu), "eco");
        registerPlaceholders();

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
    public EconomySettingsService getSettingsService() { return settingsService; }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }

    private KSound sound(String path) {
        return KSound.getSoundFromConfig(getConfig().getConfigurationSection(path));
    }

    private void registerPlaceholders() {
        if (!KPlaceholderExpansion.isAvailable()) {
            return;
        }
        new EconomyPlaceholders(api, "ravencore", economyService, economy).register();
        new EconomyPlaceholders(api, "raven", economyService, economy).register();
        getLogger().info("[Economy] Registered PlaceholderAPI placeholders.");
    }
}
