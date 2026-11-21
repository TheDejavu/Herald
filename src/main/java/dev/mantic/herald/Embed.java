package dev.mantic.herald;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A Discord embed. Every setter returns {@code this}; unset parts are left out.
 * Discord's size limits are checked when the message is sent.
 */
public final class Embed {
    private String title, description, url, footerText, footerIcon, authorName, authorUrl, authorIcon, thumbnail, image;
    private Integer color;
    private Instant timestamp;
    private final List<String[]> fields = new ArrayList<>(); // name, value, inline

    public Embed title(String title) { this.title = title; return this; }

    public Embed description(String description) { this.description = description; return this; }

    /** Makes the title a link. */
    public Embed url(String url) { this.url = url; return this; }

    /** @param rgb colour of the side bar, e.g. {@code 0x5865F2} */
    public Embed color(int rgb) { this.color = rgb & 0xFFFFFF; return this; }

    public Embed timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

    public Embed footer(String text) { return footer(text, null); }

    public Embed footer(String text, String iconUrl) { this.footerText = text; this.footerIcon = iconUrl; return this; }

    public Embed author(String name) { return author(name, null, null); }

    public Embed author(String name, String url, String iconUrl) {
        this.authorName = name;
        this.authorUrl = url;
        this.authorIcon = iconUrl;
        return this;
    }

    public Embed thumbnail(String imageUrl) { this.thumbnail = imageUrl; return this; }

    public Embed image(String imageUrl) { this.image = imageUrl; return this; }

    public Embed field(String name, String value) { return field(name, value, false); }

    /** @param inline true to let Discord place this field beside the previous one */
    public Embed field(String name, String value, boolean inline) {
        fields.add(new String[]{name, value, Boolean.toString(inline)});
        return this;
    }

    /** Characters that count towards Discord's 6000-per-message embed limit. */
    int textLength() {
        int n = len(title) + len(description) + len(footerText) + len(authorName);
        for (String[] f : fields) n += len(f[0]) + len(f[1]);
        return n;
    }

    /** @throws IllegalArgumentException if a Discord limit is exceeded */
    String toJson() {
        limit("embed title", title, 256);
        limit("embed description", description, 4096);
        limit("embed footer", footerText, 2048);
        limit("embed author name", authorName, 256);
        if (fields.size() > 25) throw new IllegalArgumentException("an embed can have at most 25 fields, got " + fields.size());

        Json j = new Json().put("title", title).put("description", description).put("url", url);
        if (color != null) j.put("color", color);
        if (timestamp != null) j.put("timestamp", timestamp.toString());
        if (footerText != null) j.raw("footer", new Json().put("text", footerText).put("icon_url", footerIcon).end());
        if (authorName != null) j.raw("author", new Json().put("name", authorName).put("url", authorUrl).put("icon_url", authorIcon).end());
        if (thumbnail != null) j.raw("thumbnail", new Json().put("url", thumbnail).end());
        if (image != null) j.raw("image", new Json().put("url", image).end());
        if (!fields.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String[] f : fields) {
                if (f[0] == null || f[0].isEmpty() || f[1] == null || f[1].isEmpty())
                    throw new IllegalArgumentException("embed fields need a non-empty name and value");
                limit("embed field name", f[0], 256);
                limit("embed field value", f[1], 1024);
                out.add(new Json().put("name", f[0]).put("value", f[1]).put("inline", Boolean.parseBoolean(f[2])).end());
            }
            j.raw("fields", Json.array(out));
        }
        return j.end();
    }

    static void limit(String what, String value, int max) {
        if (len(value) > max) throw new IllegalArgumentException(what + " is " + value.length() + " characters, Discord allows " + max);
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }
}
