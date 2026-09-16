package com.kammoun.raven.staffmode.invsee;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InvSeeService {

    private static final int SIZE = 54;
    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final Map<UUID, InvSeeHolder> openSessions = new ConcurrentHashMap<>();

    public InvSeeService(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open(Player viewer, Player target, boolean editRequested) {
        if (!viewer.hasPermission("raven.staffmode.invsee.view")) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-view-permission", "&#D90429You cannot inspect inventories.")));
            return;
        }

        boolean editable = editRequested && viewer.hasPermission("raven.staffmode.invsee.edit");
        if (editRequested && !editable) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-edit-permission", "&#D90429You can view this inventory, but cannot edit it.")));
        }
        if (editable) {
            viewer.openInventory(target.getInventory());
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.edit-opened", "&#33FF66Editing &#FFD447%player%&#33FF66's inventory.")
                    .replace("%player%", target.getName())));
            plugin.getLogger().info("[StaffMode] " + viewer.getName() + " opened live InvSee edit for " + target.getName() + ".");
            return;
        }

        StaffInventoryAccess access = StaffInventoryAccess.viewOnly();
        InvSeeHolder holder = new InvSeeHolder(viewer.getUniqueId(), target.getUniqueId(), access);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                ChatFormatter.menuTitle("&8InvSee > " + target.getName()));
        holder.inventory(inventory);
        fillFromTarget(inventory, target);
        openSessions.put(viewer.getUniqueId(), holder);
        viewer.openInventory(inventory);
    }

    public void openOffline(Player viewer, String targetName, boolean editRequested) {
        if (!viewer.hasPermission("raven.staffmode.invsee.offline")) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-offline-permission", "&#D90429You cannot inspect offline inventories.")));
            return;
        }
        if (!externalInventoryAvailable()) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.external-missing", "&#D90429Saved player data is not available.")));
            return;
        }

        boolean editable = editRequested && viewer.hasPermission("raven.staffmode.invsee.edit");
        if (editRequested && !editable) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-edit-permission", "&#D90429You can view this inventory, but cannot edit it.")));
        }
        performExternal(viewer, commands("external.inventory-commands",
                List.of("inventory %player% %mode%", "husksync:inventory %player% %mode%", "userdata view %player%")),
                targetName, editable);
    }

    public void openSnapshots(Player viewer, String targetName) {
        if (!viewer.hasPermission("raven.staffmode.invsee.restore")) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-restore-permission", "&#D90429You cannot restore inventory snapshots.")));
            return;
        }
        if (!externalInventoryAvailable()) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.external-missing", "&#D90429Saved player data is not available.")));
            return;
        }
        performExternal(viewer, commands("external.snapshots-commands",
                List.of("userdata list %player%", "husksync:userdata list %player%")), targetName, false);
    }

    public void openEnderChest(Player viewer, Player target, boolean editRequested) {
        if (!viewer.hasPermission("raven.staffmode.enderchest.view")) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-view-permission", "&#D90429You cannot inspect inventories.")));
            return;
        }

        boolean editable = editRequested && viewer.hasPermission("raven.staffmode.enderchest.edit");
        if (editRequested && !editable) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-edit-permission", "&#D90429You can view this inventory, but cannot edit it.")));
        }
        if (editable) {
            viewer.openInventory(target.getEnderChest());
            return;
        }

        StaffInventoryAccess access = StaffInventoryAccess.viewOnly();
        InvSeeHolder holder = new InvSeeHolder(viewer.getUniqueId(), target.getUniqueId(), access);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                ChatFormatter.menuTitle("&8EnderChest > " + target.getName()));
        holder.inventory(inventory);
        ItemStack[] contents = target.getEnderChest().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            inventory.setItem(slot, clone(contents[slot]));
        }
        openSessions.put(viewer.getUniqueId(), holder);
        viewer.openInventory(inventory);
    }

    public void openOfflineEnderChest(Player viewer, String targetName, boolean editRequested) {
        if (!viewer.hasPermission("raven.staffmode.enderchest.view")) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-view-permission", "&#D90429You cannot inspect inventories.")));
            return;
        }
        if (!externalInventoryAvailable()) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.external-missing", "&#D90429Saved player data is not available.")));
            return;
        }

        boolean editable = editRequested && viewer.hasPermission("raven.staffmode.enderchest.edit");
        if (editRequested && !editable) {
            viewer.sendMessage(ChatFormatter.color(config.getString("messages.no-edit-permission", "&#D90429You can view this inventory, but cannot edit it.")));
        }
        performExternal(viewer, commands("external.enderchest-commands",
                List.of("enderchest %player% %mode%", "husksync:enderchest %player% %mode%", "userdata view %player%")),
                targetName, editable);
    }

    private boolean externalInventoryAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("HuskSync");
    }

    private List<String> commands(String path, List<String> fallback) {
        List<String> configured = config.getStringList(path);
        return configured == null || configured.isEmpty() ? fallback : configured;
    }

    private void performExternal(Player viewer, List<String> templates, String targetName, boolean edit) {
        List<String> failures = new ArrayList<>();
        for (String template : templates) {
            String command = InvSeeCommandTemplate.parse(template, targetName, edit);
            if (command.isBlank()) continue;
            try {
                if (viewer.performCommand(command)) {
                    return;
                }
                failures.add("/" + command + " returned false");
            } catch (RuntimeException error) {
                failures.add("/" + command + " -> " + error.getMessage());
            }
        }
        viewer.sendMessage(ChatFormatter.color(config.getString("messages.external-command-failed",
                "&#D90429Saved data command failed. Check staffmode/config.yml.")));
        plugin.getLogger().warning("[StaffMode] Saved data command failed for " + targetName + ": " + String.join(" | ", failures));
    }

    public void applyAndClose(InvSeeHolder holder) {
        openSessions.remove(holder.viewerId());
    }

    public void clear() {
        openSessions.clear();
    }

    private void fillFromTarget(Inventory inventory, Player target) {
        ItemStack[] contents = target.getInventory().getContents();
        for (int slot = 0; slot < Math.min(36, contents.length); slot++) {
            inventory.setItem(slot, clone(contents[slot]));
        }
        ItemStack[] armor = target.getInventory().getArmorContents();
        for (int i = 0; i < Math.min(4, armor.length); i++) {
            inventory.setItem(36 + i, clone(armor[i]));
        }
        inventory.setItem(40, clone(target.getInventory().getItemInOffHand()));
        for (int slot = 41; slot < SIZE; slot++) {
            inventory.setItem(slot, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
