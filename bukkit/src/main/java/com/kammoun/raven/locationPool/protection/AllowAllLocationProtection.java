package com.kammoun.raven.locationPool.protection;

import org.bukkit.Location;

public final class AllowAllLocationProtection implements LocationProtection {
    @Override
    public boolean isProtected(Location location) {
        return false;
    }
}
