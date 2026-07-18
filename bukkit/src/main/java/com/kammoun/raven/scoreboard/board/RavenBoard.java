package com.kammoun.raven.scoreboard.board;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.scoreboard.model.LineUpdateMode;
import com.kammoun.raven.scoreboard.model.ScoreboardDefinition;
import com.kammoun.raven.scoreboard.model.ScoreboardLine;
import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RavenBoard {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] ENTRIES = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r",
            "§5§r", "§6§r", "§7§r", "§8§r", "§9§r",
            "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Team> teams = new ArrayList<>();
    private final Player player;
    private final ScoreboardDefinition definition;
    private final ScoreboardPlaceholders placeholders;
    private final List<String> renderedLines = new ArrayList<>();
    private String renderedTitle = "";
    private long tickAge;

    public RavenBoard(Player player, ScoreboardDefinition definition, ScoreboardPlaceholders placeholders) {
        this.player = player;
        this.definition = definition;
        this.placeholders = placeholders;
        this.scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager(), "scoreboard manager")
                .getNewScoreboard();
        this.objective = scoreboard.registerNewObjective(
                "raven",
                Criteria.DUMMY,
                Component.empty(),
                RenderType.INTEGER
        );
        this.objective.numberFormat(NumberFormat.blank());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < ENTRIES.length; i++) {
            Team team = scoreboard.registerNewTeam("raven_" + i);
            team.addEntry(ENTRIES[i]);
            teams.add(team);
        }
    }

    public ScoreboardDefinition getDefinition() {
        return definition;
    }

    public void create() {
        renderedTitle = "";
        renderedLines.clear();
        updateAll();
        player.setScoreboard(scoreboard);
    }

    public void tick(int elapsedTicks) {
        tickAge += elapsedTicks;
        for (int i = 0; i < definition.lines().size(); i++) {
            ScoreboardLine line = definition.lines().get(i);
            if (line.mode() == LineUpdateMode.INTERVAL && tickAge % line.intervalTicks() == 0) {
                updateAll();
                return;
            }
        }
    }

    public void updateEvent(String eventName) {
        for (ScoreboardLine line : definition.lines()) {
            if (line.mode() == LineUpdateMode.EVENT && eventName.equalsIgnoreCase(line.eventName())) {
                updateAll();
                return;
            }
        }
    }

    public void updateAll() {
        updateTitle();
        renderedLines.clear();
        for (ScoreboardLine line : definition.lines()) {
            addRenderedLine(line.text());
        }
        applyLines();
    }

    public void delete() {
        if (player.isOnline() && player.getScoreboard() == scoreboard) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void updateTitle() {
        String title = render(definition.title());
        if (!Objects.equals(title, renderedTitle)) {
            renderedTitle = title;
            objective.displayName(component(title));
        }
    }

    private String render(String text) {
        return ChatFormatter.color(placeholders.apply(player, text));
    }

    private void addRenderedLine(String text) {
        String rendered = render(text);
        String[] split = rendered.split("\\\\n|\n", -1);
        for (String line : split) {
            renderedLines.add(line);
            if (renderedLines.size() >= 15) {
                return;
            }
        }
    }

    private void applyLines() {
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int visible = Math.min(renderedLines.size(), ENTRIES.length);
        for (int i = 0; i < ENTRIES.length; i++) {
            Team team = teams.get(i);
            String text = i < visible ? renderedLines.get(i) : "";
            team.prefix(component(text));

            if (i < visible) {
                objective.getScore(ENTRIES[i]).setScore(visible - i);
            }
        }
    }

    private Component component(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }
}
