package com.kammoun.raven.clearinventory;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.clearinventory.command.ClearInventoryCommand;
import com.kammoun.raven.clearinventory.config.ClearInventoryMessages;
import com.kammoun.raven.library.module.KModule;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ClearInventoryModule extends KModule<RavenCore> {

    public ClearInventoryModule() {
        super("clearinventory");
    }

    @Override
    public void onModuleEnable() {
        ClearInventoryMessages messages = new ClearInventoryMessages(loadMessages());

        boolean requireConfirmation = getConfig().getBoolean("require-confirmation", true);
        int confirmationTimeout = getConfig().getInt("confirmation-timeout", 30);

        registerCommand(new ClearInventoryCommand(
                api.getNetworkPlayerManager(),
                messages,
                requireConfirmation,
                confirmationTimeout
        ), "clearinventory");

        getLogger().info("[ClearInventory] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        getLogger().info("[ClearInventory] Disabled.");
    }

    private YamlConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
}
