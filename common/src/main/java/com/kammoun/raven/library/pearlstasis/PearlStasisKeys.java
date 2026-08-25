package com.kammoun.raven.library.pearlstasis;

public final class PearlStasisKeys {

    public static final String MARKER_PREFIX = "raven:pearl:has_pending:";
    public static final int DEFAULT_TTL_SECONDS = 300;

    private PearlStasisKeys() {}

    public static String markerKey(Object uuid) {
        return MARKER_PREFIX + uuid;
    }
}
