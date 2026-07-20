package com.kammoun.raven.settings.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSettingRegistry {

    private final Map<String, PlayerSettingDefinition> definitions = new ConcurrentHashMap<>();

    public void register(PlayerSettingDefinition definition) {
        PlayerSettingDefinition existing = definitions.putIfAbsent(definition.id().toLowerCase(), definition);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate player setting id: " + definition.id());
        }
    }

    public Optional<PlayerSettingDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toLowerCase()));
    }

    public List<PlayerSettingDefinition> definitions() {
        List<PlayerSettingDefinition> ordered = new ArrayList<>(definitions.values());
        ordered.sort(Comparator.comparingInt(PlayerSettingDefinition::priority)
                .thenComparing(PlayerSettingDefinition::id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(ordered);
    }

    public void clear() {
        definitions.clear();
    }
}
