package com.kammoun.raven.economy.gui;

import com.kammoun.raven.economy.model.EconomyTransactionLog;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class EconomyLogsHolder implements InventoryHolder {

    private final List<EconomyTransactionLog> logs;
    private final boolean biggest;
    private Inventory inventory;

    public EconomyLogsHolder(List<EconomyTransactionLog> logs, boolean biggest) {
        this.logs = List.copyOf(logs);
        this.biggest = biggest;
    }

    public List<EconomyTransactionLog> logs() {
        return logs;
    }

    public boolean biggest() {
        return biggest;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
