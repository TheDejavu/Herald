# Herald

A tiny Discord webhook client for Java. No dependencies, Java 8+, one 11 KB jar.

Made for Minecraft plugins: sending never blocks the server thread, and there is nothing to drag into your shaded jar (no OkHttp, Kotlin, JSON library or SLF4J).

## Install

Herald is published through [JitPack](https://jitpack.io).

Maven:

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.TheDejavu</groupId>
    <artifactId>Herald</artifactId>
    <version>1.0.0</version>
</dependency>
```

Gradle:

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { implementation("com.github.TheDejavu:Herald:1.0.0") }
```

Shade it into your plugin and relocate `dev.mantic.herald` to your own package, so two plugins using different Herald versions can't clash.

## Use

```java
Webhook webhook = Webhook.of("https://discord.com/api/webhooks/123/abc");

webhook.send("Server started");

webhook.send(new Message()
        .username("Shop")
        .avatarUrl("https://example.com/shop.png")
        .embed(new Embed()
                .author("Steve")
                .title("Purchase")
                .description("Bought a **Diamond Sword**")
                .field("Price", "5 gems", true)
                .field("Zone", "spawn", true)
                .thumbnail("https://mc-heads.net/avatar/Steve")
                .color(0x57F287)
                .timestamp(Instant.now())
                .footer("AfkZone")));

webhook.close(); // in onDisable; queued messages are still delivered
```

Create one `Webhook` per URL and keep it; don't create one per message.

### Results and errors

`send` returns a `CompletableFuture<Void>`. Ignoring it is fine. To react to failures:

```java
webhook.send("hello").exceptionally(error -> {
    if (error.getCause() instanceof WebhookException) {
        WebhookException e = (WebhookException) error.getCause();
        getLogger().warning("Discord rejected the message: HTTP " + e.status()); // 404 = webhook deleted
    }
    return null;
});
```

A message that is empty or breaks one of Discord's size limits throws `IllegalArgumentException` straight from `send`, before anything is queued.

### Mentions are off by default

`webhook.send("@everyone")` shows the text but pings nobody. Webhook text usually contains player names or other input you don't control, and a player named `@everyone` shouldn't be able to ping your server. Allow pings per message:

```java
webhook.send(new Message().content("@everyone the event is starting").mentionEveryone(true));
```

`mentionUsers(true)` and `mentionRoles(true)` do the same for `<@id>` and `<@&id>`.

## How it works

- **One background thread per webhook.** Messages go out in the order they were sent. The thread is a daemon, so it never keeps a stopping server alive. Name it with `Webhook.of(url, "My Plugin Discord")` to make it recognisable in thread dumps.
- **Rate limits.** Discord says how many sends are left in the current window; when that reaches zero Herald waits before the next message. If Discord still answers 429, Herald waits the time Discord asks for and retries, up to three times.
- **Plain JDK.** HTTP is `HttpURLConnection`, JSON is a 60-line writer. Timeouts are 10 s to connect and 15 s to read.
- **The URL is a secret.** Herald never puts it in an exception message or log line.

## Coming from `club.minnced:discord-webhooks`

| discord-webhooks | Herald |
|---|---|
| `new WebhookClientBuilder(url).build()` | `Webhook.of(url)` |
| `builder.setThreadFactory(...)` with a named daemon thread | `Webhook.of(url, "thread name")` |
| `new WebhookMessageBuilder().setUsername(..).setAvatarUrl(..).addEmbeds(..).build()` | `new Message().username(..).avatarUrl(..).embed(..)` |
| `new WebhookEmbedBuilder().setAuthor(new EmbedAuthor(name, null, null))` | `new Embed().author(name)` |
| `.setDescription(..)`, `.setThumbnailUrl(..)`, `.setTimestamp(..)` | `.description(..)`, `.thumbnail(..)`, `.timestamp(..)` |
| `.setFooter(new EmbedFooter(text, null))` | `.footer(text)` |
| `.setColor(Color.decode(hex).getRGB())` | `.color(Integer.decode(hex))` |
| `client.send("@everyone")` pings | `send(new Message().content("@everyone").mentionEveryone(true))` |

## Not included

Editing or deleting messages, file uploads, forum threads, and receiving events. Herald only sends.

## Development

```
./gradlew build
```

Needs JDK 17 or newer to build; the library itself is compiled for Java 8. Tests run the client against a local stand-in for Discord's endpoint.

## License

[MIT](LICENSE)
