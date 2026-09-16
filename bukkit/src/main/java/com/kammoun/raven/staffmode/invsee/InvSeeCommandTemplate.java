package com.kammoun.raven.staffmode.invsee;

public final class InvSeeCommandTemplate {

    private InvSeeCommandTemplate() {
    }

    public static String parse(String template, String playerName) {
        return parse(template, playerName, false);
    }

    public static String parse(String template, String playerName, boolean edit) {
        String mode = edit ? "edit" : "";
        String command = template == null ? "" : template;
        command = command.replace("%player%", playerName)
                .replace("%mode%", mode)
                .replaceAll("\\s+", " ")
                .trim();
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
