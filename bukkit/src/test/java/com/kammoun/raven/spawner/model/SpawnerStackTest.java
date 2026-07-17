package com.kammoun.raven.spawner.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerStackTest {

    @Test
    void freshStackStartsDirtySoNewPlacementsCanBePersisted() {
        SpawnerStack stack = SpawnerStack.fresh(location(), type(), 3, null);

        assertTrue(stack.isDirty());
    }

    @Test
    void snapshotCopiesStoredItemsBeforeLaterMutations() {
        SpawnerStack stack = SpawnerStack.fresh(location(), type(), 1, null);
        stack.addItems(Material.IRON_INGOT, 10);

        SpawnerStack.Snapshot snapshot = stack.snapshot();
        stack.addItems(Material.IRON_INGOT, 5);

        assertEquals(10, snapshot.storedItems().get(Material.IRON_INGOT));
        assertEquals(15, stack.getItemAmount(Material.IRON_INGOT));
    }

    private static Location location() {
        return new Location(null, 10, 64, -20);
    }

    private static SpawnerType type() {
        return new SpawnerType(
                "IRON_GOLEM",
                "Iron Golem",
                null,
                null,
                null,
                30,
                0,
                1000,
                List.of(new SpawnerDrop(Material.IRON_INGOT, 1, 1.0))
        );
    }
}
