package com.kammoun.raven.teleport.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportSettingsTest {

    @Test
    void defaultsAcceptTpaAndTpahereButNotAutoAccept() {
        TeleportSettings settings = TeleportSettings.defaults(UUID.randomUUID());

        assertTrue(settings.acceptsTpa());
        assertTrue(settings.acceptsTpaHere());
        assertFalse(settings.autoAccept());
        assertTrue(settings.confirmMenu());
    }

    @Test
    void toggleChangesOnlyRequestedPreference() {
        TeleportSettings original = TeleportSettings.defaults(UUID.randomUUID());
        TeleportSettings changed = original.toggle(TeleportPreference.TPA_AUTO);

        assertTrue(changed.acceptsTpa());
        assertTrue(changed.acceptsTpaHere());
        assertTrue(changed.autoAccept());
        assertTrue(changed.confirmMenu());
        assertFalse(original.autoAccept());
    }

    @Test
    void toggleConfirmMenuDoesNotChangeRequestSettings() {
        TeleportSettings original = TeleportSettings.defaults(UUID.randomUUID());
        TeleportSettings changed = original.toggle(TeleportPreference.CONFIRM_MENU);

        assertTrue(changed.acceptsTpa());
        assertTrue(changed.acceptsTpaHere());
        assertFalse(changed.autoAccept());
        assertFalse(changed.confirmMenu());
    }
}
