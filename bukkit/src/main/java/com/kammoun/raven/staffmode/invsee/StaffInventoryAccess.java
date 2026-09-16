package com.kammoun.raven.staffmode.invsee;

public final class StaffInventoryAccess {

    private final boolean canMutate;

    private StaffInventoryAccess(boolean canMutate) {
        this.canMutate = canMutate;
    }

    public static StaffInventoryAccess viewOnly() {
        return new StaffInventoryAccess(false);
    }

    public static StaffInventoryAccess editable() {
        return new StaffInventoryAccess(true);
    }

    public boolean canMutate() {
        return canMutate;
    }

    public boolean shouldCancelClick(boolean topInventoryClicked) {
        return !canMutate;
    }

    public boolean shouldCancelDrag(boolean touchesTopInventory) {
        return !canMutate;
    }
}
