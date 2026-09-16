package com.kammoun.raven.tpa;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.redis.event.TpaNotificationEvent;
import com.kammoun.raven.library.redis.event.TpaRequestEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.tpa.command.SpeedCommand;
import com.kammoun.raven.tpa.command.tp.TeleportCommand;
import com.kammoun.raven.tpa.command.tp.TeleportHereCommand;
import com.kammoun.raven.tpa.command.tpa.TpaAcceptCommand;
import com.kammoun.raven.tpa.command.tpa.TpaCancelCommand;
import com.kammoun.raven.tpa.command.tpa.TpaCommand;
import com.kammoun.raven.tpa.command.tpa.TpaDenyCommand;
import com.kammoun.raven.tpa.command.tpa.TpaHereCommand;
import com.kammoun.raven.tpa.command.toggle.TpAutoCommand;
import com.kammoun.raven.tpa.command.toggle.TpaHereToggleCommand;
import com.kammoun.raven.tpa.command.toggle.TpaToggleCommand;
import com.kammoun.raven.tpa.config.TeleportConfig;
import com.kammoun.raven.tpa.config.TeleportMessage;
import com.kammoun.raven.tpa.manager.TpaManager;
import com.kammoun.raven.tpa.menu.TpaConfirmMenu;
import com.kammoun.raven.tpa.model.TpaRequest;
import com.kammoun.raven.tpa.settings.TeleportSettingsRepository;
import com.kammoun.raven.tpa.settings.TeleportSettingsService;
import com.kammoun.raven.tpa.transfer.TeleportTransferStore;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class TeleportModule extends KModule<RavenCore> {

    private TeleportConfig teleportConfig;
    private TeleportTransferStore transferStore;
    private TeleportSettingsService settingsService;
    private TpaManager tpaManager;
    private TpaConfirmMenu tpaRequestMenu;
    private TpaConfirmMenu tpaHereRequestMenu;
    private TpaConfirmMenu tpaAcceptMenu;
    private TpaConfirmMenu tpaHereAcceptMenu;
    private SchedulerUtil.Task cleanupTask;

    public TeleportModule() {
        super("teleport");
    }

    @Override
    public void onModuleEnable() {
        loadTeleportState();
        api.getNetworkEventBus().subscribe(CrossServerTeleportEvent.class, this::onCrossServerTeleport);
        api.getNetworkEventBus().subscribe(TpaRequestEvent.class, this::onTpaRequest);
        api.getNetworkEventBus().subscribe(TpaNotificationEvent.class, this::onTpaNotification);
        onEvent(PlayerJoinEvent.class, this::onJoin);
        onEvent(PlayerQuitEvent.class, this::onQuit);
        onEvent(PlayerMoveEvent.class, this::onMove);
        registerCommand(new TeleportCommand(this, api.getNetworkPlayerManager()), "tp");
        registerCommand(new TeleportHereCommand(this, api.getNetworkPlayerManager()), "tphere");
        registerCommand(new TpaCommand(this, api.getNetworkPlayerManager()), "tpa");
        registerCommand(new TpaHereCommand(this, api.getNetworkPlayerManager()), "tpahere");
        registerCommand(new TpaAcceptCommand(this), "tpaccept");
        registerCommand(new TpaDenyCommand(this), "tpadeny");
        registerCommand(new TpaCancelCommand(this), "tpacancel");
        registerCommand(new TpaToggleCommand(this), "tpatoggle");
        registerCommand(new TpaHereToggleCommand(this), "tpaheretoggle");
        registerCommand(new TpAutoCommand(this), "tpauto");
        registerCommand(new SpeedCommand(), "speed");
        cleanupTask = SchedulerUtil.runTimer(() -> tpaManager.cleanup(), 1200L, 1200L);
        getLogger().info("[Teleport] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        api.getNetworkEventBus().unsubscribe(CrossServerTeleportEvent.class);
        api.getNetworkEventBus().unsubscribe(TpaRequestEvent.class);
        api.getNetworkEventBus().unsubscribe(TpaNotificationEvent.class);
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (tpaManager != null) {
            tpaManager.cleanup();
        }
        if (settingsService != null) {
            settingsService.saveAll();
        }
    }

    @Override
    public void reload() {
        if (settingsService != null) {
            settingsService.saveAll();
        }
        super.reload();
        loadTeleportState();
    }

    private void loadTeleportState() {
        teleportConfig = new TeleportConfig(getConfig());
        transferStore = new TeleportTransferStore(api, api.getRedisMessenger().getConnection().getPool());
        MySQLDBManager db = api.getMySqlManager();
        TeleportSettingsRepository repository = db != null && db.isConnected()
                ? new TeleportSettingsRepository(db)
                : null;
        settingsService = new TeleportSettingsService(api, repository, teleportConfig, getLogger());
        settingsService.init();
        tpaManager = new TpaManager(this, teleportConfig, settingsService);
        tpaRequestMenu = new TpaConfirmMenu(api, "teleport/tpa-request-menu.yml", this, TpaConfirmMenu.Mode.REQUEST, TpaRequest.Type.TPA);
        tpaHereRequestMenu = new TpaConfirmMenu(api, "teleport/tpahere-request-menu.yml", this, TpaConfirmMenu.Mode.REQUEST, TpaRequest.Type.TPA_HERE);
        tpaAcceptMenu = new TpaConfirmMenu(api, "teleport/tpa-accept-menu.yml", this, TpaConfirmMenu.Mode.ACCEPT, TpaRequest.Type.TPA);
        tpaHereAcceptMenu = new TpaConfirmMenu(api, "teleport/tpahere-accept-menu.yml", this, TpaConfirmMenu.Mode.ACCEPT, TpaRequest.Type.TPA_HERE);
        Bukkit.getOnlinePlayers().forEach(settingsService::load);
    }

    public void teleportToPlayer(CommandSender sender, NetworkPlayerInfo traveller, NetworkPlayerInfo destination) {
        Player localTraveller = Bukkit.getPlayer(traveller.getUuid());
        Player localDestination = Bukkit.getPlayer(destination.getUuid());

        if (localTraveller != null && localDestination != null) {
            localTraveller.teleportAsync(localDestination.getLocation());
            send(sender, "&#33FF66Teleported &#FFD447" + traveller.getPlayerName()
                    + " &#33FF66to &#FFD447" + destination.getPlayerName() + "&#33FF66.");
            return;
        }

        if (!transferStore.store(new TeleportTransferStore.PlayerTarget(traveller.getUuid(), destination.getServerId(), destination.getUuid()))) {
            send(sender, "&#D90429Failed to prepare cross-server teleport. Try again.");
            return;
        }

        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(traveller.getUuid().toString());
        event.setDestinationUuid(destination.getUuid().toString());
        event.setTargetServer(destination.getServerId());
        api.getNetworkEventBus().publish(event);

        send(sender, "&#33FF66Sending &#FFD447" + traveller.getPlayerName()
                + " &#33FF66to &#FFD447" + destination.getPlayerName() + "&#33FF66...");
    }

    public void teleportToCoordinates(CommandSender sender, NetworkPlayerInfo traveller, CoordinateTarget target) {
        Player localTraveller = Bukkit.getPlayer(traveller.getUuid());

        if (localTraveller != null && api.getServerId().equalsIgnoreCase(target.server())) {
            if (teleportLocal(sender, localTraveller, target)) {
                send(sender, "&#33FF66Teleported &#FFD447" + traveller.getPlayerName()
                        + " &#33FF66to &#FFFFFF" + target.shortLabel() + "&#33FF66.");
            }
            return;
        }

        if (!transferStore.store(new TeleportTransferStore.CoordinateTarget(
                traveller.getUuid(), target.server(), target.world(), target.x(), target.y(), target.z(), target.yaw(), target.pitch()
        ))) {
            send(sender, "&#D90429Failed to prepare cross-server teleport. Try again.");
            return;
        }

        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(traveller.getUuid().toString());
        event.setTargetServer(target.server());
        event.setWorld(target.world());
        event.setX(target.x());
        event.setY(target.y());
        event.setZ(target.z());
        event.setYaw(target.yaw());
        event.setPitch(target.pitch());
        api.getNetworkEventBus().publish(event);

        send(sender, "&#33FF66Sending &#FFD447" + traveller.getPlayerName()
                + " &#33FF66to &#FFFFFF" + target.shortLabel() + "&#33FF66.");
    }

    private void onCrossServerTeleport(CrossServerTeleportEvent event) {
        if (event.getTargetServer() != null && !api.getServerId().equalsIgnoreCase(event.getTargetServer())) {
            return;
        }

        UUID playerUuid = UUID.fromString(event.getPlayerUuid());
        Player traveller = Bukkit.getPlayer(playerUuid);
        if (traveller != null) {
            SchedulerUtil.execute(traveller, () -> {
                if (!transferStore.apply(traveller, this)) {
                    fallbackApplyTeleportEvent(traveller, event);
                }
            });
        }
    }

    private void onJoin(PlayerJoinEvent event) {
        settingsService.load(event.getPlayer());
        transferStore.apply(event.getPlayer(), this);
    }

    private void onQuit(PlayerQuitEvent event) {
        settingsService.unload(event.getPlayer());
        tpaManager.unload(event.getPlayer().getUniqueId());
    }

    private void onMove(PlayerMoveEvent event) {
        tpaManager.onMove(event);
    }

    private void onTpaRequest(TpaRequestEvent event) {
        if (event == null || event.getTargetServer() == null || !api.getServerId().equalsIgnoreCase(event.getTargetServer())) {
            return;
        }
        TpaRequest request;
        try {
            request = new TpaRequest(
                    UUID.fromString(event.getRequesterUuid()),
                    event.getRequesterName(),
                    event.getRequesterServer(),
                    event.getRequesterServerDisplayName(),
                    UUID.fromString(event.getTargetUuid()),
                    event.getTargetName(),
                    event.getTargetServer(),
                    event.getTargetServerDisplayName(),
                    TpaRequest.Type.valueOf(event.getType())
            );
        } catch (RuntimeException ignored) {
            return;
        }
        Player target = Bukkit.getPlayer(request.targetId());
        if (target == null) return;
        SchedulerUtil.execute(target, () -> tpaManager.receiveRequest(request));
    }

    private void onTpaNotification(TpaNotificationEvent event) {
        if (event == null || event.getRecipientUuid() == null || event.getMessageKey() == null) {
            return;
        }
        Player player;
        TeleportMessage message;
        try {
            player = Bukkit.getPlayer(UUID.fromString(event.getRecipientUuid()));
            message = TeleportMessage.valueOf(event.getMessageKey());
        } catch (RuntimeException ignored) {
            return;
        }
        if (player == null) return;
        SchedulerUtil.execute(player, () -> {
            teleportConfig.send(player, message, "%player%", event.getPlayerName());
            if (event.getActionBarKey() != null) {
                try {
                    teleportConfig.actionBar(player, TeleportMessage.valueOf(event.getActionBarKey()),
                            "%player%", event.getPlayerName());
                } catch (RuntimeException ignored) {
                }
            }
            if (event.getSoundKey() != null) {
                teleportConfig.sound(player, event.getSoundKey());
            }
        });
    }

    private void fallbackApplyTeleportEvent(Player traveller, CrossServerTeleportEvent event) {
        if (event.hasCoordinates()) {
            teleportLocal(Bukkit.getConsoleSender(), traveller, new CoordinateTarget(
                    event.getTargetServer() == null ? api.getServerId() : event.getTargetServer(),
                    event.getWorld(),
                    event.getX(),
                    event.getY(),
                    event.getZ(),
                    event.getYaw() == null ? 0.0f : event.getYaw(),
                    event.getPitch() == null ? 0.0f : event.getPitch()
            ));
            return;
        }

        if (event.getDestinationUuid() == null) return;
        try {
            Player destination = Bukkit.getPlayer(UUID.fromString(event.getDestinationUuid()));
            if (destination != null) {
                traveller.teleportAsync(destination.getLocation());
            }
        } catch (RuntimeException ignored) {
        }
    }

    boolean teleportLocal(CommandSender sender, Player player, CoordinateTarget target) {
        World world = target.world() == null || target.world().isBlank()
                ? player.getWorld()
                : Bukkit.getWorld(target.world());
        if (world == null) {
            send(sender, "&#D90429World &#FFD447" + target.world() + " &#D90429is not loaded.");
            return false;
        }
        if (api.getBorderModule() != null) {
            var cell = api.getBorderModule().getBorderConfig().cellFor(world.getEnvironment());
            if (cell != null && !cell.contains(target.x(), target.z())) {
                send(sender, "&#D90429Target location is outside this server border cell.");
                api.getLogger().warning("[Teleport] Rejected local coordinate teleport for "
                        + player.getName() + " at " + target.x() + "," + target.z()
                        + " because it is outside " + api.getServerId() + "'s border cell.");
                return false;
            }
        }
        Location location = new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        player.teleportAsync(location);
        return true;
    }

    public boolean teleportLocal(Player player, String world, double x, double y, double z, float yaw, float pitch) {
        return teleportLocal(Bukkit.getConsoleSender(), player, new CoordinateTarget(currentServer(), world, x, y, z, yaw, pitch));
    }

    public boolean queueOfflineCoordinateTeleport(CommandSender sender, UUID playerUuid, String playerName, CoordinateTarget target) {
        if (!transferStore.storeOffline(new TeleportTransferStore.CoordinateTarget(
                playerUuid, target.server(), target.world(), target.x(), target.y(), target.z(), target.yaw(), target.pitch()
        ))) {
            send(sender, "&#D90429Failed to prepare offline teleport. Try again.");
            return false;
        }
        send(sender, "&#33FF66Queued teleport for &#FFD447" + playerName
                + " &#33FF66to &#FFFFFF" + target.shortLabel() + "&#33FF66.");
        return true;
    }

    public boolean queueOfflinePlayerTeleport(CommandSender sender, UUID playerUuid, String playerName, NetworkPlayerInfo destination) {
        if (!transferStore.storeOffline(new TeleportTransferStore.PlayerTarget(
                playerUuid, destination.getServerId(), destination.getUuid()
        ))) {
            send(sender, "&#D90429Failed to prepare offline teleport. Try again.");
            return false;
        }
        send(sender, "&#33FF66Queued teleport for &#FFD447" + playerName
                + " &#33FF66to &#FFD447" + destination.getPlayerName() + "&#33FF66.");
        return true;
    }

    public void openAcceptMenu(Player player, TpaRequest request) {
        if (request.type() == TpaRequest.Type.TPA_HERE) {
            tpaHereAcceptMenu.open(player, request);
            return;
        }
        tpaAcceptMenu.open(player, request);
    }

    public boolean usesConfirmMenu(Player player) {
        return teleportConfig.confirmMenusEnabled()
                && settingsService.get(player.getUniqueId()).confirmMenu();
    }

    public Optional<TpaRequest> findRequest(Player target, String requesterName) {
        return api.getNetworkPlayerManager().findPlayer(requesterName)
                .flatMap(info -> tpaManager.requestFrom(target.getUniqueId(), info.getUuid()));
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatFormatter.color(message));
    }

    public List<String> tabPlayers(String prefix) {
        return api.getNetworkPlayerManager().completePlayerNames(prefix);
    }

    public List<String> tabPlayers(String prefix, String excludedName) {
        return api.getNetworkPlayerManager().completePlayerNames(prefix, excludedName);
    }

    public List<String> tabServers(String prefix) {
        String lower = prefix.toLowerCase();
        return Stream.concat(
                        Stream.of(api.getServerId()),
                        api.getNetworkPlayerManager().getOnlineNetworkPlayers().stream().map(NetworkPlayerInfo::getServerId)
                )
                .filter(server -> server != null && server.toLowerCase().startsWith(lower))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public String currentServer() {
        return api.getServerId();
    }

    public String currentServerDisplayName() {
        return api.getServerDisplayName();
    }

    public record CoordinateTarget(String server, String world, double x, double y, double z, float yaw, float pitch) {
        String shortLabel() {
            return trim(x) + " " + trim(y) + " " + trim(z) + " @ " + server;
        }

        private static String trim(double value) {
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        }
    }
}
