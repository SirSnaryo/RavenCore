package com.kammoun.raven.staffmode.commandgate;

import com.kammoun.raven.staffmode.StaffModeService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

public final class StaffCommandGateListener implements Listener {

    private final StaffModeService service;
    private final StaffCommandGate gate;
    private final boolean enabled;
    private final String bypassPermission;

    public StaffCommandGateListener(StaffModeService service, FileConfiguration config) {
        this.service = service;
        this.enabled = config.getBoolean("command-gate.enabled", true);
        this.bypassPermission = config.getString("command-gate.bypass-permission", "raven.staffmode.bypass-gate");
        this.gate = new StaffCommandGate(config.getStringList("command-gate.commands"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getMessage())) return;
        event.setCancelled(true);
        service.deny(event.getPlayer());
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (!enabled || service.isEnabled(player) || player.hasPermission(bypassPermission)) return;
        event.getCommands().removeIf(gate::isGated);
    }

    private boolean shouldBlock(Player player, String command) {
        return enabled
                && gate.isGated(command)
                && !service.isEnabled(player)
                && !player.hasPermission(bypassPermission);
    }
}
