package com.kammoun.raven.scoreboard.board;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.scoreboard.model.LineUpdateMode;
import com.kammoun.raven.scoreboard.model.ScoreboardDefinition;
import com.kammoun.raven.scoreboard.model.ScoreboardLine;
import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RavenBoard {

    private static final String HIDDEN_SCORE = " ";

    private final FastBoard board;
    private final Player player;
    private final ScoreboardDefinition definition;
    private final ScoreboardPlaceholders placeholders;
    private final List<String> renderedLines = new ArrayList<>();
    private final List<Integer> lineIndexes = new ArrayList<>();
    private String renderedTitle = "";
    private long tickAge;

    public RavenBoard(Player player, ScoreboardDefinition definition, ScoreboardPlaceholders placeholders) {
        this.player = player;
        this.definition = definition;
        this.placeholders = placeholders;
        this.board = new FastBoard(player);
    }

    public ScoreboardDefinition getDefinition() {
        return definition;
    }

    public void create() {
        renderedTitle = "";
        renderedLines.clear();
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
        board.updateLines(renderedLines, hiddenScores(renderedLines.size()));
    }

    public void delete() {
        board.delete();
    }

    private void updateTitle() {
        String title = render(definition.title());
        if (!Objects.equals(title, renderedTitle)) {
            renderedTitle = title;
            board.updateTitle(title);
        }
    }

    private String render(String text) {
        return ChatFormatter.color(placeholders.apply(player, text));
    }

    private void addRenderedLine(String text) {
        lineIndexes.add(renderedLines.size());
        String rendered = render(text);
        String[] split = rendered.split("\\\\n|\n", -1);
        for (String line : split) {
            renderedLines.add(line);
            if (renderedLines.size() >= 15) {
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
        String value = render(line.text());
        if (renderedIndex >= renderedLines.size()) {
            updateAll();
            return;
        }

        if (Objects.equals(renderedLines.get(renderedIndex), value)) {
            return;
        }

        renderedLines.set(renderedIndex, value);
        board.updateLine(renderedIndex, value, HIDDEN_SCORE);
    }

    private List<String> hiddenScores(int size) {
        return Collections.nCopies(size, HIDDEN_SCORE);
    }
}
