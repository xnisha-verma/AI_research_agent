//package com.research.AIagent.scraper;
//
//import com.research.AIagent.config.ProxyConfig;
//import okhttp3.Credentials;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.Response;
//import okio.Timeout;
//
//import java.io.IOException;
//import java.util.concurrent.TimeUnit;
//
//public class AbstractScarper {
//    private final ProxyConfig proxyConfig;
//    public AbstractScarper(final ProxyConfig proxyConfig){
//        this.proxyConfig = proxyConfig;
//    }
//
//    public OkHttpClient okHttpClient() {
//        return new OkHttpClient.Builder()
//                .proxy(this.proxyConfig.toProxy())
//                .connectTimeout(30, TimeUnit.SECONDS)
//                .readTimeout(30, TimeUnit.SECONDS)
//                .build();
//    }
//
//    public String fetch(final String url) throws IOException {
//
//        final Request request = new Request.Builder()
//                .url(url)
//                .header("User-Agent", this.proxyConfig.getUserAgent())
//                .build();
//
//        try (Response response = okHttpClient()
//                .newCall(request)
//                .execute()) {
//
//            if (!response.isSuccessful()) {
//                throw new IOException(
//                        "HTTP code " + response.code() + " for " + url
//                );
//            }
//
//            if (response.body() == null) {
//                throw new IOException("Empty response body for " + url);
//            }
//
//            return response.body().string();
//        }
//    }
//
//    public String detectProxyIp() {
//
//        try {
//            final String body = fetch("https://api.ipify.org/");
//
//            return body.trim();
//
//        } catch (final IOException e) {
//
//            System.out.println("Failed to detect proxy IP: " + e.getMessage());
//
//            return "unknown";
//        }
//    }
//}

package com.research.AIagent.scraper;

import com.research.AIagent.config.ProxyConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AbstractScarper {

    private final ProxyConfig proxyConfig;
    private final OkHttpClient client;

    private static final int MAX_RETRIES = 3;

    public AbstractScarper(final ProxyConfig proxyConfig) {

        this.proxyConfig = proxyConfig;

        this.client = new OkHttpClient.Builder()
                .proxy(this.proxyConfig.toProxy())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    /**
     * Fetch URL with default Accept header (application/json).
     */
    public String fetch(final String url) throws IOException {
        return fetch(url, "application/json");
    }

    /**
     * Fetch URL with a custom Accept header.
     * Uses exponential backoff; HTTP 429 responses get
     * longer waits so we don't hammer the server.
     */
    public String fetch(
            final String url,
            final String acceptHeader
    ) throws IOException {

        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            try {

                final Request request = new Request.Builder()
                        .url(url)
                        .header(
                                "User-Agent",
                                this.proxyConfig.getUserAgent()
                        )
                        .header(
                                "Accept",
                                acceptHeader
                        )
                        .build();

                log.debug(
                        "Fetching {} (attempt {}/{})",
                        url, attempt, MAX_RETRIES
                );

                try (Response response =
                             this.client.newCall(request).execute()) {

                    final int code = response.code();

                    if (code == 429) {
                        /*
                         * Rate-limited — wait longer before
                         * retrying. Most Reddit rate-limits
                         * clear in 5–10 seconds.
                         */
                        throw new RateLimitException(
                                "HTTP 429 (rate-limited) for " + url
                        );
                    }

                    if (!response.isSuccessful()) {
                        throw new IOException(
                                "HTTP " + code + " for " + url
                        );
                    }

                    if (response.body() == null) {
                        throw new IOException(
                                "Empty response body for " + url
                        );
                    }

                    return response.body().string();
                }

            } catch (RateLimitException e) {

                lastException = e;

                log.warn(
                        "Rate-limited on attempt {}/{}: {}",
                        attempt, MAX_RETRIES, e.getMessage()
                );

                if (attempt < MAX_RETRIES) {
                    sleepQuietly(15_000L * attempt);
                }

            } catch (IOException e) {

                lastException = e;

                log.warn(
                        "Request failed attempt {}/{}: {}",
                        attempt, MAX_RETRIES, e.getMessage()
                );

                if (attempt < MAX_RETRIES) {
                    sleepQuietly(2000L * attempt);
                }
            }
        }

        throw lastException;
    }

    /**
     * Detect the outgoing IP visible to external sites.
     * Skipped when no proxy is configured.
     */
    public String detectProxyIp() {

        if (!this.proxyConfig.isConfigured()) {
            return "direct (no proxy)";
        }

        try {

            final String body =
                    fetch("https://httpbin.org/ip");

            return body.trim();

        } catch (final IOException e) {

            log.warn(
                    "Failed to detect proxy IP: {}",
                    e.getMessage()
            );

            return "unknown";
        }
    }

    private void sleepQuietly(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal marker so we can apply longer back-off
     * specifically for 429 responses.
     */
    private static class RateLimitException extends IOException {
        RateLimitException(final String message) {
            super(message);
        }
    }
}