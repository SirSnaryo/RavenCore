package com.kammoun.raven.scoreboard.model;

public record ScoreboardLine(String text, LineUpdateMode mode, int intervalTicks, String eventName) {

    public static ScoreboardLine parse(String text, Object update) {
        if (update == null) {
            return new ScoreboardLine(text, LineUpdateMode.STATIC, 0, null);
        }

        if (update instanceof Number number) {
            int ticks = Math.max(1, number.intValue());
            return new ScoreboardLine(text, LineUpdateMode.INTERVAL, ticks, null);
        }

        String raw = String.valueOf(update).trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("static")) {
            return new ScoreboardLine(text, LineUpdateMode.STATIC, 0, null);
        }

        if (raw.regionMatches(true, 0, "event:", 0, 6)) {
            String event = raw.substring(6).trim();
            return new ScoreboardLine(text, LineUpdateMode.EVENT, 0, event.isEmpty() ? null : event);
        }

        try {
            int ticks = Math.max(1, Integer.parseInt(raw));
            return new ScoreboardLine(text, LineUpdateMode.INTERVAL, ticks, null);
        } catch (NumberFormatException ignored) {
            return new ScoreboardLine(text, LineUpdateMode.STATIC, 0, null);
        }
    }
}
