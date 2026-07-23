package com.kammoun.raven.library.locationpool;

import java.util.List;
import java.util.Map;

public final class ClusterServerIds {

    private ClusterServerIds() {}

    public static List<String> fromRegistry(Map<String, String> registry) {
        return registry.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
