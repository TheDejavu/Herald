package dev.mantic.herald;

import java.util.ArrayList;
import java.util.List;

/**
 * A webhook message: text, embeds, or both. Every setter returns {@code this}.
 *
 * <p>Mentions are off by default: text such as {@code @everyone} or {@code <@id>} is shown but pings nobody.
 * That is deliberate, because webhook text often contains player names or other input you don't control.
 * Turn pings on per message with {@link #mentionEveryone}, {@link #mentionUsers} and {@link #mentionRoles}.
 */
public final class Message {
    private String content, username, avatarUrl;
    private final List<Embed> embeds = new ArrayList<>();
    private boolean everyone, users, roles;

    public Message content(String content) { this.content = content; return this; }

    /** Overrides the webhook's name for this message. */
    public Message username(String username) { this.username = username; return this; }

    /** Overrides the webhook's avatar for this message. */
    public Message avatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }

    public Message embed(Embed embed) { embeds.add(embed); return this; }

    /** Lets {@code @everyone} and {@code @here} in the content ping. */
    public Message mentionEveryone(boolean allow) { this.everyone = allow; return this; }

    /** Lets user mentions ({@code <@id>}) in the content ping. */
    public Message mentionUsers(boolean allow) { this.users = allow; return this; }

    /** Lets role mentions ({@code <@&id>}) in the content ping. */
    public Message mentionRoles(boolean allow) { this.roles = allow; return this; }

    /** @throws IllegalArgumentException if the message is empty or exceeds a Discord limit */
    String toJson() {
        boolean hasContent = content != null && !content.isEmpty();
        if (!hasContent && embeds.isEmpty()) throw new IllegalArgumentException("a message needs content or at least one embed");
        Embed.limit("message content", content, 2000);
        Embed.limit("username", username, 80);
        if (embeds.size() > 10) throw new IllegalArgumentException("a message can have at most 10 embeds, got " + embeds.size());

        Json j = new Json().put("content", hasContent ? content : null).put("username", username).put("avatar_url", avatarUrl);
        if (!embeds.isEmpty()) {
            List<String> out = new ArrayList<>();
            int total = 0;
            for (Embed e : embeds) {
                out.add(e.toJson());
                total += e.textLength();
            }
            if (total > 6000) throw new IllegalArgumentException("embeds total " + total + " characters, Discord allows 6000 per message");
            j.raw("embeds", Json.array(out));
        }
        List<String> parse = new ArrayList<>();
        if (everyone) parse.add(Json.quote("everyone"));
        if (users) parse.add(Json.quote("users"));
        if (roles) parse.add(Json.quote("roles"));
        return j.raw("allowed_mentions", new Json().raw("parse", Json.array(parse)).end()).end();
    }
}
