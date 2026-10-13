package com.kammoun.raven.staffmode;

import org.junit.jupiter.api.Test;

import static com.kammoun.raven.staffmode.StaffModeService.canSee;
import static com.kammoun.raven.staffmode.VanishTier.OWNER_VANISH;
import static com.kammoun.raven.staffmode.VanishTier.VANISH;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffModeVisibilityTest {

    @Test
    void playersWithoutVanishSeeNobodyWhoIsVanished() {
        assertFalse(canSee(VANISH, false, false, false));
        assertFalse(canSee(OWNER_VANISH, false, false, false));
    }

    @Test
    void vanishedPlayersSeeEachOtherButNotOwnerVanish() {
        assertTrue(canSee(VANISH, false, false, true));
        assertFalse(canSee(OWNER_VANISH, false, false, true));
    }

    @Test
    void ownerVanishSeesBothTiers() {
        assertTrue(canSee(VANISH, false, true, false));
        assertTrue(canSee(OWNER_VANISH, false, true, false));
        assertTrue(canSee(OWNER_VANISH, false, true, true));
    }

    @Test
    void aVanishedPlayerAlwaysSeesThemselves() {
        assertTrue(canSee(VANISH, true, false, false));
        assertTrue(canSee(OWNER_VANISH, true, false, false));
    }

    @Test
    void playersWhoAreNotVanishedStayVisibleToEveryone() {
        assertTrue(canSee(null, false, false, false));
        assertTrue(canSee(null, false, false, true));
        assertTrue(canSee(null, false, true, false));
    }
}
