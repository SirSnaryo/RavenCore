package com.kammoun.raven.team.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.team.config.TeamMessages;
import com.kammoun.raven.team.model.TeamPermission;
import com.kammoun.raven.team.model.TeamSnapshot;
import com.kammoun.raven.home.api.HomeDestination;
import com.kammoun.raven.library.redis.event.TeamStateChangedEvent;
import com.kammoun.raven.library.redis.event.TeamNotificationEvent;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.team.db.TeamRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class TeamService {
    private final RavenCore plugin;
    private final TeamRepository repository;
    private final TeamMessages messages;
    private final int maxMembers;
    private final long inviteDurationMillis;
    private final Map<UUID, TeamSnapshot> onlineTeams = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> teamChat = new ConcurrentHashMap<>();

    public TeamService(RavenCore plugin, TeamRepository repository, TeamMessages messages,
                       int maxMembers, long inviteDurationSeconds) {
        this.plugin = plugin;
        this.repository = repository;
        this.messages = messages;
        this.maxMembers = maxMembers;
        this.inviteDurationMillis = inviteDurationSeconds * 1000L;
    }

    public Optional<TeamSnapshot> cached(UUID player) {
        return Optional.ofNullable(onlineTeams.get(player));
    }

    public boolean isTeamChat(UUID player) {
        return teamChat.getOrDefault(player, false);
    }

    public void load(Player player) {
        query(player, team -> {
            if (team.isPresent()) onlineTeams.put(player.getUniqueId(), team.get());
            else onlineTeams.remove(player.getUniqueId());
        });
    }

    public void unload(UUID player) {
        onlineTeams.remove(player);
        teamChat.remove(player);
    }

    public void refreshAll() {
        plugin.getServer().getOnlinePlayers().forEach(this::load);
    }

    public void create(Player player, String name) {
        if (!name.matches("[A-Za-z0-9_-]{3,24}")) {
            messages.send(player, "invalid-name");
            return;
        }
        async(player, () -> repository.create(player.getUniqueId(), player.getName(), name), result -> {
            switch (result) {
                case CREATED -> {
                    messages.send(player, "created", "%team%", name);
                    changed(null);
                }
                case NAME_TAKEN -> messages.send(player, "name-taken");
                case ALREADY_IN_TEAM -> messages.send(player, "already-in-team");
            }
        });
    }

    public void invite(Player actor, String targetName) {
        SchedulerUtil.runAsync(() -> {
            Optional<NetworkPlayerInfo> target = plugin.getNetworkPlayerManager().findPlayer(targetName);
            SchedulerUtil.execute(actor, () -> {
                if (!actor.isOnline()) return;
                if (target.isEmpty()) {
                    messages.send(actor, "player-offline");
                    return;
                }
                NetworkPlayerInfo info = target.get();
                if (actor.getUniqueId().equals(info.getUuid())) {
                    messages.send(actor, "cannot-invite-self");
                    return;
                }
                inviteResolved(actor, info);
            });
        });
    }

    private void inviteResolved(Player actor, NetworkPlayerInfo target) {
        async(actor, () -> repository.invite(actor.getUniqueId(), target.getUuid(), target.getPlayerName(),
                        maxMembers, System.currentTimeMillis() + inviteDurationMillis), result -> {
                    switch (result) {
                        case SENT -> {
                            messages.send(actor, "invite-sent", "%player%", target.getPlayerName());
                            notify(target.getUuid(), messages.get("invite-received", "%player%", actor.getName()));
                        }
                        case NO_TEAM -> messages.send(actor, "no-team");
                        case NO_PERMISSION -> messages.send(actor, "no-permission");
                        case ALREADY_IN_TEAM -> messages.send(actor, "player-in-team");
                        case FULL -> messages.send(actor, "full");
                        case INVITES_DISABLED -> messages.send(actor, "invites-disabled-target");
                    }
                });
    }

    public void accept(Player player, String teamName) {
        async(player, () -> repository.accept(player.getUniqueId(), player.getName(), teamName,
                        maxMembers, System.currentTimeMillis()), result -> {
            switch (result) {
                case JOINED -> {
                    messages.send(player, "joined");
                    changed(null);
                }
                case NO_INVITE -> messages.send(player, "no-invite");
                case ALREADY_IN_TEAM -> messages.send(player, "already-in-team");
                case FULL -> messages.send(player, "full");
            }
        });
    }

    public void deny(Player player, String teamName) {
        async(player, () -> repository.deny(player.getUniqueId(), teamName),
                denied -> messages.send(player, denied ? "invite-denied" : "no-invite"));
    }

    public void leave(Player player) {
        mutate(player, () -> repository.leave(player.getUniqueId()), "left");
    }

    public void kick(Player actor, String targetName) {
        query(actor, team -> {
            if (team.isEmpty()) {
                messages.send(actor, "no-team");
                return;
            }
            TeamSnapshot.Member target = team.get().members().stream()
                    .filter(member -> member.lastName().equalsIgnoreCase(targetName)).findFirst().orElse(null);
            if (target == null) {
                messages.send(actor, "not-member");
                return;
            }
            async(actor, () -> repository.kick(actor.getUniqueId(), target.uuid()), result -> {
                handleMutation(actor, result, "kicked", "%player%", target.lastName());
                if (result == TeamRepository.MutationResult.SUCCESS) {
                    notify(target.uuid(), messages.get("you-were-kicked"));
                }
            });
        });
    }

    public void disband(Player player) {
        mutate(player, () -> repository.disband(player.getUniqueId()), "disbanded");
    }

    public void setHome(Player player) {
        setHome(player, null);
    }

    public void setHome(Player player, Runnable afterSuccess) {
        Location location = player.getLocation().clone();
        HomeDestination destination = HomeDestination.from("Team Home", plugin.getServerId(), location);
        mutate(player, () -> repository.setHome(player.getUniqueId(), destination), "home-set", afterSuccess);
    }

    public void deleteHome(Player player) {
        deleteHome(player, null);
    }

    public void deleteHome(Player player, Runnable afterSuccess) {
        mutate(player, () -> repository.setHome(player.getUniqueId(), null), "home-deleted", afterSuccess);
    }

    public void home(Player player) {
        query(player, team -> {
            if (team.isEmpty()) {
                messages.send(player, "no-team");
                return;
            }
            TeamSnapshot snapshot = team.get();
            TeamSnapshot.Member member = snapshot.member(player.getUniqueId());
            if (!snapshot.isLeader(player.getUniqueId()) && (member == null || !member.has(TeamPermission.VISIT_HOME))) {
                messages.send(player, "no-permission");
                return;
            }
            if (snapshot.home() == null) {
                messages.send(player, "no-home");
                return;
            }
            plugin.getHomeModule().getTeleportGateway().teleport(player, snapshot.home());
        });
    }

    public void togglePvp(Player player) {
        mutate(player, () -> repository.togglePvp(player.getUniqueId()), "pvp-toggled");
    }

    public void toggleInvites(Player player) {
        async(player, () -> repository.toggleInvites(player.getUniqueId()),
                enabled -> messages.send(player, enabled ? "invites-enabled" : "invites-disabled"));
    }

    public void toggleChat(Player player) {
        query(player, team -> {
            if (team.isEmpty()) {
                messages.send(player, "no-team");
                return;
            }
            TeamSnapshot.Member member = team.get().member(player.getUniqueId());
            if (!team.get().isLeader(player.getUniqueId())
                    && (member == null || !member.has(TeamPermission.USE_TEAM_CHAT))) {
                messages.send(player, "no-permission");
                return;
            }
            boolean enabled = !isTeamChat(player.getUniqueId());
            teamChat.put(player.getUniqueId(), enabled);
            messages.send(player, enabled ? "chat-enabled" : "chat-disabled");
        });
    }

    public void setPermission(Player actor, UUID target, TeamPermission permission, boolean enabled) {
        setPermission(actor, target, permission, enabled, null);
    }

    public void setPermission(Player actor, UUID target, TeamPermission permission, boolean enabled,
                              Runnable afterSuccess) {
        async(actor, () -> repository.setPermission(actor.getUniqueId(), target, permission, enabled), result -> {
            handleMutation(actor, result, enabled ? "permission-enabled" : "permission-disabled",
                    "%permission%", permission.name());
            if (result == TeamRepository.MutationResult.SUCCESS && afterSuccess != null) afterSuccess.run();
        });
    }

    public void query(Player player, Consumer<Optional<TeamSnapshot>> callback) {
        SchedulerUtil.runAsync(() -> {
            try {
                Optional<TeamSnapshot> team = repository.findByPlayer(player.getUniqueId());
                SchedulerUtil.execute(player, () -> {
                    if (player.isOnline()) callback.accept(team);
                });
            } catch (SQLException | RuntimeException e) {
                databaseFailure(player, e);
            }
        });
    }

    private void mutate(Player player, SqlSupplier<TeamRepository.MutationResult> action,
                        String success, String... replacements) {
        async(player, action, result -> handleMutation(player, result, success, replacements));
    }

    private void mutate(Player player, SqlSupplier<TeamRepository.MutationResult> action,
                        String success, Runnable afterSuccess, String... replacements) {
        async(player, action, result -> {
            handleMutation(player, result, success, replacements);
            if (result == TeamRepository.MutationResult.SUCCESS && afterSuccess != null) afterSuccess.run();
        });
    }

    private void handleMutation(Player player, TeamRepository.MutationResult result,
                                String success, String... replacements) {
        switch (result) {
            case SUCCESS -> {
                messages.send(player, success, replacements);
                changed(null);
            }
            case NO_TEAM -> messages.send(player, "no-team");
            case NO_PERMISSION -> messages.send(player, "no-permission");
            case NOT_MEMBER -> messages.send(player, "not-member");
            case LEADER_CANNOT_LEAVE -> messages.send(player, "leader-cannot-leave");
        }
    }

    private void changed(UUID teamId) {
        TeamStateChangedEvent event = new TeamStateChangedEvent();
        event.setTeamId(teamId == null ? "" : teamId.toString());
        plugin.getNetworkEventBus().publish(event);
        refreshAll();
    }

    private void notify(UUID target, String message) {
        TeamNotificationEvent event = new TeamNotificationEvent();
        event.setTargetUuid(target.toString());
        event.setMessage(ChatFormatter.color(message));
        plugin.getNetworkEventBus().publish(event);
    }

    private <T> void async(Player player, SqlSupplier<T> supplier, Consumer<T> callback) {
        SchedulerUtil.runAsync(() -> {
            try {
                T result = supplier.get();
                SchedulerUtil.execute(player, () -> {
                    if (player.isOnline()) callback.accept(result);
                });
            } catch (SQLException | RuntimeException e) {
                databaseFailure(player, e);
            }
        });
    }

    private void databaseFailure(Player player, Exception e) {
        plugin.getLogger().warning("[Team] Database operation failed for " + player.getName() + ": " + e.getMessage());
        SchedulerUtil.execute(player, () -> {
            if (player.isOnline()) messages.send(player, "database-error");
        });
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
