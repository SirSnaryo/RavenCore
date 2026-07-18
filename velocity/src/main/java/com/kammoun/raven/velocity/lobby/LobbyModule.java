package com.kammoun.raven.velocity.lobby;

import com.kammoun.raven.library.module.VModule;
import com.kammoun.raven.velocity.lobby.command.LobbyCommand;
import com.kammoun.raven.velocity.lobby.config.LobbyConfig;
import lombok.Getter;


@Getter
public class LobbyModule extends VModule {

    private LobbyConfig config;

    public LobbyModule() {
        super("lobby");
    }

    @Override
    public void onModuleEnable() {
        this.config = new LobbyConfig(this);

        registerCommand("lobby", new LobbyCommand(this), "hub");

        getLogger().info("Lobby module ");
    }

    @Override
    public void onModuleDisable() {}

    public void reload() {
        reloadConfig();
        config.load();
    }
}
