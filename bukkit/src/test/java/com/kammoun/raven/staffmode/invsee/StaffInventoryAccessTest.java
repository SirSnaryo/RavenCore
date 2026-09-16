package com.kammoun.raven.staffmode.invsee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffInventoryAccessTest {

    @Test
    void viewOnlySessionsDenyInventoryMutations() {
        StaffInventoryAccess access = StaffInventoryAccess.viewOnly();

        assertFalse(access.canMutate());
        assertTrue(access.shouldCancelClick(true));
        assertTrue(access.shouldCancelDrag(true));
        assertTrue(access.shouldCancelClick(false));
        assertTrue(access.shouldCancelDrag(false));
    }

    @Test
    void editSessionsAllowTopInventoryMutations() {
        StaffInventoryAccess access = StaffInventoryAccess.editable();

        assertTrue(access.canMutate());
        assertFalse(access.shouldCancelClick(true));
        assertFalse(access.shouldCancelDrag(true));
    }
}
