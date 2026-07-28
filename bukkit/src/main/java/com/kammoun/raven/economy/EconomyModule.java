package com.kammoun.raven.economy;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.economy.command.BalanceCommand;
import com.kammoun.raven.economy.command.EcoCommand;
import com.kammoun.raven.economy.command.PayMessageCommand;
import com.kammoun.raven.economy.command.PayCommand;
import com.kammoun.raven.economy.command.PayToggleCommand;
import com.kammoun.raven.economy.balance.EconomyBalanceMirror;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public class EconomyModule extends KModule<RavenCore> {

    private EconomyService economyService;
    private RavenEconomy economy;
    private EconomySettingsService settingsService;
    private EconomyConfirmationService confirmationService;
    private EconomyLogsMenu logsMenu;
    private EconomyLogRepository logRepository;
    private EconomyBalanceMirror balanceMirror;

    public EconomyModule() {
        super("economy");
    }

    @Override
    public void onModuleEnable() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("[Economy] Vault not found — economy module will not load.");
            return;
        }

        saveDefaultConfig("messages.yml");
        saveDefaultConfig("pay-confirm-menu.yml");
        upgradeDefaultTextFiles();
        reloadConfig();

        double startingBalance = getConfig().getDouble("starting-balance", 0.0);
        String singular = normalizeCurrencyName(getConfig().getString("currency.singular", "Money"));
        String plural = normalizeCurrencyName(getConfig().getString("currency.plural", "Money"));
        int digits = getConfig().getInt("currency.fractional-digits", 2);

        if (api.getMySqlManager() != null && api.getMySqlManager().isConnected()) {
            try {
                logRepository = new EconomyLogRepository(api.getMySqlManager());
                logRepository.init();
            } catch (Exception e) {
                logRepository = null;
                getLogger().warning("[Economy] Failed to initialize transaction logs; logs GUI disabled: " + e.getMessage());
            }
            try {
                balanceMirror = new EconomyBalanceMirror(api, api.getMySqlManager(),
                        Math.max(1, getConfig().getInt("balance-mirror.flush-interval-seconds", 10)) * 20,
                        getConfig().getInt("balance-mirror.batch-size", 250));
                balanceMirror.start();
            } catch (Exception e) {
                balanceMirror = null;
                getLogger().warning("[Economy] Balance leaderboard mirror disabled: " + e.getMessage());
            }
        } else {
            getLogger().warning("[Economy] MySQL unavailable; transaction logs GUI will be disabled.");
            getLogger().warning("[Economy] Balance leaderboard mirror will be disabled.");
        }

        economyService = new EconomyService(
                api,
                api.getRedisMessenger().getConnection().getPool(),
                api.getNetworkEventBus(),
                api.getServerId(),
                api.getServerDisplayName(),
                startingBalance,
                logRepository,
                balanceMirror
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
                getConfig().getString("confirm.bypass-permission", ""),
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
                Bukkit.getAsyncScheduler().runNow(api, task -> economyService.loadPlayer(p.getUniqueId(), p.getName()))
        );

        onEvent(AsyncPlayerPreLoginEvent.class, event -> {
            if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                economyService.loadPlayer(event.getUniqueId(), event.getName());
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
        if (balanceMirror != null) {
            balanceMirror.shutdown();
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

    private String normalizeCurrencyName(String value) {
        if (value == null || value.isBlank()) {
            return "Money";
        }
        if (value.equalsIgnoreCase("coin") || value.equalsIgnoreCase("coins")) {
            return "Money";
        }
        return value;
    }

    private void upgradeDefaultTextFiles() {
        replaceText(new File(getDataFolder(), "config.yml"),
                "singular: \"Coin\"", "singular: \"Money\"",
                "plural: \"Coins\"", "plural: \"Money\"",
                "  large-amount-threshold: 100000.0\n  expires-seconds: 30",
                "  large-amount-threshold: 100000.0\n  bypass-permission: \"\"\n  expires-seconds: 30",
                "  large-amount-threshold: 100000.0\r\n  expires-seconds: 30",
                "  large-amount-threshold: 100000.0\r\n  bypass-permission: \"\"\r\n  expires-seconds: 30");
        replaceText(new File(getDataFolder(), "messages.yml"),
                "&#33FF66Your balance: &#FFFFFF%balance%", "&#FFFFFFMoney &#7A7A7A» &#33FF66%balance%",
                "&#33FF66%player%'s balance: &#FFFFFF%balance%", "&#FFD447%player% &#FFFFFFMoney &#7A7A7A» &#33FF66%balance%",
                "&#D90429Insufficient funds. Your balance: &#FFFFFF%balance%", "&#D90429Not enough money. &#7A7A7ABalance: &#33FF66%balance%",
                "&#33FF66You paid &#FFFFFF%amount% &#33FF66to &#FFFFFF%player%&#33FF66.", "&#33FF66Sent &#FFFFFFMoney &#33FF66%amount% &#7A7A7Ato &#FFD447%player%&#7A7A7A.",
                "&#33FF66You received &#FFFFFF%amount% &#33FF66from &#FFFFFF%player%&#33FF66.", "&#33FF66Received &#FFFFFFMoney &#33FF66%amount% &#7A7A7Afrom &#FFD447%player%&#7A7A7A.",
                "&#33FF66Gave &#FFFFFF%amount% &#33FF66to &#FFFFFF%player%&#33FF66. New balance: &#FFFFFF%balance%", "&#33FF66Gave &#FFFFFFMoney &#33FF66%amount% &#7A7A7Ato &#FFD447%player%&#7A7A7A. Balance: &#33FF66%balance%",
                "&#33FF66Took &#FFFFFF%amount% &#33FF66from &#FFFFFF%player%&#33FF66. New balance: &#FFFFFF%balance%", "&#33FF66Took &#FFFFFFMoney &#33FF66%amount% &#7A7A7Afrom &#FFD447%player%&#7A7A7A. Balance: &#33FF66%balance%",
                "&#33FF66Set &#FFFFFF%player%&#33FF66's balance to &#FFFFFF%amount%&#33FF66.", "&#33FF66Set &#FFD447%player%&#7A7A7A's Money to &#33FF66%amount%&#7A7A7A.",
                "&#33FF66An admin updated your balance to &#FFFFFF%balance%&#33FF66.", "&#FFFFFFMoney &#7A7A7Aupdated to &#33FF66%balance%&#7A7A7A.",
                "&#33FF66Reset &#FFFFFF%player%&#33FF66's balance to &#FFFFFF%balance%&#33FF66.", "&#33FF66Reset &#FFD447%player%&#7A7A7A's Money to &#33FF66%balance%&#7A7A7A.");
        replaceText(new File(getDataFolder(), "pay-confirm-menu.yml"),
                "&#7A7A7Aᴀᴍᴏᴜɴᴛ &#FFFFFF%amount%", "&#7A7A7Aᴍᴏɴᴇʏ &#33FF66%amount_formatted%",
                "&#7A7A7Aᴀᴍᴏᴜɴᴛ &#33FF66%amount%", "&#7A7A7Aᴍᴏɴᴇʏ &#33FF66%amount_formatted%",
                "&#7A7A7Aʙᴀʟᴀɴᴄᴇ &#FFFFFF%balance%", "&#7A7A7Aʙᴀʟᴀɴᴄᴇ &#33FF66%balance_formatted%",
                "&#7A7A7Aᴀғᴛᴇʀ &#FFFFFF%after%", "&#7A7A7Aᴀғᴛᴇʀ &#33FF66%after_formatted%",
                "&#7A7A7Aᴛᴀʀɢᴇᴛ &#FFFFFF%player%", "&#7A7A7Aᴛᴀʀɢᴇᴛ &#FFD447%player%",
                "Close without sending money.", "Close without sending Money.",
                "Send this payment.", "Send this Money.");
    }

    private void replaceText(File file, String... replacements) {
        if (!file.isFile()) {
            return;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String updated = content;
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                updated = updated.replace(replacements[i], replacements[i + 1]);
            }
            if (!updated.equals(content)) {
                Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            getLogger().warning("[Economy] Failed to upgrade economy text file " + file.getName() + ": " + e.getMessage());
        }
    }
}
