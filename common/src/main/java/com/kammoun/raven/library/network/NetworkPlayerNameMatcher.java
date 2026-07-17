package com.kammoun.raven.library.network;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class NetworkPlayerNameMatcher {

    private NetworkPlayerNameMatcher() {}

    public static List<String> complete(Collection<String> names, String prefix, Set<String> excludedNames) {
        String normalizedPrefix = normalize(prefix);
        Set<String> normalizedExcluded = excludedNames.stream()
                .map(NetworkPlayerNameMatcher::normalize)
                .collect(Collectors.toSet());

        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> !normalizedExcluded.contains(normalize(name)))
                .filter(name -> normalize(name).startsWith(normalizedPrefix))
                .sorted(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                        .thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
