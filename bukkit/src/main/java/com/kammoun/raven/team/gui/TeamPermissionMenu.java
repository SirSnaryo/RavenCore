package com.kammoun.raven.team.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.team.TeamModule;
import com.kammoun.raven.team.model.TeamPermission;
import com.kammoun.raven.team.model.TeamSnapshot;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TeamPermissionMenu extends KMenu {
    private final TeamModule module;
    private final TeamSnapshot team;
    private final TeamSnapshot.Member target;

    public TeamPermissionMenu(RavenCore plugin, TeamModule module, TeamSnapshot team, TeamSnapshot.Member target) {
        super(plugin, "team/gui/team-permission.yml");
        this.module = module;
        this.team = team;
        this.target = target;
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        Map<String, TeamPermission> items = new LinkedHashMap<>();
        items.put("edit-home", TeamPermission.EDIT_HOME);
        items.put("manage-members", TeamPermission.MANAGE_MEMBERS);
        items.put("toggle-pvp", TeamPermission.TOGGLE_PVP);
        items.put("visit-home", TeamPermission.VISIT_HOME);
        items.put("team-chat", TeamPermission.USE_TEAM_CHAT);
        items.forEach((key, permission) -> {
            MenuItem configured = configured("Items." + key);
            if (configured == null) return;
            ItemStack stack = replace(configured.getItemStack());
            ItemMeta meta = stack.getItemMeta();
            List<String> rawLore = meta.getLore();
            if (rawLore != null) {
                String replacement = target.has(permission) ? "&a&lON" : "&c&lOFF";
                List<String> newLore = rawLore.stream()
                        .map(line -> line.replace("%status%", replacement))
                        .toList();
                meta.setLore(newLore);
            }
            stack.setItemMeta(meta);
            setItem(MenuItem.of(new ItemBuilder(stack)).slot(configured.getSlot()).onClick(event -> {
                player.closeInventory();
                module.getService().setPermission(player, target.uuid(), permission, !target.has(permission),
                        () -> module.getService().query(player, refreshed ->
                                refreshed.ifPresent(value -> {
                                    TeamSnapshot.Member updated = value.member(target.uuid());
                                    if (updated != null) new TeamPermissionMenu(module.getApi(), module, value, updated).open(player);
                                })));
            }));
        });
        MenuItem kick = configured("Items.kick");
        if (kick != null) {
            setItem(MenuItem.of(new ItemBuilder(replace(kick.getItemStack()))).slot(kick.getSlot())
                    .onClick(event -> module.openKickConfirmation(player, target)));
        }
        MenuItem back = configured("Items.back");
        if (back != null) {
            setItem(MenuItem.of(new ItemBuilder(replace(back.getItemStack()))).slot(back.getSlot())
                    .onClick(event -> module.openMenu(player)));
        }
    }

    @Override
    public void open(Player player, String... ignored) {
        super.open(player, "%player%", target.lastName());
    }

    private MenuItem configured(String path) {
        return ItemReader.readMenuItem(config.getConfigurationSection(path));
    }

    private ItemStack replace(ItemStack source) {
        ItemStack stack = source.clone();
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(meta.getDisplayName().replace("%player%", target.lastName()));
        if (meta.getLore() != null) {
            meta.setLore(meta.getLore().stream().map(line -> line.replace("%player%", target.lastName())).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
