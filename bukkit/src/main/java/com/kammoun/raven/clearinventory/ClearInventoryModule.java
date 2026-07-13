package com.kammoun.raven.clearinventory;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.clearinventory.command.ClearInventoryCommand;
import com.kammoun.raven.clearinventory.config.ClearInventoryMessages;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.redis.event.ClearInventoryEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.UUID;

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
                api.getNetworkEventBus(),
                messages,
                requireConfirmation,
                confirmationTimeout
        ), "clear");

        api.getNetworkEventBus().subscribe(ClearInventoryEvent.class, event -> {
            Player target = Bukkit.getPlayer(UUID.fromString(event.getTargetUuid()));
            if (target == null) return;
            target.getInventory().clear();
            target.getInventory().setHelmet(null);
            target.getInventory().setChestplate(null);
            target.getInventory().setLeggings(null);
            target.getInventory().setBoots(null);
            target.getInventory().setItemInOffHand(null);
            target.updateInventory();
            target.sendMessage(messages.get("cleared-by-admin"));
        });

        getLogger().info("[ClearInventory] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        api.getNetworkEventBus().unsubscribe(ClearInventoryEvent.class);
        getLogger().info("[ClearInventory] Disabled.");
    }

    private YamlConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
}
