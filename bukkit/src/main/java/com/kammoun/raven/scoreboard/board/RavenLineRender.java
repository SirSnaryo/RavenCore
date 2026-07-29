package com.kammoun.raven.scoreboard.board;

record RavenLineRender(String text, String numberFormat, Integer score) {

    static RavenLineRender parse(String rendered) {
        String safe = rendered == null ? "" : rendered;
        if (!safe.contains("||")) {
            return new RavenLineRender(safe, "", null);
        }

        String[] split = safe.split("\\|\\|", -1);
        String text = split.length > 0 ? split[0] : "";
        String numberFormat = split.length > 1 ? split[1].trim() : "";
        Integer score = split.length > 2 ? parseScore(split[2]) : null;
        return new RavenLineRender(text, numberFormat, score);
    }

    private static Integer parseScore(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
