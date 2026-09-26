/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;

/** Internal fixed-source acquisition; only validated pixels cross into composition. */
final class GithubArtwork {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIMENSION = 2048;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private final HttpClient http;

    /** Retains the internal HTTP boundary, never exposed to application callers. */
    GithubArtwork(HttpClient http) {
        this.http = http;
    }

    /** Starts optional acquisition without waiting; failures complete with no source pixels. */
    void acquire(String name, Consumer<BufferedImage> completed) {
        var request = HttpRequest.newBuilder(URI.create(
                "https://github.com/intrinsical/sto-aso/raw/master/icons/" + name))
                .timeout(REQUEST_TIMEOUT).GET().build();
        fetch(request, new HashSet<>()).whenComplete((response, failure) -> {
            BufferedImage image = null;
            if (failure == null) {
                try {
                    long length = response.headers().firstValueAsLong("Content-Length").orElse(response.body().length);
                    if (length != response.body().length) throw new IOException("Incomplete response");
                    image = ArtworkPng.decode(response.body(), MAX_DIMENSION);
                } catch (IOException | RuntimeException rejected) {
                    // Optional malformed artwork uses the same retry outcome as transport failure.
                }
            }
            completed.accept(image);
        });
    }

    /** Bounds both buffering and total body arrival time, cancelling a timed-out exchange. */
    private CompletableFuture<HttpResponse<byte[]>> fetch(HttpRequest request, Set<URI> visited) {
        URI uri = request.uri();
        if (!approved(uri) || visited.size() >= 6 || !visited.add(uri)) {
            return CompletableFuture.failedFuture(new IOException("Untrusted or excessive redirect"));
        }
        var exchange = http.sendAsync(request, info -> {
            if (redirect(info.statusCode())) {
                // Redirect bodies are never decoded, but still cannot consume unbounded bandwidth.
                return HttpResponse.BodySubscribers.limiting(HttpResponse.BodySubscribers.replacing(new byte[0]), MAX_BYTES);
            }
            if (info.statusCode() != 200
                    || !info.headers().firstValue("Content-Type").orElse("")
                            .split(";", 2)[0].trim().equalsIgnoreCase("image/png")
                    || !info.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity")
                    || info.headers().firstValueAsLong("Content-Length").orElse(0) < 0
                    || info.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_BYTES) {
                throw new IllegalArgumentException("Rejected artwork response");
            }
            return HttpResponse.BodySubscribers.limiting(HttpResponse.BodySubscribers.ofByteArray(), MAX_BYTES);
        });
        // Use a separate dependent future: orTimeout on exchange itself would prevent cancellation.
        return exchange.thenApply(response -> response).orTimeout(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .whenComplete((response, failure) -> {
                    if (failure != null) exchange.cancel(true);
                }).thenCompose(response -> {
                    if (!response.uri().equals(uri)) {
                        return CompletableFuture.failedFuture(new IOException("Unexpected response origin"));
                    }
                    if (!redirect(response.statusCode())) return CompletableFuture.completedFuture(response);
                    URI target = uri.resolve(response.headers().firstValue("Location").orElseThrow());
                    return fetch(HttpRequest.newBuilder(target).timeout(REQUEST_TIMEOUT).GET().build(), visited);
                });
    }

    /** Allows only the two HTTPS origins used by the fixed GitHub raw-file source. */
    private static boolean approved(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && uri.getFragment() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                && ("github.com".equalsIgnoreCase(uri.getHost())
                    || "raw.githubusercontent.com".equalsIgnoreCase(uri.getHost()));
    }

    /** Recognizes redirects with defined GET semantics, excluding other non-success statuses. */
    private static boolean redirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

}
