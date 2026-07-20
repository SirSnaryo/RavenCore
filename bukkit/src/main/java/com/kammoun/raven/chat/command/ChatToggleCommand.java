package com.kammoun.raven.chat.command;

import com.kammoun.raven.chat.ChatModule;
import com.kammoun.raven.chat.model.ChatSettings;
import com.kammoun.raven.library.command.KCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ChatToggleCommand extends KCommand {

    private final ChatModule module;

    public ChatToggleCommand(ChatModule module) {
        super("chattoggle", "raven.chat", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        ChatSettings settings = module.getSettingsService().toggleChat(player);
        module.sendToggleState(player, "chat", settings.chatEnabled());
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
