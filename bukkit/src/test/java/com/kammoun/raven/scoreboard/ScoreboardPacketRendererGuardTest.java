package com.kammoun.raven.scoreboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardPacketRendererGuardTest {

    @Test
    void scoreboardModuleDoesNotUseBukkitScoreboardApiForSidebarRendering() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/kammoun/raven/scoreboard");
        List<String> forbidden = List.of(
                "getNewScoreboard(",
                "registerNewObjective(",
                "registerNewTeam(",
                "setScoreboard("
        );

        try (var files = Files.walk(sourceRoot)) {
            List<String> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("ScoreboardPacketRendererGuardTest.java"))
                    .flatMap(path -> forbidden.stream()
                            .filter(token -> contains(path, token))
                            .map(token -> sourceRoot.relativize(path) + " contains " + token))
                    .toList();

            assertTrue(violations.isEmpty(), "Sidebar rendering must use packets on Canvas/Folia: " + violations);
        }
    }

    private static boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
