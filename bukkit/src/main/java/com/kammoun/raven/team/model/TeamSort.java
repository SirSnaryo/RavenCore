package com.kammoun.raven.team.model;

import java.util.List;

public enum TeamSort {
    JOIN_DATE(List.of("&#00f986• Join Date", "&f• Permissions", "&f• Money", "&f• Alphabetically", "&f• Online Members")),
    PERMISSIONS(List.of("&f• Join Date", "&#00f986• Permissions", "&f• Money", "&f• Alphabetically", "&f• Online Members")),
    MONEY(List.of("&f• Join Date", "&f• Permissions", "&#00f986• Money", "&f• Alphabetically", "&f• Online Members")),
    ALPHABETICALLY(List.of("&f• Join Date", "&f• Permissions", "&f• Money", "&#00f986• Alphabetically", "&f• Online Members")),
    ONLINE_MEMBERS(List.of("&f• Join Date", "&f• Permissions", "&f• Money", "&f• Alphabetically", "&#00f986• Online Members"));

    private final List<String> lore;

    TeamSort(List<String> lore) {
        this.lore = lore;
    }

    public List<String> lore() {
        return lore;
    }

    public TeamSort next() {
        TeamSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
