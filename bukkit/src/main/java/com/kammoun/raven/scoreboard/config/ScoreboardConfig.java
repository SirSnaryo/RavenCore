package com.kammoun.raven.scoreboard.config;

import com.kammoun.raven.scoreboard.model.ScoreboardDefinition;
import com.kammoun.raven.scoreboard.model.ScoreboardLine;
import com.kammoun.raven.scoreboard.model.ScoreboardRequirement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScoreboardConfig {

    private final boolean enabled;
    private final int joinDelayTicks;
    private final int updateIntervalTicks;
    private final int conditionCheckTicks;
    private final Set<String> disabledWorlds;
    private final List<ScoreboardDefinition> scoreboards;

    public ScoreboardConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("enabled", true);
        this.joinDelayTicks = Math.max(1, config.getInt("join-delay-ticks", 80));
        this.updateIntervalTicks = Math.max(1, config.getInt("update-interval-ticks", 20));
        this.conditionCheckTicks = Math.max(1, config.getInt("condition-check-ticks", 40));
        this.disabledWorlds = new HashSet<>(config.getStringList("disabled-worlds"));
        this.scoreboards = loadScoreboards(config.getConfigurationSection("scoreboards"));
    }

    private List<ScoreboardDefinition> loadScoreboards(ConfigurationSection section) {
        List<ScoreboardDefinition> definitions = new ArrayList<>();
        if (section == null) return definitions;

        for (String name : section.getKeys(false)) {
            ConfigurationSection board = section.getConfigurationSection(name);
            if (board == null) continue;

            List<ScoreboardRequirement> requirements = loadRequirements(board.getMapList("requirements"));
            List<ScoreboardLine> lines = loadLines(board.getMapList("lines"));
            definitions.add(new ScoreboardDefinition(
                    name,
                    board.getInt("priority", 0),
                    board.getString("title", ""),
                    requirements,
                    lines
            ));
        }

        definitions.sort(Comparator.comparingInt(ScoreboardDefinition::priority).reversed());
        return definitions;
    }

    private List<ScoreboardRequirement> loadRequirements(List<Map<?, ?>> maps) {
        List<ScoreboardRequirement> requirements = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            String type = string(map.get("type"), "");
            String value = string(map.get("value"), null);
            String placeholder = string(map.get("placeholder"), value);
            String expected = string(map.get("expected"), string(map.get("equals"), value));
            requirements.add(new ScoreboardRequirement(type, value, placeholder, expected));
        }
        return requirements;
    }

    private List<ScoreboardLine> loadLines(List<Map<?, ?>> maps) {
        List<ScoreboardLine> lines = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            String text = string(map.get("text"), "");
            Object update = map.get("update");
            lines.add(ScoreboardLine.parse(text, update));
        }
        return lines.size() > 15 ? lines.subList(0, 15) : lines;
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getJoinDelayTicks() {
        return joinDelayTicks;
    }

    public int getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    public int getConditionCheckTicks() {
        return conditionCheckTicks;
    }

    public boolean isWorldDisabled(String world) {
        return disabledWorlds.stream().anyMatch(name -> name.equalsIgnoreCase(world));
    }

    public List<ScoreboardDefinition> getScoreboards() {
        return scoreboards;
    }
}
