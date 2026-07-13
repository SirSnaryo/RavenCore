package com.kammoun.raven.velocity.info;

import com.kammoun.raven.library.module.VModule;
import com.kammoun.raven.velocity.info.command.DiscordCommand;
import com.kammoun.raven.velocity.info.command.HelpCommand;
import com.kammoun.raven.velocity.info.command.MediaCommand;
import com.kammoun.raven.velocity.info.command.RulesCommand;
import com.kammoun.raven.velocity.info.command.StoreCommand;
import com.kammoun.raven.velocity.info.config.ConfigManager;
import lombok.Getter;

/**
 * Proxy-wide informational commands (/discord, /store, /rules, /media, /help).
 * Every player-facing line is read from {@code info/config.yml} — nothing is hardcoded.
 */
@Getter
public class InfoModule extends VModule {

    private ConfigManager configManager;

    public InfoModule() {
        super("info");
    }

    @Override
    public void onModuleEnable() {
        this.configManager = new ConfigManager(this);

        registerCommand("discord", new DiscordCommand(this));
        registerCommand("store", new StoreCommand(this));
        registerCommand("rules", new RulesCommand(this));
        registerCommand("media", new MediaCommand(this));
        registerCommand("help", new HelpCommand(this));

        getLogger().info("Info module enabled.");
    }

    @Override
    public void onModuleDisable() {}

    public void reload() {
        reloadConfig();
        configManager.load();
    }
}
