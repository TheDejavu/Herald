package dev.mantic.herald;

import java.io.IOException;

/** Discord answered a send with an error status. */
public final class WebhookException extends IOException {
    private final int status;
    private final String body;

    WebhookException(int status, String body) {
        super("Discord returned HTTP " + status + (body.isEmpty() ? "" : ": " + body));
        this.status = status;
        this.body = body;
    }

    /** HTTP status, e.g. 404 when the webhook was deleted or 400 when the payload was rejected. */
    public int status() {
        return status;
    }

    /** Response body, usually a JSON error description; may be empty. */
    public String body() {
        return body;
    }
}
