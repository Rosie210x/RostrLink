package com.rostrlink.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Delivery channel used for OTP / password-reset notifications.
 *
 * <p>Values are serialised to/from JSON as lower-case strings
 * ({@code "email"}, {@code "sms"}) so existing API consumers are
 * unaffected by the move from a bare {@code String} field.
 */
public enum ResetChannel {

    EMAIL("email"),
    SMS("sms");

    private final String value;

    ResetChannel(String value) {
        this.value = value;
    }

    /** Serialise to lower-case string in JSON responses. */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Deserialise from JSON — accepts both lower-case ({@code "email"})
     * and upper-case ({@code "EMAIL"}) to be lenient with API callers.
     */
    @JsonCreator
    public static ResetChannel fromValue(String raw) {
        if (raw == null) return null;
        for (ResetChannel c : values()) {
            if (c.value.equalsIgnoreCase(raw) || c.name().equalsIgnoreCase(raw)) {
                return c;
            }
        }
        throw new IllegalArgumentException(
                "Unknown reset channel: '" + raw + "'. Accepted values: email, sms");
    }
}
