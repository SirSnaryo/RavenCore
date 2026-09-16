package com.kammoun.raven.staffmode.commandgate;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffCommandGateTest {

    @Test
    void gatesConfiguredCommandsAndNamespacedAliases() {
        StaffCommandGate gate = new StaffCommandGate(Set.of("ban", "mute", "tool", "staff"));

        assertTrue(gate.isGated("/ban zYousef"));
        assertTrue(gate.isGated("worldedit:tool"));
        assertTrue(gate.isGated("/staff"));
    }

    @Test
    void leavesToggleAndPublicCommandsUngated() {
        StaffCommandGate gate = new StaffCommandGate(Set.of("ban", "mute", "staff"));

        assertFalse(gate.isGated("/staffmode"));
        assertFalse(gate.isGated("/stmode"));
        assertFalse(gate.isGated("/spawn"));
    }
}
