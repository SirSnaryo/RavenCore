package com.kammoun.raven.library.item;

import java.util.List;

public record ClickAction(
        List<String> commands,
        Type type
) {
    public enum Type { PLAYER, CONSOLE, BUNGEE }

    public static ClickAction player(String... commands) {
        return new ClickAction(List.of(commands), Type.PLAYER);
    }

    public static ClickAction console(String... commands) {
        return new ClickAction(List.of(commands), Type.CONSOLE);
    }

    public static ClickAction bungee(String... commands) {
        return new ClickAction(List.of(commands), Type.BUNGEE);
    }

    public boolean isEmpty() {
        return commands == null || commands.isEmpty();
    }
}