package dev.mantic.herald;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the client against a local stand-in for Discord's webhook endpoint. */
class WebhookTest {
    private HttpServer server;
    private Webhook webhook;
    private final List<String> received = Collections.synchronizedList(new ArrayList<>());
    private final List<String> userAgents = Collections.synchronizedList(new ArrayList<>());
    /** Statuses to answer with, in order; 204 once exhausted. */
    private final List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/webhooks/1/token", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            int n = requests.getAndIncrement();
            int status = n < statuses.size() ? statuses.get(n) : 204;
            if (status == 429) exchange.getResponseHeaders().add("Retry-After", "0.05");
            byte[] body = status >= 400 && status != 429 ? "{\"message\":\"Unknown Webhook\"}".getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        webhook = new Webhook(new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhooks/1/token"), "test");
    }

    @AfterEach
    void stop() {
        webhook.close();
        server.stop(0);
    }

    @Test
    void sendsTextWithMentionsSuppressed() throws Exception {
        webhook.send("hello @everyone").get(5, TimeUnit.SECONDS);
        assertEquals("{\"content\":\"hello @everyone\",\"allowed_mentions\":{\"parse\":[]}}", received.get(0));
        assertTrue(userAgents.get(0).startsWith("Herald"));
    }

    @Test
    void serializesFullMessage() throws Exception {
        Message m = new Message().username("Shop").avatarUrl("https://a/b.png").mentionEveryone(true)
                .content("line1\nsaid \"hi\" \\ tab\t")
                .embed(new Embed().title("Purchase").description("Bought a sword").url("https://x").color(0xFF57F287)
                        .timestamp(Instant.parse("2026-01-02T03:04:05Z")).footer("foot", "https://f").author("Steve")
                        .thumbnail("https://t").image("https://i").field("Price", "5 gems", true).field("Zone", "spawn"));
        webhook.send(m).get(5, TimeUnit.SECONDS);
        assertEquals("{\"content\":\"line1\\nsaid \\\"hi\\\" \\\\ tab\\t\",\"username\":\"Shop\",\"avatar_url\":\"https://a/b.png\","
                + "\"embeds\":[{\"title\":\"Purchase\",\"description\":\"Bought a sword\",\"url\":\"https://x\",\"color\":5763719,"
                + "\"timestamp\":\"2026-01-02T03:04:05Z\",\"footer\":{\"text\":\"foot\",\"icon_url\":\"https://f\"},"
                + "\"author\":{\"name\":\"Steve\"},\"thumbnail\":{\"url\":\"https://t\"},\"image\":{\"url\":\"https://i\"},"
                + "\"fields\":[{\"name\":\"Price\",\"value\":\"5 gems\",\"inline\":true},{\"name\":\"Zone\",\"value\":\"spawn\",\"inline\":false}]}],"
                + "\"allowed_mentions\":{\"parse\":[\"everyone\"]}}", received.get(0));
    }

    @Test
    void keepsSendOrder() throws Exception {
        for (int i = 0; i < 5; i++) webhook.send("m" + i);
        webhook.send("last").get(5, TimeUnit.SECONDS);
        assertEquals(6, received.size());
        for (int i = 0; i < 5; i++) assertTrue(received.get(i).contains("\"m" + i + "\""));
    }

    @Test
    void retriesAfterRateLimit() throws Exception {
        statuses.add(429);
        statuses.add(429);
        webhook.send("patient").get(5, TimeUnit.SECONDS);
        assertEquals(3, requests.get());
    }

    @Test
    void givesUpAfterRepeatedRateLimits() {
        for (int i = 0; i < 10; i++) statuses.add(429);
        ExecutionException e = assertThrows(ExecutionException.class, () -> webhook.send("x").get(5, TimeUnit.SECONDS));
        assertEquals(429, ((WebhookException) e.getCause()).status());
        assertEquals(4, requests.get()); // first try + 3 retries
    }

    @Test
    void reportsDiscordErrors() {
        statuses.add(404);
        ExecutionException e = assertThrows(ExecutionException.class, () -> webhook.send("x").get(5, TimeUnit.SECONDS));
        WebhookException cause = (WebhookException) e.getCause();
        assertEquals(404, cause.status());
        assertTrue(cause.body().contains("Unknown Webhook"));
    }

    @Test
    void rejectsInvalidMessagesBeforeSending() {
        assertThrows(IllegalArgumentException.class, () -> webhook.send(new Message()));
        assertThrows(IllegalArgumentException.class, () -> webhook.send("x".repeat(2001)));
        assertThrows(IllegalArgumentException.class, () -> webhook.send(new Message().embed(new Embed().title("t".repeat(257)))));
        assertThrows(IllegalArgumentException.class, () -> webhook.send(new Message().embed(new Embed().field("", "v"))));
        Message tooManyEmbeds = new Message();
        for (int i = 0; i < 11; i++) tooManyEmbeds.embed(new Embed().title("e"));
        assertThrows(IllegalArgumentException.class, () -> webhook.send(tooManyEmbeds));
        Message tooLong = new Message().embed(new Embed().description("d".repeat(4096))).embed(new Embed().description("d".repeat(4096)));
        assertThrows(IllegalArgumentException.class, () -> webhook.send(tooLong));
        assertEquals(0, requests.get());
    }

    @Test
    void failsCleanlyAfterClose() {
        webhook.close();
        ExecutionException e = assertThrows(ExecutionException.class, () -> webhook.send("late").get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void validatesUrl() {
        assertThrows(IllegalArgumentException.class, () -> Webhook.of("http://discord.com/api/webhooks/1/token"));
        assertThrows(IllegalArgumentException.class, () -> Webhook.of("not a url"));
        assertThrows(IllegalArgumentException.class, () -> Webhook.of(null));
        Webhook.of("https://discord.com/api/webhooks/1/token").close();
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("\"a\\u0001b\\u2028c\"", Json.quote("a\u0001b c"));
    }
}
