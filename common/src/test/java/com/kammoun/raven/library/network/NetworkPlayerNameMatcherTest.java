package com.kammoun.raven.library.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkPlayerNameMatcherTest {

    @Test
    void completesNamesCaseInsensitivelyWithStableOrdering() {
        List<String> result = NetworkPlayerNameMatcher.complete(
                List.of("Steve", "alex", "Stella", "Builder"),
                "st",
                Set.of()
        );

        assertEquals(List.of("Stella", "Steve"), result);
    }

    @Test
    void excludesProvidedNamesIgnoringCase() {
        List<String> result = NetworkPlayerNameMatcher.complete(
                List.of("Steve", "Alex", "Builder"),
                "",
                Set.of("steve")
        );

        assertEquals(List.of("Alex", "Builder"), result);
    }
}
