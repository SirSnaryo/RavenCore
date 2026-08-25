package com.kammoun.raven.scoreboard;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.scoreboard.board.RavenBoard;
import com.kammoun.raven.scoreboard.config.ScoreboardConfig;
import com.kammoun.raven.scoreboard.model.ScoreboardDefinition;
import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import com.kammoun.raven.scoreboard.stats.ScoreboardStatsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardService {

    private static final int RELOAD_BATCH_SIZE = 100;

    private final RavenCore plugin;
    private final ScoreboardPlaceholders placeholders;
    private final ScoreboardToggleStorage toggleStorage;
    private final Map<UUID, RavenBoard> boards = new ConcurrentHashMap<>();
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> forcedBoards = new ConcurrentHashMap<>();
    private ScoreboardConfig config;

    public ScoreboardService(RavenCore plugin, ScoreboardConfig config, ScoreboardToggleStorage toggleStorage,
                             ScoreboardStatsService stats) {
        this.plugin = plugin;
        this.config = config;
        this.toggleStorage = toggleStorage;
        this.placeholders = new ScoreboardPlaceholders(plugin, stats);
        this.disabled.addAll(toggleStorage.loadDisabled());
        this.forcedBoards.putAll(toggleStorage.loadBoards());
    }

    public void reload(ScoreboardConfig config) {
        SchedulerUtil.run(() -> {
            clearGlobal();
            this.config = config;
            if (config.isEnabled()) {
                showOnlineBatched(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getUniqueId)
                        .toList(), 0);
            }
        });
    }

    public void clear() {
        if (!plugin.isEnabled()) {
            boards.clear();
            return;
        }
        SchedulerUtil.run(this::clearGlobal);
    }

    private void clearGlobal() {
        List<Map.Entry<UUID, RavenBoard>> snapshot = new ArrayList<>(boards.entrySet());
        boards.clear();
        deleteBatched(snapshot, 0);
    }

    public void showIfAllowed(Player player) {
        if (!plugin.isEnabled()) {
            return;
        }
        SchedulerUtil.run(() -> showIfAllowedGlobal(player));
    }

    private void showIfAllowedGlobal(Player player) {
        if (!config.isEnabled()
                || !player.isOnline()
                || disabled.contains(player.getUniqueId())
                || config.isWorldDisabled(player.getWorld().getName())) {
            removeGlobal(player);
            return;
        }

        Optional<ScoreboardDefinition> definition = select(player);
        if (definition.isEmpty()) {
            removeGlobal(player);
            return;
        }

        RavenBoard current = boards.get(player.getUniqueId());
        if (current != null && current.getDefinition().name().equalsIgnoreCase(definition.get().name())) {
            return;
        }

        removeGlobal(player);
        RavenBoard board = new RavenBoard(player, definition.get(), placeholders);
        boards.put(player.getUniqueId(), board);
        board.create();
    }

    public void remove(Player player) {
        if (!plugin.isEnabled()) {
            boards.remove(player.getUniqueId());
            return;
        }
        SchedulerUtil.run(() -> removeGlobal(player));
    }

    private void removeGlobal(Player player) {
        RavenBoard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    public boolean toggle(Player player) {
        return setEnabled(player, disabled.contains(player.getUniqueId()));
    }

    public boolean isEnabled(Player player) {
        return !disabled.contains(player.getUniqueId());
    }

    public boolean setEnabled(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        if (enabled) {
            disabled.remove(uuid);
            saveDisabled();
            showIfAllowed(player);
            return true;
        }

        disabled.add(uuid);
        saveDisabled();
        remove(player);
        return false;
    }

    public boolean setBoard(Player player, String boardName) {
        if (!hasBoard(boardName)) {
            return false;
        }

        forcedBoards.put(player.getUniqueId(), boardName);
        saveDisabled();
        showIfAllowed(player);
        return true;
    }

    public void clearBoard(Player player) {
        forcedBoards.remove(player.getUniqueId());
        saveDisabled();
        showIfAllowed(player);
    }

    public Optional<String> currentBoard(Player player) {
        RavenBoard board = boards.get(player.getUniqueId());
        return board == null ? Optional.empty() : Optional.of(board.getDefinition().name());
    }

    public Optional<String> forcedBoard(Player player) {
        return Optional.ofNullable(forcedBoards.get(player.getUniqueId()));
    }

    public boolean hasBoard(String boardName) {
        return config.getScoreboards().stream()
                .anyMatch(definition -> definition.name().equalsIgnoreCase(boardName));
    }

    public boolean canUseBoard(Player player, String boardName) {
        return config.getScoreboards().stream()
                .filter(definition -> definition.name().equalsIgnoreCase(boardName))
                .anyMatch(definition -> definition.matches(player, placeholders));
    }

    public Set<String> boardNames() {
        Set<String> names = new HashSet<>();
        for (ScoreboardDefinition definition : config.getScoreboards()) {
            names.add(definition.name());
        }
        return names;
    }

    public void saveState() {
        saveDisabled();
    }

    public void tick(int elapsedTicks) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            RavenBoard board = boards.get(player.getUniqueId());
            if (board != null && player.isOnline()) {
                board.tick(elapsedTicks);
            }
        }
    }

    public void refreshSelections() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showIfAllowedGlobal(player);
        }
    }

    public void updateEvent(Player player, String eventName) {
        if (!plugin.isEnabled()) {
            return;
        }
        SchedulerUtil.run(() -> {
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

    public ScoreboardPlaceholders getPlaceholders() {
        return placeholders;
    }

    private Optional<ScoreboardDefinition> select(Player player) {
        String forced = forcedBoards.get(player.getUniqueId());
        if (forced != null) {
            Optional<ScoreboardDefinition> selected = config.getScoreboards().stream()
                    .filter(definition -> definition.name().equalsIgnoreCase(forced))
                    .findFirst();
            if (selected.isPresent()) {
                return selected;
            }
            forcedBoards.remove(player.getUniqueId());
            saveDisabled();
        }

        return config.getScoreboards().stream()
                .filter(definition -> definition.matches(player, placeholders))
                .findFirst();
    }

    private void saveDisabled() {
        toggleStorage.saveState(disabled, forcedBoards);
    }

    private void showOnlineBatched(List<UUID> players, int start) {
        int end = Math.min(players.size(), start + RELOAD_BATCH_SIZE);
        for (int i = start; i < end; i++) {
            Player player = Bukkit.getPlayer(players.get(i));
            if (player != null) {
                showIfAllowedGlobal(player);
            }
        }

        if (end < players.size()) {
            SchedulerUtil.runLater(() -> showOnlineBatched(players, end), 1L);
        }
    }

    private void deleteBatched(List<Map.Entry<UUID, RavenBoard>> entries, int start) {
        // On disable/shutdown the scheduler rejects new tasks (Folia throws), and we're
        // already on the region thread — delete every remaining board synchronously.
        if (!plugin.isEnabled()) {
            for (Map.Entry<UUID, RavenBoard> entry : entries.subList(start, entries.size())) {
                entry.getValue().delete();
            }
            return;
        }

        int end = Math.min(entries.size(), start + RELOAD_BATCH_SIZE);
        for (int i = start; i < end; i++) {
            Map.Entry<UUID, RavenBoard> entry = entries.get(i);
            entry.getValue().delete();
        }

        if (end < entries.size()) {
            SchedulerUtil.runLater(() -> deleteBatched(entries, end), 1L);
        }
    }
}
