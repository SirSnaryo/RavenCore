package com.kammoun.raven.settings.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerSettingRegistryTest {

    @Test
    void registersSettingsInPriorityThenIdOrder() {
        PlayerSettingRegistry registry = new PlayerSettingRegistry();

        registry.register(definition("teleport.tpa", "Teleport", 20));
        registry.register(definition("scoreboard.enabled", "Visual", 10));
        registry.register(definition("teleport.auto", "Teleport", 20));

        assertEquals(List.of("scoreboard.enabled", "teleport.auto", "teleport.tpa"),
                registry.definitions().stream().map(PlayerSettingDefinition::id).toList());
    }

    @Test
    void rejectsDuplicateSettingIds() {
        PlayerSettingRegistry registry = new PlayerSettingRegistry();
        registry.register(definition("teleport.tpa", "Teleport", 20));

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(definition("teleport.tpa", "Teleport", 30)));
    }

    private PlayerSettingDefinition definition(String id, String category, int priority) {
        return PlayerSettingDefinition.builder(id)
                .category(category)
                .displayName("Setting")
                .description(List.of("Description"))
                .material(Material.COMPASS)
                .priority(priority)
                .getter(player -> true)
                .setter((player, enabled) -> {
                })
                .build();
    }
}
