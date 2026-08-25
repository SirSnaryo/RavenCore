package com.kammoun.raven.endportal.config;

import com.kammoun.raven.library.config.MessageKey;

public enum EndPortalMessage implements MessageKey {
    ENTERING("entering", "&#A855F7The End &#7A7A7Apulls you in&#A855F7..."),
    RETURNING("returning",
            "&#A855F7The End &#7A7A7Areleases you. Sending you back to the overworld&#A855F7..."),
    NO_END_LOCATION("no-end-location",
            "&#D90429The End is not accepting travellers right now. Try again shortly."),
    NO_RETURN_LOCATION("no-return-location",
            "&#D90429No safe overworld location is available right now. Try again shortly."),
    TRANSFER_FAILED("transfer-failed",
            "&#D90429The destination server could not be reached. Please try again.");

    private final String path;
    private final String[] defaults;

    EndPortalMessage(String path, String... defaults) {
        this.path = path;
        this.defaults = defaults;
    }

    @Override public String getPath() { return path; }
    @Override public String[] getDefault() { return defaults; }
}
