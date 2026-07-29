package com.kammoun.raven.scoreboard.board;

import com.kammoun.raven.scoreboard.model.LineUpdateMode;
import com.kammoun.raven.scoreboard.model.ScoreboardDefinition;
import com.kammoun.raven.scoreboard.model.ScoreboardLine;
import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RavenBoard {

    private static final String OBJECTIVE_NAME = "raven_sb";
    private static final int MAX_LINES = 15;

    private final Player player;
    private final ScoreboardDefinition definition;
    private final ScoreboardPlaceholders placeholders;
    private final Scoreboard scoreboard;
    private final RavenComponentCache componentCache = new RavenComponentCache();
    private final RavenSidebarLine[] sidebarLines = new RavenSidebarLine[MAX_LINES];
    private final List<RavenLineRender> renderedLines = new ArrayList<>();
    private final List<Integer> lineIndexes = new ArrayList<>();
    private Objective objective;
    private String renderedTitle = "";
    private long tickAge;

    public RavenBoard(Player player, ScoreboardDefinition definition, ScoreboardPlaceholders placeholders) {
        this.player = player;
        this.definition = definition;
        this.placeholders = placeholders;
        this.scoreboard = createScoreboard();
        for (int i = 0; i < sidebarLines.length; i++) {
            sidebarLines[i] = new RavenSidebarLine(i);
        }
    }

    public ScoreboardDefinition getDefinition() {
        return definition;
    }

    public void create() {
        renderedTitle = "";
        renderedLines.clear();
        lineIndexes.clear();
        registerObjective();
        player.setScoreboard(scoreboard);
        updateAll();
    }

    public void tick(int elapsedTicks) {
        tickAge += elapsedTicks;
        for (int i = 0; i < definition.lines().size(); i++) {
            ScoreboardLine line = definition.lines().get(i);
            if (line.mode() == LineUpdateMode.INTERVAL && tickAge % line.intervalTicks() == 0) {
                updateConfiguredLine(i);
            }
        }
    }

    public void updateEvent(String eventName) {
        for (int i = 0; i < definition.lines().size(); i++) {
            ScoreboardLine line = definition.lines().get(i);
            if (line.mode() == LineUpdateMode.EVENT && eventName.equalsIgnoreCase(line.eventName())) {
                updateConfiguredLine(i);
            }
        }
    }

    public void updateAll() {
        updateTitle();
        renderedLines.clear();
        lineIndexes.clear();
        for (ScoreboardLine line : definition.lines()) {
            addRenderedLine(line.text());
        }
        applyRenderedLines();
    }

    public void delete() {
        Arrays.stream(sidebarLines).forEach(RavenSidebarLine::delete);
        if (registeredObjective()) {
            objective.unregister();
        }
    }

    private void updateTitle() {
        String title = render(definition.title());
        if (!Objects.equals(title, renderedTitle)) {
            renderedTitle = title;
            if (registeredObjective()) {
                objective.displayName(componentCache.component(title));
            }
        }
    }

    private String render(String text) {
        return placeholders.apply(player, text);
    }

    private void addRenderedLine(String rawText) {
        lineIndexes.add(renderedLines.size());
        String rendered = render(rawText);
        if (shouldHide(rawText, rendered)) {
            lineIndexes.set(lineIndexes.size() - 1, -1);
            return;
        }

        String[] split = rendered.split("\\\\n|\n", -1);
        for (String line : split) {
            renderedLines.add(RavenLineRender.parse(line));
            if (renderedLines.size() >= MAX_LINES) {
                return;
            }
        }
    }

    private void updateConfiguredLine(int configIndex) {
        if (configIndex < 0 || configIndex >= definition.lines().size()) return;
        if (configIndex >= lineIndexes.size()) {
            updateAll();
            return;
        }

        ScoreboardLine line = definition.lines().get(configIndex);
        if (line.text().contains("\\n") || line.text().contains("\n")) {
            updateAll();
            return;
        }

        int renderedIndex = lineIndexes.get(configIndex);
        String rendered = render(line.text());
        if (renderedIndex == -1 || shouldHide(line.text(), rendered)) {
            updateAll();
            return;
        }

        if (renderedIndex >= renderedLines.size()) {
            updateAll();
            return;
        }

        RavenLineRender value = RavenLineRender.parse(rendered);
        if (Objects.equals(renderedLines.get(renderedIndex), value)) {
            return;
        }

        renderedLines.set(renderedIndex, value);
        sidebarLines[renderedIndex].show(scoreboard, objective, componentCache, value, MAX_LINES - renderedIndex);
    }

    private Scoreboard createScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("Scoreboard manager is not available");
        }
        return manager.getNewScoreboard();
    }

    private void registerObjective() {
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
        if (existing != null) {
            existing.unregister();
        }
        objective = scoreboard.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                Component.empty(),
                RenderType.INTEGER
        );
        objective.numberFormat(NumberFormat.blank());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    private void applyRenderedLines() {
        int visible = Math.min(renderedLines.size(), MAX_LINES);
        for (int index = 0; index < visible; index++) {
            sidebarLines[index].show(scoreboard, objective, componentCache, renderedLines.get(index), MAX_LINES - index);
        }
        for (int index = visible; index < MAX_LINES; index++) {
            sidebarLines[index].hide(scoreboard);
        }
    }

    private boolean shouldHide(String rawText, String rendered) {
        return rawText != null && !rawText.isBlank() && rendered.isBlank();
    }

    private boolean registeredObjective() {
        return objective != null && scoreboard.getObjective(OBJECTIVE_NAME) != null;
    }
}
