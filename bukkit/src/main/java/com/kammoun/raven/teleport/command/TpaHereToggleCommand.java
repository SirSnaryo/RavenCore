package com.kammoun.raven.teleport.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.teleport.TeleportModule;
import com.kammoun.raven.teleport.config.TeleportMessage;
import com.kammoun.raven.teleport.model.TeleportPreference;
import com.kammoun.raven.teleport.model.TeleportSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TpaHereToggleCommand extends KCommand {

    private final TeleportModule module;

    public TpaHereToggleCommand(TeleportModule module) {
        super("tpaheretoggle", "raven.teleport.tpa", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        TeleportSettings settings = module.getSettingsService().toggle(player, TeleportPreference.TPA_HERE);
        TeleportMessage message = settings.acceptsTpaHere()
                ? TeleportMessage.TPA_HERE_TOGGLE_ON
                : TeleportMessage.TPA_HERE_TOGGLE_OFF;
        TeleportMessage action = settings.acceptsTpaHere()
                ? TeleportMessage.TPA_HERE_TOGGLE_ON_ACTIONBAR
                : TeleportMessage.TPA_HERE_TOGGLE_OFF_ACTIONBAR;
        module.getTeleportConfig().send(player, message);
        module.getTeleportConfig().actionBar(player, action);
        module.getTeleportConfig().sound(player, "toggle");
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
