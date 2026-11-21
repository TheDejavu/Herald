package dev.mantic.herald;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sends messages to one Discord webhook.
 *
 * <p>Sends never block the caller: each webhook has one background daemon thread that delivers messages in the
 * order they were sent, waiting out Discord's rate limits. Safe to call from a Minecraft server's main thread.
 *
 * <pre>{@code
 * Webhook webhook = Webhook.of("https://discord.com/api/webhooks/123/abc");
 * webhook.send("Server started");
 * webhook.send(new Message().username("Shop").embed(new Embed().title("Purchase").color(0x57F287)));
 * webhook.close(); // in onDisable
 * }</pre>
 */
public final class Webhook implements AutoCloseable {
    private static final String USER_AGENT = "Herald (https://github.com/Mantic-Development/Herald, 1.0.0)";
    private static final int MAX_RETRIES = 3;
    private static final long MAX_WAIT_MILLIS = 60_000;

    private final URL url;
    private final ExecutorService executor;

    /** @throws IllegalArgumentException if the URL is not a valid https URL */
    public static Webhook of(String url) {
        return of(url, "Herald Discord Webhook");
    }

    /** @param threadName name of the background thread, useful in thread dumps and timings reports */
    public static Webhook of(String url, String threadName) {
        URL parsed;
        try {
            parsed = new URL(url);
        } catch (MalformedURLException | NullPointerException e) {
            throw new IllegalArgumentException("not a valid webhook URL", e); // the URL is a secret: keep it out of the message
        }
        if (!"https".equals(parsed.getProtocol())) throw new IllegalArgumentException("webhook URL must use https");
        return new Webhook(parsed, threadName);
    }

    Webhook(URL url, String threadName) {
        this.url = url;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread t = new Thread(task, threadName);
            t.setDaemon(true); // never keeps the JVM (or a stopping server) alive
            return t;
        });
    }

    /** Sends plain text. Mentions in it do not ping; use {@link Message} to allow them. */
    public CompletableFuture<Void> send(String content) {
        return send(new Message().content(content));
    }

    /**
     * Queues a message. The future completes when Discord accepts it, or fails with a {@link WebhookException}
     * (Discord rejected it) or another {@link IOException} (network). Ignoring the future is fine.
     *
     * @throws IllegalArgumentException immediately, if the message is empty or exceeds a Discord limit
     */
    public CompletableFuture<Void> send(Message message) {
        byte[] body = message.toJson().getBytes(StandardCharsets.UTF_8);
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    post(body);
                    result.complete(null);
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException closed) {
            result.completeExceptionally(new IllegalStateException("webhook is closed"));
        }
        return result;
    }

    /** Stops accepting messages. Messages already queued are still delivered. */
    @Override
    public void close() {
        executor.shutdown();
    }

    private void post(byte[] body) throws IOException {
        for (int attempt = 0; ; attempt++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            try {
                c.setRequestMethod("POST");
                c.setConnectTimeout(10_000);
                c.setReadTimeout(15_000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("User-Agent", USER_AGENT);
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
                int status = c.getResponseCode();
                if (status == 429 && attempt < MAX_RETRIES) {
                    pause(seconds(c.getHeaderField("Retry-After"), 1));
                    continue;
                }
                if (status / 100 != 2) throw new WebhookException(status, read(c.getErrorStream()));
                read(c.getInputStream());
                // Discord says how many sends are left in the current window; wait it out rather than hit a 429.
                if ("0".equals(c.getHeaderField("X-RateLimit-Remaining"))) pause(seconds(c.getHeaderField("X-RateLimit-Reset-After"), 0));
                return;
            } finally {
                c.disconnect();
            }
        }
    }

    private static double seconds(String header, double fallback) {
        try {
            return header == null ? fallback : Double.parseDouble(header.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void pause(double seconds) throws IOException {
        long millis = Math.min(MAX_WAIT_MILLIS, (long) Math.ceil(seconds * 1000));
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for Discord's rate limit", e);
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = stream.read(buffer)) != -1 && out.size() < 8192) out.write(buffer, 0, n); // error bodies are small; cap anyway
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
