package com.kammoun.raven.settings.model;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public record PlayerSettingDefinition(
        String id,
        String category,
        String displayName,
        List<String> description,
        Material material,
        int priority,
        Function<Player, Boolean> getter,
        BiConsumer<Player, Boolean> setter
) {

    public PlayerSettingDefinition {
        id = requireText(id, "id");
        category = requireText(category, "category");
        displayName = requireText(displayName, "displayName");
        description = List.copyOf(description == null ? List.of() : description);
        material = Objects.requireNonNullElse(material, Material.COMPARATOR);
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
    }

    public boolean value(Player player) {
        return Boolean.TRUE.equals(getter.apply(player));
    }

    public void set(Player player, boolean enabled) {
        setter.accept(player, enabled);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Setting " + field + " cannot be blank");
        }
        return value;
    }

    public static final class Builder {
        private final String id;
        private String category = "General";
        private String displayName = "Setting";
        private List<String> description = List.of();
        private Material material = Material.COMPARATOR;
        private int priority = 100;
        private Function<Player, Boolean> getter;
        private BiConsumer<Player, Boolean> setter;

        private Builder(String id) {
            this.id = id;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(List<String> description) {
            this.description = description;
            return this;
        }

        public Builder material(Material material) {
            this.material = material;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder getter(Function<Player, Boolean> getter) {
            this.getter = getter;
            return this;
        }

        public Builder setter(BiConsumer<Player, Boolean> setter) {
            this.setter = setter;
            return this;
        }

        public PlayerSettingDefinition build() {
            return new PlayerSettingDefinition(id, category, displayName, description, material, priority, getter, setter);
        }
    }
}
