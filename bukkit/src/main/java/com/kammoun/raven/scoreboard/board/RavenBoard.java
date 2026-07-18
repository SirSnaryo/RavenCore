package com.kammoun.raven.scoreboard.board;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.scoreboard.model.LineUpdateMode;
import com.kammoun.raven.scoreboard.model.ScoreboardDefinition;
import com.kammoun.raven.scoreboard.model.ScoreboardLine;
import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RavenBoard {

    private final FastBoard board;
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
        board.updateLines(toComponents(renderedLines));
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
        String rendered = render(text);
        String[] split = rendered.split("\\\\n|\n", -1);
        for (String line : split) {
            renderedLines.add(line);
            if (renderedLines.size() >= 15) {
                return;
            }
        }
    }

    private List<String> toComponents(List<String> lines) {
        return lines;
    }
}
