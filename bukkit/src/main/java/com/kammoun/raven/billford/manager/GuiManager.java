package com.kammoun.raven.billford.manager;

import com.kammoun.raven.billford.config.BillfordConfig;
import com.kammoun.raven.billford.config.BillfordMessages;
import com.kammoun.raven.billford.gui.BillfordGui;
import com.kammoun.raven.billford.gui.BillfordPresetEditorGui;
import com.kammoun.raven.billford.model.BillfordPreset;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns the Billford GUIs and dispatches their clicks. These are plain InventoryHolders (not KMenu),
 * so the plugin-wide KMenu dispatcher ignores them and this listener handles them directly.
 */
public class GuiManager implements Listener {

    private volatile BillfordConfig config;
    private volatile BillfordMessages messages;
    private final BillfordManager manager;
    private final JavaPlugin plugin;

    public GuiManager(BillfordConfig config, BillfordMessages messages, BillfordManager manager, JavaPlugin plugin) {
        this.config = config;
        this.messages = messages;
        this.manager = manager;
        this.plugin = plugin;
    }

    /** Point future GUIs at freshly reloaded config/messages. */
    public void updateRefs(BillfordConfig config, BillfordMessages messages) {
        this.config = config;
        this.messages = messages;
    }

    public void openMainGui(Player player) {
        BillfordPreset current = manager.getCurrentPreset();
        if (current == null) {
            messages.send(player, "no-preset");
            return;
        }
        new BillfordGui(config, messages, plugin).open(player, current);
    }

    public void openEditor(Player player, BillfordPreset preset) {
        new BillfordPresetEditorGui(config, messages, manager, plugin, preset).open(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BillfordGui gui) {
            gui.handleClick(event);
        } else if (holder instanceof BillfordPresetEditorGui editor) {
            editor.handleClick(event);
        }
    }

    public void closeAll() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof BillfordGui || holder instanceof BillfordPresetEditorGui) {
                player.closeInventory();
            }
        });
    }
}
