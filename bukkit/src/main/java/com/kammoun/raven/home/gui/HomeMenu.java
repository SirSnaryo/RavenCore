package com.kammoun.raven.home.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.home.HomeModule;
import com.kammoun.raven.home.model.HomeRecord;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.team.model.TeamPermission;
import com.kammoun.raven.team.model.TeamSnapshot;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public final class HomeMenu extends KMenu {
    private final HomeModule module;
    private final List<HomeRecord> homes;
    private final Optional<TeamSnapshot> team;
    private final List<Integer> homeSlots;

    public HomeMenu(RavenCore plugin, HomeModule module, List<HomeRecord> homes, Optional<TeamSnapshot> team) {
        super(plugin, "home/gui/Home.yml");
        this.module = module;
        this.homes = homes;
        this.team = team;
        this.homeSlots = config.getIntegerList("Home-Slots");
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        int maxHomes = module.getService().limitFor(player);
        for (int i = 0; i < homeSlots.size(); i++) {
            int number = i + 1;
            String name = "home" + number;
            HomeRecord home = find(name);
            State state = i >= maxHomes ? State.NO_PERMISSION : home == null ? State.NO_HOME : State.HAS_HOME;
            MenuItem bed = read("Homes.Bed." + state.name(), homeSlots.get(i), number);
            MenuItem dye = read("Homes.Dye." + state.name(), homeSlots.get(i) + 9, number);
            if (bed != null) {
                bed.onClick(event -> {
                    if (state == State.NO_PERMISSION) return;
                    if (home == null) {
                        player.closeInventory();
                        module.getService().set(player, name, () -> module.openMenu(player));
                    } else module.getService().teleport(player, name);
                });
                setItem(bed);
            }
            if (dye != null) {
                dye.onClick(event -> {
                    if (state == State.NO_PERMISSION) return;
                    if (home == null) {
                        player.closeInventory();
                        module.getService().set(player, name, () -> module.openMenu(player));
                    } else {
                        new HomeDeleteConfirmMenu(module.getApi(),
                                () -> module.getService().delete(player, name),
                                () -> module.openMenu(player)).open(player);
                    }
                });
                setItem(dye);
            }
        }
        setTeamItems(player);
    }

    private void setTeamItems(Player player) {
        TeamSnapshot snapshot = team.orElse(null);
        TeamSnapshot.Member member = snapshot == null ? null : snapshot.member(player.getUniqueId());
        boolean visit = snapshot != null && (snapshot.isLeader(player.getUniqueId())
                || member != null && member.has(TeamPermission.VISIT_HOME));
        boolean edit = snapshot != null && (snapshot.isLeader(player.getUniqueId())
                || member != null && member.has(TeamPermission.EDIT_HOME));
        boolean hasHome = snapshot != null && snapshot.home() != null;
        State bedState = !visit || (!hasHome && !edit)
                ? State.NO_PERMISSION : hasHome ? State.HAS_HOME : State.NO_HOME;
        State dyeState = !edit ? State.NO_PERMISSION : hasHome ? State.HAS_HOME : State.NO_HOME;
        MenuItem bed = readConfigured("Team." + bedState.name());
        MenuItem dye = readConfigured("Team.Dye." + dyeState.name());
        if (bed != null) {
            bed.onClick(event -> {
                if (!visit) return;
                if (hasHome) module.getApi().getTeamModule().getService().home(player);
                else if (edit) {
                    player.closeInventory();
                    module.getApi().getTeamModule().getService().setHome(player,
                            () -> module.openMenu(player));
                }
            });
            setItem(bed);
        }
        if (dye != null) {
            dye.onClick(event -> {
                if (!edit) return;
                if (hasHome) {
                    new HomeDeleteConfirmMenu(module.getApi(),
                            () -> module.getApi().getTeamModule().getService().deleteHome(player,
                                    () -> module.openMenu(player)),
                            () -> module.openMenu(player)).open(player);
                } else {
                    player.closeInventory();
                    module.getApi().getTeamModule().getService().setHome(player,
                            () -> module.openMenu(player));
                }
            });
            setItem(dye);
        }
    }

    private HomeRecord find(String name) {
        return homes.stream().filter(home -> home.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private MenuItem read(String path, int slot, int homeId) {
        MenuItem template = readConfigured(path);
        if (template == null) return null;
        String display = template.getItemStack().getItemMeta().getDisplayName().replace("%home_id%", String.valueOf(homeId));
        return MenuItem.of(new ItemBuilder(template.getItemStack()).name(display)).slot(slot);
    }

    private MenuItem readConfigured(String path) {
        return ItemReader.readMenuItem(config.getConfigurationSection(path));
    }

    private enum State { HAS_HOME, NO_HOME, NO_PERMISSION }
}
