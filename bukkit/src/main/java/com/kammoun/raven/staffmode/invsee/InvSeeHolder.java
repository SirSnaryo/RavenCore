package com.kammoun.raven.staffmode.invsee;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class InvSeeHolder implements InventoryHolder {

    private final UUID viewerId;
    private final UUID targetId;
    private final StaffInventoryAccess access;
    private Inventory inventory;

    public InvSeeHolder(UUID viewerId, UUID targetId, StaffInventoryAccess access) {
        this.viewerId = viewerId;
        this.targetId = targetId;
        this.access = access;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public UUID targetId() {
        return targetId;
    }

    public StaffInventoryAccess access() {
        return access;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
