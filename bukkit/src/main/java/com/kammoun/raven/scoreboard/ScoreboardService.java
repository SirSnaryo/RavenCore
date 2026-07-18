package com.kammoun.raven.scoreboard;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.scoreboard.board.RavenBoard;
import com.kammoun.raven.scoreboard.config.ScoreboardConfig;
import com.kammoun.raven.scoreboard.model.ScoreboardDefinition;
import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardService {

    private final RavenCore plugin;
    private final ScoreboardPlaceholders placeholders;
    private final Map<UUID, RavenBoard> boards = new ConcurrentHashMap<>();
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();
    private ScoreboardConfig config;

    public ScoreboardService(RavenCore plugin, ScoreboardConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.placeholders = new ScoreboardPlaceholders(plugin);
    }

    public void reload(ScoreboardConfig config) {
        clear();
        this.config = config;
        if (config.isEnabled()) {
            Bukkit.getOnlinePlayers().forEach(this::showIfAllowed);
        }
    }

    public void clear() {
        boards.values().forEach(RavenBoard::delete);
        boards.clear();
    }

    public void showIfAllowed(Player player) {
        if (!config.isEnabled()
                || disabled.contains(player.getUniqueId())
                || config.isWorldDisabled(player.getWorld().getName())) {
            remove(player);
            return;
        }

        Optional<ScoreboardDefinition> definition = select(player);
        if (definition.isEmpty()) {
            remove(player);
            return;
        }

        RavenBoard current = boards.get(player.getUniqueId());
        if (current != null && current.getDefinition().name().equalsIgnoreCase(definition.get().name())) {
            current.updateAll();
            return;
        }

        remove(player);
        RavenBoard board = new RavenBoard(player, definition.get(), placeholders);
        boards.put(player.getUniqueId(), board);
        board.create();
    }

    public void remove(Player player) {
        RavenBoard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabled.remove(uuid)) {
            showIfAllowed(player);
            return true;
        }

        disabled.add(uuid);
        remove(player);
        return false;
    }

    public void tick(int elapsedTicks) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtil.execute(player, () -> {
                RavenBoard board = boards.get(player.getUniqueId());
                if (board != null && player.isOnline()) {
                    board.tick(elapsedTicks);
                }
            });
        }
    }

    public void refreshSelections() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtil.execute(player, () -> showIfAllowed(player));
        }
    }

    public void updateEvent(Player player, String eventName) {
        SchedulerUtil.execute(player, () -> {
            RavenBoard board = boards.get(player.getUniqueId());
            if (board != null && player.isOnline()) {
                board.updateEvent(eventName);
            }
        });
    }

    public void updateEvent(UUID uuid, String eventName) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            updateEvent(player, eventName);
        }
    }

    public Set<String> eventNames() {
        Set<String> names = new HashSet<>();
        for (ScoreboardDefinition definition : config.getScoreboards()) {
            definition.lines().stream()
                    .filter(line -> line.eventName() != null && !line.eventName().isBlank())
                    .forEach(line -> names.add(line.eventName()));
        }
        return names;
    }

    private Optional<ScoreboardDefinition> select(Player player) {
        return config.getScoreboards().stream()
                .filter(definition -> definition.matches(player, placeholders))
                .findFirst();
    }
}
