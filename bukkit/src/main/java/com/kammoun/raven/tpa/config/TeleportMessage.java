package com.kammoun.raven.tpa.config;

public enum TeleportMessage {
    USER_NOT_FOUND("user-not-found", "&#D90429Player &#FFD447%player% &#D90429is not online."),
    SELF_TELEPORT("self-teleport", "&#D90429You cannot teleport to yourself."),
    NON_VALID_REQUEST("non-valid-request", "&#D90429Teleport request does not exist."),
    NO_REQUESTS_FOUND("no-requests-found", "&#D90429You do not have any teleport requests."),
    ACCEPTED_TELEPORT("accepted-teleport", "&#33FF66You accepted this teleport."),
    TARGET_ACCEPTED_TELEPORT("target-accepted-teleport", "&#FFD447%player% &#33FF66accepted your request."),
    RECEIVE_REQUEST("receive-request", "&#4CC9F0%player% &#FFFFFFsent you a tpa request.\n<click:run_command:/tpaccept %player%>&#33FF66[ACCEPT]</click> &#7A7A7A| <click:run_command:/tpadeny %player%>&#D90429[DENY]</click>"),
    RECEIVE_HERE_REQUEST("receive-here-request", "&#4CC9F0%player% &#FFFFFFsent you a tpahere request.\n<click:run_command:/tpaccept %player%>&#33FF66[ACCEPT]</click> &#7A7A7A| <click:run_command:/tpadeny %player%>&#D90429[DENY]</click>"),
    SENT_REQUEST("sent-request", "&#33FF66You sent &#FFD447%player% &#33FF66a teleport request."),
    SENT_HERE_REQUEST("sent-here-request", "&#33FF66You sent &#FFD447%player% &#33FF66a tpahere request."),
    TPA_REQUEST_SENDER_ACTIONBAR("tpa-request-sender-actionbar", "&#7A7A7AYou sent &#4CC9F0%player%&#7A7A7A a teleport request."),
    TPA_HERE_REQUEST_SENDER_ACTIONBAR("tpa-here-request-sender-actionbar", "&#7A7A7AYou sent &#4CC9F0%player%&#7A7A7A a tpahere request."),
    TPA_REQUEST_TARGET_ACTIONBAR("tpa-request-target-actionbar", "&#4CC9F0%player% &#7A7A7Asent you a tpa request."),
    TPA_HERE_REQUEST_TARGET_ACTIONBAR("tpa-here-request-target-actionbar", "&#4CC9F0%player% &#7A7A7Asent you a tpahere request."),
    CANCELLED_REQUEST("cancelled-request", "&#D90429You declined &#FFD447%player%&#D90429's request."),
    CANCELLED_REQUEST_SENDER("cancelled-request-sender", "&#FFD447%player% &#D90429declined your teleport request."),
    BLOCK_TPA_REQUEST("block-tpa-request", "&#D90429That player is not accepting tpa requests."),
    BLOCK_TPHERE_REQUEST("block-tphere-request", "&#D90429That player is not accepting tpahere requests."),
    REQUEST_COOLDOWN("request-cooldown", "&#D90429You already have a pending request to &#FFD447%player%&#D90429."),
    CANCEL_REQUESTS("cancel-requests", "&#33FF66Cancelled your outgoing teleport requests."),
    CANCEL_REQUESTS_FAILED("cancel-requests-failed", "&#D90429You do not have outgoing teleport requests."),
    TELEPORT_COUNTDOWN("teleport-countdown", "&#7A7A7ATeleporting in &#4CC9F0%time%&#7A7A7A seconds."),
    TELEPORT_COUNTDOWN_ACTIONBAR("teleport-countdown-actionbar", "&#7A7A7ATeleporting in &#4CC9F0%time%&#7A7A7A seconds."),
    TELEPORT_CANCELLED("teleport-cancelled", "&#D90429Teleport has been cancelled."),
    TELEPORT_MOVEMENT_CANCELLATION_ACTION_BAR("teleport-movement-cancellation-action-bar", "&#D90429Teleport cancelled because you moved."),
    TPA_TOGGLE_ON("tpa-toggle-on", "&#33FF66You are now accepting tpa requests."),
    TPA_TOGGLE_OFF("tpa-toggle-off", "&#D90429You are no longer accepting tpa requests."),
    TPA_HERE_TOGGLE_ON("tpa-here-toggle-on", "&#33FF66You are now accepting tpahere requests."),
    TPA_HERE_TOGGLE_OFF("tpa-here-toggle-off", "&#D90429You are no longer accepting tpahere requests."),
    TPAUTO_TOGGLE_ON("tpa-auto-toggle-on", "&#33FF66You are now auto accepting teleport requests."),
    TPAUTO_TOGGLE_OFF("tpa-auto-toggle-off", "&#D90429You are no longer auto accepting teleport requests."),
    TPA_TOGGLE_ON_ACTIONBAR("tpa-toggle-on-actionbar", "&#33FF66TPA requests enabled."),
    TPA_TOGGLE_OFF_ACTIONBAR("tpa-toggle-off-actionbar", "&#D90429TPA requests disabled."),
    TPA_HERE_TOGGLE_ON_ACTIONBAR("tpa-here-toggle-on-actionbar", "&#33FF66TPAHere requests enabled."),
    TPA_HERE_TOGGLE_OFF_ACTIONBAR("tpa-here-toggle-off-actionbar", "&#D90429TPAHere requests disabled."),
    TPA_AUTO_TOGGLE_ON_ACTIONBAR("tpa-auto-toggle-on-actionbar", "&#33FF66TPAuto enabled."),
    TPA_AUTO_TOGGLE_OFF_ACTIONBAR("tpa-auto-toggle-off-actionbar", "&#D90429TPAuto disabled."),
    TPA_AUTO_STATUS_ACTIONBAR("tpa-auto-status-actionbar", "&#33FF66TPAuto is currently enabled.");

    private final String path;
    private final String fallback;

    TeleportMessage(String path, String fallback) {
        this.path = path;
        this.fallback = fallback;
    }

    public String path() {
        return path;
    }

    public String fallback() {
        return fallback;
    }
}
