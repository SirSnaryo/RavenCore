package com.kammoun.raven.team.model;

public enum TeamPermission {
    EDIT_HOME(1),
    MANAGE_MEMBERS(1 << 1),
    TOGGLE_PVP(1 << 2),
    VISIT_HOME(1 << 3),
    USE_TEAM_CHAT(1 << 4);

    private final int bit;

    TeamPermission(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static int all() {
        int value = 0;
        for (TeamPermission permission : values()) value |= permission.bit;
        return value;
    }
}
