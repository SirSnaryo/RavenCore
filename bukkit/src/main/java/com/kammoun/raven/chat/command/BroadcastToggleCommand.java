package com.kammoun.raven.chat.command;

import com.kammoun.raven.chat.ChatModule;
import com.kammoun.raven.chat.model.ChatSettings;
import com.kammoun.raven.library.command.KCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BroadcastToggleCommand extends KCommand {

    private final ChatModule module;

    public BroadcastToggleCommand(ChatModule module) {
        super("broadcasttoggle", "raven.chat", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        ChatSettings settings = module.getSettingsService().toggleBroadcast(player);
        module.sendToggleState(player, "broadcast", settings.broadcastEnabled());
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
