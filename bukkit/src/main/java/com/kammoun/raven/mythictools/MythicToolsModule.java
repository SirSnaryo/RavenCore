package com.kammoun.raven.mythictools;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.mythictools.command.MythicToolsCommand;
import com.kammoun.raven.mythictools.config.MythicToolsConfig;
import com.kammoun.raven.mythictools.expiry.MythicExpiryCoordinator;
import com.kammoun.raven.mythictools.item.MythicItemService;
import com.kammoun.raven.mythictools.listener.MythicToolListener;
import com.kammoun.raven.library.sell.SellBridgeInterface;
import org.bukkit.command.CommandSender;

public final class MythicToolsModule extends KModule<RavenCore> {

    private MythicToolsConfig config;
    private MythicItemService items;
    private MythicExpiryCoordinator expiry;
    private SellBridgeInterface sellBridge;

    public MythicToolsModule() {
        super("mythictools");
    }

    @Override
    public void onModuleEnable() {
        config = new MythicToolsConfig(this);
        config.load();
        items = new MythicItemService(this);
        sellBridge = loadSellBridge();
        expiry = new MythicExpiryCoordinator(this);
        expiry.start();
        registerEvent(new MythicToolListener(this));
        registerCommand(new MythicToolsCommand(this), "mythictools");
        api.getServer().getOnlinePlayers().forEach(player -> expiry.discover(player, player.getInventory()));
        getLogger().info("[MythicTools] Loaded " + config.tools().size()
                + " tools with event-driven expiration.");
    }

    @Override
    public void onModuleDisable() {
        if (expiry != null) expiry.stop();
    }

    @Override
    public void reload() {
        reloadConfig();
        config.load();
        sellBridge = loadSellBridge();
        getLogger().info("[MythicTools] Configuration reloaded.");
    }

    public MythicToolsConfig config() {
        return config;
    }

    public MythicItemService items() {
        return items;
    }

    public MythicExpiryCoordinator expiry() {
        return expiry;
    }

    public SellBridgeInterface sellBridge() {
        return sellBridge;
    }


    public RavenCore getApi() {
        return api;
    }

    public void send(CommandSender sender, String path, String... replacements) {
        String message = config.message(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        if (!message.isBlank()) sender.sendMessage(ChatFormatter.color(message));
    }

    private SellBridgeInterface loadSellBridge() {
        if (!api.getServer().getPluginManager().isPluginEnabled("RavenSell")) {
            getLogger().warning("[MythicTools] RavenSell is unavailable; Sell Axe is disabled.");
            return SellBridgeInterface.unavailable();
        }
        try {
            return (SellBridgeInterface) Class.forName(
                    "com.kammoun.raven.library.sell.RavenSellBridge").getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("[MythicTools] RavenSell API could not be loaded; Sell Axe is disabled: "
                    + e.getMessage());
            return SellBridgeInterface.unavailable();
        }
    }
}
