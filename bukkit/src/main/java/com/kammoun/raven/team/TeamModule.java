package com.kammoun.raven.team;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.team.command.TeamCommand;
import com.kammoun.raven.team.config.TeamMessages;
import com.kammoun.raven.team.gui.TeamConfirmationMenu;
import com.kammoun.raven.team.gui.TeamMenu;
import com.kammoun.raven.team.listener.TeamListener;
import com.kammoun.raven.team.model.TeamSnapshot;
import com.kammoun.raven.team.model.TeamSort;
import com.kammoun.raven.team.service.TeamService;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.redis.event.TeamChatEvent;
import com.kammoun.raven.library.redis.event.TeamStateChangedEvent;
import com.kammoun.raven.library.redis.event.TeamNotificationEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.kammoun.raven.team.db.TeamRepository;
import com.kammoun.raven.team.db.TeamTable;
import lombok.Getter;

@Getter
public final class TeamModule extends KModule<RavenCore> {
    private TeamMessages messages;
    private TeamService service;
    private TeamListener listener;
    private int maxMembers;

    public TeamModule() {
        super("team");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().severe("[Team] Disabled because MySQL is unavailable.");
            return;
        }
        new TeamTable(api.getMySqlManager()).createTable();
        restoreGuiSection("gui/kick-confirm.yml", "Items.Confirm", "Items.Cancel");
        restoreGuiSection("gui/disband-confirm.yml", "Items.Confirm", "Items.Cancel");
        loadState();
        TeamCommand command = new TeamCommand(this);
        registerCommand(command, "team");
        registerEvent(listener);
        api.getNetworkEventBus().subscribe(TeamChatEvent.class, listener::deliver);
        api.getNetworkEventBus().subscribe(TeamStateChangedEvent.class, event -> service.refreshAll());
        api.getNetworkEventBus().subscribe(TeamNotificationEvent.class, this::deliverNotification);
        api.getServer().getOnlinePlayers().forEach(service::load);
        getLogger().info("[Team] Network-wide teams enabled.");
    }

    @Override
    public void onModuleDisable() {
        api.getNetworkEventBus().unsubscribe(TeamChatEvent.class);
        api.getNetworkEventBus().unsubscribe(TeamStateChangedEvent.class);
        api.getNetworkEventBus().unsubscribe(TeamNotificationEvent.class);
    }

    @Override
    public void reload() {
        reloadConfig();
        getLogger().info("[Team] Configuration changes apply after a server restart.");
    }

    public void openMenu(Player player) {
        openMenu(player, 1, TeamSort.JOIN_DATE, null);
    }

    public void openMenu(Player player, int page, TeamSort sort, String filter) {
        service.query(player, team -> {
            if (team.isEmpty()) {
                messages.send(player, "no-team-help");
                return;
            }
            new TeamMenu(api, this, team.get(), page, sort, filter).open(player);
        });
    }

    public void openKickConfirmation(Player player, TeamSnapshot.Member target) {
        new TeamConfirmationMenu(api, "kick-confirm.yml",
                () -> service.kick(player, target.lastName()),
                () -> openMenu(player)).open(player);
    }

    public void openDisbandConfirmation(Player player) {
        new TeamConfirmationMenu(api, "disband-confirm.yml",
                () -> service.disband(player),
                () -> openMenu(player)).open(player);
    }

    /**
     * The confirm/cancel items moved out of Java and into the gui files, so installs created before
     * that change have a menu file with no Items section at all. Merge in only what is missing.
     */
    private void restoreGuiSection(String resourcePath, String... paths) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.isFile()) return;

        YamlConfiguration menu = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults;
        try (InputStream stream = api.getResource("team/" + resourcePath)) {
            if (stream == null) return;
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            getLogger().warning("[Team] Could not read bundled " + resourcePath + ": " + e.getMessage());
            return;
        }

        boolean changed = false;
        for (String path : paths) {
            if (menu.contains(path) || !defaults.contains(path)) continue;
            copyPath(defaults, menu, path);
            changed = true;
        }
        if (!changed) return;

        try {
            menu.save(file);
            getLogger().info("[Team] Restored confirm menu items in " + resourcePath + ".");
        } catch (IOException e) {
            getLogger().warning("[Team] Could not save " + resourcePath + ": " + e.getMessage());
        }
    }

    private void copyPath(YamlConfiguration source, YamlConfiguration target, String path) {
        ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) {
            target.set(path, source.get(path));
            return;
        }
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) continue;
            target.set(path + "." + key, section.get(key));
        }
    }

    private void loadState() {
        messages = new TeamMessages(getConfig());
        maxMembers = Math.max(2, getConfig().getInt("max-team-members", 50));
        service = new TeamService(api, new TeamRepository(api.getMySqlManager()), messages,
                maxMembers,
                Math.max(30L, getConfig().getLong("invite-expiration-seconds", 300L)));
        listener = new TeamListener(api, service, messages,
                getConfig().getString("team-chat-format", "&8[&aTeam&8] &f%player%&7: &f%message%"));
    }

    private void deliverNotification(TeamNotificationEvent event) {
        try {
            Player player = api.getServer().getPlayer(UUID.fromString(event.getTargetUuid()));
            if (player != null && event.getMessage() != null) {
                SchedulerUtil.execute(player, () -> player.sendMessage(ChatFormatter.color(event.getMessage())));
            }
        } catch (RuntimeException ignored) {
        }
    }
}
