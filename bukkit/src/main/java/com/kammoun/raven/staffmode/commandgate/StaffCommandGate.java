package com.kammoun.raven.staffmode.commandgate;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class StaffCommandGate {

    private final Set<String> gatedCommands;

    public StaffCommandGate(Collection<String> gatedCommands) {
        this.gatedCommands = normalizeCommands(gatedCommands);
    }

    public boolean isGated(String input) {
        String root = rootCommand(input);
        if (root.isEmpty()) return false;
        return gatedCommands.contains(root) || gatedCommands.contains(simpleName(root));
    }

    public static Set<String> normalizeCommands(Collection<String> commands) {
        if (commands == null) return Set.of();
        return commands.stream()
                .map(StaffCommandGate::rootCommand)
                .filter(command -> !command.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String rootCommand(String input) {
        if (input == null) return "";
        String command = input.trim().toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) command = command.substring(1);
        int space = command.indexOf(' ');
        if (space >= 0) command = command.substring(0, space);
        if (command.equals("staffmode") || command.equals("stmode")) return "";
        return command;
    }

    private static String simpleName(String command) {
        int namespace = command.indexOf(':');
        return namespace >= 0 ? command.substring(namespace + 1) : command;
    }
}
