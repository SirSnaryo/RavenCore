package com.kammoun.raven.staffmode;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.staffmode.command.InvSeeCommand;
import com.kammoun.raven.staffmode.command.StaffCommand;
import com.kammoun.raven.staffmode.command.StaffModeCommand;
import com.kammoun.raven.staffmode.commandgate.StaffCommandGateListener;
import com.kammoun.raven.staffmode.gui.StaffMenu;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import com.kammoun.raven.staffmode.listener.StaffModeListener;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

@Getter
public class StaffModeModule extends KModule<RavenCore> {

    private StaffModeService service;
    private InvSeeService invSeeService;
    private StaffMenu staffMenu;

    public StaffModeModule() {
        super("staffmode");
    }

    @Override
    public void onModuleEnable() {
        saveDefaultConfig("staff-menu.yml");
        saveDefaultConfig("players-menu.yml");
        saveDefaultConfig("inventory-menu.yml");
        saveDefaultConfig("punishments-menu.yml");
        saveDefaultConfig("history-menu.yml");
        saveDefaultConfig("player-menu.yml");
        saveDefaultConfig("player-inventory-menu.yml");
        saveDefaultConfig("player-punishments-menu.yml");
        saveDefaultConfig("player-history-menu.yml");
        saveDefaultConfig("punishment-confirm.yml");
        migrateLegacyResources();
        migrateConfig();

        service = new StaffModeService(getConfig());
        invSeeService = new InvSeeService(api, getConfig());
        staffMenu = new StaffMenu(api, invSeeService);

        registerCommand(new StaffModeCommand(service), "staffmode");
        registerCommand(new StaffCommand(service, staffMenu, invSeeService, api), "staff");
        registerCommand(new InvSeeCommand(api, service, invSeeService), "invsee");
        registerEvent(new StaffModeListener(service, invSeeService));
        registerEvent(new StaffCommandGateListener(service, getConfig()));
        getLogger().info("[StaffMode] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (service != null) service.clear();
        if (invSeeService != null) invSeeService.clear();
        getLogger().info("[StaffMode] Disabled.");
    }

    private void migrateLegacyResources() {
        replaceLegacyResource("staff-menu.yml", List.of("boarder", "border", "devmode"));
        replaceLegacyResource("player-menu.yml", List.of("husksync", "invsee-view", "invsee-edit"));
        replaceLegacyResource("players-menu.yml", List.of("husk", "boarder", "border"));
        replaceResourceIfMissing("staff-menu.yml", "history:");
        replaceResourceIfMissing("player-menu.yml", "inventory-menu");
    }

    private void replaceLegacyResource(String fileName, List<String> markers) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) return;

        try {
            String content = Files.readString(file.toPath());
            String lower = content.toLowerCase(Locale.ROOT);
            boolean legacy = markers.stream().anyMatch(lower::contains);
            if (!legacy) return;

            String resourcePath = getName() + "/" + fileName;
            try (InputStream in = api.getResource(resourcePath)) {
                if (in == null) return;
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("[StaffMode] Updated legacy " + fileName + " to the current layout.");
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "[StaffMode] Could not update legacy " + fileName, e);
        }
    }

    private void replaceResourceIfMissing(String fileName, String requiredText) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) return;

        try {
            String content = Files.readString(file.toPath()).toLowerCase(Locale.ROOT);
            if (content.contains(requiredText.toLowerCase(Locale.ROOT))) return;

            String resourcePath = getName() + "/" + fileName;
            try (InputStream in = api.getResource(resourcePath)) {
                if (in == null) return;
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("[StaffMode] Refreshed " + fileName + " with the current menu layout.");
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "[StaffMode] Could not refresh " + fileName, e);
        }
    }

    private void migrateConfig() {
        FileConfiguration config = getConfig();
        boolean changed = false;

        String failed = config.getString("messages.external-command-failed", "");
        if (failed.toLowerCase(Locale.ROOT).contains("husksync")) {
            config.set("messages.external-command-failed", "&#D90429Saved data command failed. Check staffmode/config.yml.");
            changed = true;
        }
        String missing = config.getString("messages.external-missing", "");
        if (missing.toLowerCase(Locale.ROOT).contains("husksync") || missing.isBlank()) {
            config.set("messages.external-missing", "&#D90429Saved player data is not available.");
            changed = true;
        }
        if (!config.isList("external.inventory-commands")) {
            config.set("external.inventory-commands", List.of(
                    "inventory %player% %mode%",
                    "husksync:inventory %player% %mode%",
                    "userdata view %player%"
            ));
            changed = true;
        }
        if (!config.isList("external.enderchest-commands")) {
            config.set("external.enderchest-commands", List.of(
                    "enderchest %player% %mode%",
                    "husksync:enderchest %player% %mode%",
                    "userdata view %player%"
            ));
            changed = true;
        }
        if (!config.isList("external.snapshots-commands")) {
            config.set("external.snapshots-commands", List.of(
                    "userdata list %player%",
                    "husksync:userdata list %player%"
            ));
            changed = true;
        }

        if (changed) {
            saveConfig();
        }
    }
}
