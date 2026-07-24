package com.kammoun.raven.economy.command;

import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.settings.EconomyPlayerSettings;
import com.kammoun.raven.economy.settings.EconomySettingsService;
import com.kammoun.raven.library.command.KCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PayMessageCommand extends KCommand {

    private final EconomySettingsService settings;
    private final EconomyMessages messages;

    public PayMessageCommand(EconomySettingsService settings, EconomyMessages messages) {
        super("paymsg", null, true);
        this.settings = settings;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        EconomyPlayerSettings updated = settings.toggleMessages(player.getUniqueId());
        player.sendMessage(messages.get(updated.paymentMessages() ? "settings.paymsg-on" : "settings.paymsg-off"));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
