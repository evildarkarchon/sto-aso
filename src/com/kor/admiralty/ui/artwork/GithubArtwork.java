/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.stream.MemoryCacheImageInputStream;

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
                    image = decode(response.body());
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

    /** Requires the complete zlib stream and checksum even when ImageIO stops after the last pixel. */
    private static void validateCompressedPixels(byte[] bytes, int width, int height) throws IOException {
        var inflater = new Inflater();
        try {
            inflater.setInput(bytes);
            byte[] buffer = new byte[8192];
            long decoded = 0;
            // Eight bytes per pixel covers 16-bit RGBA; extra rows cover Adam7 filter bytes.
            long ceiling = 8L * (width + 1) * (height + 7);
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                decoded += count;
                if (decoded > ceiling || (count == 0 && !inflater.finished())) {
                    throw new IOException("Incomplete or excessive compressed pixels");
                }
            }
            if (inflater.getRemaining() != 0) throw new IOException("Trailing compressed data");
        } catch (DataFormatException invalid) {
            throw new IOException("Invalid compressed pixels", invalid);
        } finally {
            inflater.end();
        }
    }

    /** Checks PNG framing and checksums because ImageIO can accept files missing their end chunk. */
    private static BufferedImage decode(byte[] bytes) throws IOException {
        if (bytes.length < 8 || ByteBuffer.wrap(bytes).getLong() != 0x89504e470d0a1a0aL) {
            throw new IOException("Not a PNG");
        }
        var compressed = new ByteArrayOutputStream();
        int offset = 8;
        boolean ended = false;
        while (offset <= bytes.length - 12) {
            int length = ByteBuffer.wrap(bytes).getInt(offset);
            if (length < 0 || length > bytes.length - offset - 12) throw new IOException("Truncated PNG chunk");
            var crc = new CRC32();
            crc.update(bytes, offset + 4, length + 4);
            if ((int) crc.getValue() != ByteBuffer.wrap(bytes).getInt(offset + 8 + length)) {
                throw new IOException("Corrupt PNG chunk");
            }
            int type = ByteBuffer.wrap(bytes).getInt(offset + 4);
            if (type == 0x49444154) compressed.write(bytes, offset + 8, length);
            offset += length + 12;
            if (type == 0x49454e44) {
                ended = length == 0 && offset == bytes.length;
                break;
            }
        }
        if (!ended) throw new IOException("Incomplete PNG");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unknown image data");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Check metadata before allocating a potentially hostile decoded pixel buffer.
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IOException("Invalid artwork dimensions");
                }
                validateCompressedPixels(compressed.toByteArray(), width, height);
                boolean[] warned = {false};
                reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
                BufferedImage image = reader.read(0);
                if (warned[0] || image == null || image.getWidth() != width || image.getHeight() != height) {
                    throw new IOException("Incomplete artwork decode");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }
}
