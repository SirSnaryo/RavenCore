package com.kammoun.raven.teleport.model;

import java.util.UUID;

public record TeleportSettings(UUID uuid, boolean acceptsTpa, boolean acceptsTpaHere,
                               boolean autoAccept, boolean confirmMenu) {

    public static TeleportSettings defaults(UUID uuid) {
        return new TeleportSettings(uuid, true, true, false, true);
    }

    public TeleportSettings toggle(TeleportPreference preference) {
        return switch (preference) {
            case TPA -> with(preference, !acceptsTpa);
            case TPA_HERE -> with(preference, !acceptsTpaHere);
            case TPA_AUTO -> with(preference, !autoAccept);
            case CONFIRM_MENU -> with(preference, !confirmMenu);
        };
    }

    public TeleportSettings with(TeleportPreference preference, boolean enabled) {
        return switch (preference) {
            case TPA -> new TeleportSettings(uuid, enabled, acceptsTpaHere, autoAccept, confirmMenu);
            case TPA_HERE -> new TeleportSettings(uuid, acceptsTpa, enabled, autoAccept, confirmMenu);
            case TPA_AUTO -> new TeleportSettings(uuid, acceptsTpa, acceptsTpaHere, enabled, confirmMenu);
            case CONFIRM_MENU -> new TeleportSettings(uuid, acceptsTpa, acceptsTpaHere, autoAccept, enabled);
        };
    }

    public boolean accepts(TpaRequest.Type type) {
        return type == TpaRequest.Type.TPA ? acceptsTpa : acceptsTpaHere;
    }
}
