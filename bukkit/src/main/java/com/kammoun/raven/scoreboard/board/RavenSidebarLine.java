package com.kammoun.raven.scoreboard.board;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Objects;

final class RavenSidebarLine {

    private static final String[] ENTRIES = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74",
            "\u00A75", "\u00A76", "\u00A77", "\u00A78", "\u00A79",
            "\u00A7a", "\u00A7b", "\u00A7c", "\u00A7d", "\u00A7e"
    };

    private final int index;
    private final String entry;
    private final String teamName;
    private boolean visible;
    private Team team;
    private String lastText;
    private String lastNumberFormat;
    private int lastScore = Integer.MIN_VALUE;

    RavenSidebarLine(int index) {
        this.index = index;
        this.entry = ENTRIES[index];
        this.teamName = "raven_line_" + index;
    }

    void show(Scoreboard scoreboard, Objective objective, RavenComponentCache cache,
              RavenLineRender render, int fallbackScore) {
        Team currentTeam = team(scoreboard);
        if (!currentTeam.hasEntry(entry)) {
            currentTeam.addEntry(entry);
        }

        if (!visible || !Objects.equals(lastText, render.text())) {
            currentTeam.prefix(cache.component(render.text()));
            currentTeam.suffix(Component.empty());
            lastText = render.text();
        }

        int scoreValue = render.score() == null ? fallbackScore : render.score();
        String numberFormat = render.numberFormat() == null ? "" : render.numberFormat();
        if (!visible || lastScore != scoreValue || !Objects.equals(lastNumberFormat, numberFormat)) {
            Score score = objective.getScore(entry);
            score.setScore(scoreValue);
            score.numberFormat(numberFormat(numberFormat, cache));
            lastScore = scoreValue;
            lastNumberFormat = numberFormat;
        }
        visible = true;
    }

    void hide(Scoreboard scoreboard) {
        if (!visible) return;
        scoreboard.resetScores(entry);
        if (team != null) {
            team.unregister();
            team = null;
        }
        visible = false;
        lastText = null;
        lastNumberFormat = null;
        lastScore = Integer.MIN_VALUE;
    }

    void delete() {
        if (team != null) {
            team.unregister();
            team = null;
        }
        visible = false;
    }

    private Team team(Scoreboard scoreboard) {
        if (team != null) {
            return team;
        }

        Team existing = scoreboard.getTeam(teamName);
        team = existing == null ? scoreboard.registerNewTeam(teamName) : existing;
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        return team;
    }

    private NumberFormat numberFormat(String value, RavenComponentCache cache) {
        if (value == null || value.isBlank()) return NumberFormat.blank();
        String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("blank") || lower.equals("hide")) return NumberFormat.blank();
        if (lower.equals("default") || lower.equals("vanilla")) return null;
        return NumberFormat.fixed(cache.component(value));
    }
}
