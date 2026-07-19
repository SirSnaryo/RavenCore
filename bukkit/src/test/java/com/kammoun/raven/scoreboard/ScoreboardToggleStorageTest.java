package com.kammoun.raven.scoreboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardToggleStorageTest {

    @TempDir
    File directory;

    @Test
    void savesAndLoadsDisabledPlayers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ScoreboardToggleStorage storage = new ScoreboardToggleStorage(directory, Logger.getAnonymousLogger());

        storage.saveDisabled(Set.of(first, second));

        Set<UUID> loaded = storage.loadDisabled();
        assertEquals(2, loaded.size());
        assertTrue(loaded.contains(first));
        assertTrue(loaded.contains(second));
    }
}
