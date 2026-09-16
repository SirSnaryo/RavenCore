package com.kammoun.raven.scoreboard.board;

import com.kammoun.raven.scoreboard.model.LineUpdateMode;
import com.kammoun.raven.scoreboard.model.ScoreboardDefinition;
import com.kammoun.raven.scoreboard.model.ScoreboardLine;
import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RavenBoard {

    private static final int MAX_LINES = 15;
    private static final String[] LINE_HOLDERS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74",
            "\u00A75", "\u00A76", "\u00A77", "\u00A78", "\u00A79",
            "\u00A7a", "\u00A7b", "\u00A7c", "\u00A7d", "\u00A7e"
    };

    private final Player player;
    private final ScoreboardDefinition definition;
    private final ScoreboardPlaceholders placeholders;
    private final RavenPacketSidebar sidebar;
    private final RavenComponentCache componentCache = new RavenComponentCache();
    private final RavenLineRender[] sidebarLines = new RavenLineRender[MAX_LINES];
    private final List<RavenLineRender> renderedLines = new ArrayList<>();
    private final List<Integer> lineIndexes = new ArrayList<>();
    private String renderedTitle = "";
    private long tickAge;
    private boolean created;

    public RavenBoard(Player player, ScoreboardDefinition definition, ScoreboardPlaceholders placeholders) {
        this.player = player;
        this.definition = definition;
        this.placeholders = placeholders;
        this.sidebar = new RavenPacketSidebar(player);
    }

    public ScoreboardDefinition getDefinition() {
        return definition;
    }

    public void create() {
        renderedTitle = "";
        renderedLines.clear();
        lineIndexes.clear();
        updateTitle();
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
        Arrays.fill(sidebarLines, null);
        sidebar.delete();
        created = false;
    }

    private void updateTitle() {
        String title = render(definition.title());
        if (!created || !Objects.equals(title, renderedTitle)) {
            renderedTitle = title;
            Component component = componentCache.component(title);
            if (created) {
                sidebar.updateTitle(component);
            } else {
                sidebar.create(component);
                created = true;
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
        show(renderedIndex, value, MAX_LINES - renderedIndex);
    }

    private void applyRenderedLines() {
        int visible = Math.min(renderedLines.size(), MAX_LINES);
        for (int index = 0; index < visible; index++) {
            show(index, renderedLines.get(index), MAX_LINES - index);
        }
        for (int index = visible; index < MAX_LINES; index++) {
            hide(index);
        }
    }

    private boolean shouldHide(String rawText, String rendered) {
        return rawText != null && !rawText.isBlank() && rendered.isBlank();
    }

    private void show(int index, RavenLineRender render, int fallbackScore) {
        RavenLineRender previous = sidebarLines[index];
        if (Objects.equals(previous, render)) {
            return;
        }

        int scoreValue = render.score() == null ? fallbackScore : render.score();
        sidebar.setLine(
                LINE_HOLDERS[index],
                scoreValue,
                componentCache.component(render.text()),
                numberFormat(render.numberFormat())
        );
        sidebarLines[index] = render;
    }

    private void hide(int index) {
        if (sidebarLines[index] == null) {
            return;
        }
        sidebar.removeLine(LINE_HOLDERS[index]);
        sidebarLines[index] = null;
    }

    private Component numberFormat(String value) {
        if (value == null || value.isBlank()) {
            return Component.empty();
        }
        String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("blank") || lower.equals("hide")) {
            return Component.empty();
        }
        if (lower.equals("default") || lower.equals("vanilla")) {
            return null;
        }
        return componentCache.component(value);
    }
}
