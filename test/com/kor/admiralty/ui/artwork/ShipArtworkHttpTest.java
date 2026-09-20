/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises production HTTP validation through the Ship Artwork presentation boundary. */
class ShipArtworkHttpTest {
    @TempDir Path directory;

    /** A valid fixed-source PNG must advance the existing handle on the event thread. */
    @Test
    void successfulPngReplacesFallback() throws Exception {
        var http = new ScriptedArtworkHttpClient();
        http.enqueue(200, Map.of("Content-Type", List.of("image/png")), png(64, 64));
        Ship ship = ship();
        try (var artwork = new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(),
                List.of(), ShipArtworkHttpTest::resource, http)) {
            ImageIcon handle = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            SwingUtilities.invokeAndWait(() -> assertEquals(0xFFFF00FF, pixel(handle)));
            assertEquals("https://github.com/intrinsical/sto-aso/raw/master/icons/http_fixture.png",
                    http.requests.getFirst().uri().toString());
            assertEquals(java.time.Duration.ofSeconds(15), http.requests.getFirst().timeout().orElseThrow());
        }
    }

    /** Approved HTTPS redirects preserve image replacement and retain bounded request policy. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {301, 302, 303, 307, 308})
    void approvedRedirectsReachSpecificArtwork(int status) throws Exception {
        var http = new ScriptedArtworkHttpClient();
        http.enqueue(status, Map.of("Location", List.of(
                "https://raw.githubusercontent.com/intrinsical/sto-aso/master/icons/http_fixture.png")), new byte[0]);
        http.enqueue(302, Map.of("Location", List.of("other.png")), new byte[0]);
        http.enqueue(200, Map.of("Content-Type", List.of("image/png; charset=binary")), png(2048, 2048));
        Ship ship = ship();
        try (var artwork = new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(),
                List.of(), ShipArtworkHttpTest::resource, http)) {
            ImageIcon handle = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            SwingUtilities.invokeAndWait(() -> assertEquals(0xFFFF00FF, pixel(handle)));
            assertEquals(3, http.requests.size());
            assertEquals("https://raw.githubusercontent.com/intrinsical/sto-aso/master/icons/other.png",
                    http.requests.getLast().uri().toString());
        }
    }

    /** Rejected wire data must retain fallback, enter backoff, and recover on explicit refresh. */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"status", "type", "missing-type", "oversize",
            "declared-oversize", "length-mismatch", "invalid-length", "garbage", "truncated", "truncated-zlib", "bad-zlib-checksum", "crc",
            "wide", "tall", "zero-width", "negative-height", "encoding", "connect-timeout", "request-timeout",
            "cancelled", "io", "redirect-http", "redirect-host", "redirect-port", "redirect-user",
            "redirect-malformed", "redirect-missing", "redirect-loop"})
    void rejectedResponsesUseFallbackAndRetry(String category) throws Exception {
        var http = new ScriptedArtworkHttpClient();
        byte[] bytes = png(64, 64);
        var headers = new java.util.HashMap<String, List<String>>();
        headers.put("Content-Type", List.of("image/png"));
        int status = 200;
        switch (category) {
            case "status" -> status = 404;
            case "type" -> headers.put("Content-Type", List.of("text/html"));
            case "missing-type" -> headers.clear();
            case "oversize" -> bytes = paddedPng(bytes, 2 * 1024 * 1024 + 1);
            case "declared-oversize" -> headers.put("Content-Length", List.of("2097153"));
            case "length-mismatch" -> headers.put("Content-Length", List.of("1"));
            case "invalid-length" -> headers.put("Content-Length", List.of("bad"));
            case "garbage" -> bytes = new byte[]{1, 2, 3};
            case "truncated" -> bytes = java.util.Arrays.copyOf(bytes, bytes.length - 12);
            case "truncated-zlib" -> bytes = brokenZlib(bytes, true);
            case "bad-zlib-checksum" -> bytes = brokenZlib(bytes, false);
            case "crc" -> bytes[29] ^= 1;
            case "wide" -> bytes = png(2049, 1);
            case "tall" -> bytes = png(1, 2049);
            case "zero-width" -> bytes = dimensions(bytes, 0, 64);
            case "negative-height" -> bytes = dimensions(bytes, 64, -1);
            case "encoding" -> headers.put("Content-Encoding", List.of("gzip"));
            case "connect-timeout" -> http.fail(new java.net.http.HttpConnectTimeoutException("scripted"));
            case "request-timeout" -> http.fail(new java.net.http.HttpTimeoutException("scripted"));
            case "cancelled" -> http.fail(new java.util.concurrent.CancellationException());
            case "io" -> http.fail(new java.io.IOException("scripted"));
            default -> {
                status = 302;
                String location = switch (category) {
                    case "redirect-http" -> "http://raw.githubusercontent.com/file.png";
                    case "redirect-host" -> "https://github.com.attacker.example/file.png";
                    case "redirect-port" -> "https://github.com:444/file.png";
                    case "redirect-user" -> "https://user@github.com/file.png";
                    case "redirect-malformed" -> "https://[broken";
                    case "redirect-loop" -> "https://github.com/intrinsical/sto-aso/raw/master/icons/http_fixture.png";
                    default -> "";
                };
                if (!location.isEmpty()) headers.put("Location", List.of(location));
            }
        }
        if (!List.of("connect-timeout", "request-timeout", "cancelled", "io").contains(category)) {
            http.enqueue(status, headers, bytes);
        }
        Ship ship = ship();
        try (var artwork = new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(),
                List.of(), ShipArtworkHttpTest::resource, http)) {
            int fallback = pixel(artwork.forShip(ship, ShipArtwork.Presentation.GENERIC));
            ImageIcon handle = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            SwingUtilities.invokeAndWait(() -> assertEquals(fallback, pixel(handle)));
            if (category.equals("oversize")) {
                assertEquals(1, http.cancelledBodies, "Oversized streaming responses must be cancelled");
                assertTrue(http.bytesDelivered <= 2097158, "Stop at the first chunk crossing two MiB");
            }
            if (category.startsWith("redirect-")) {
                assertEquals(1, http.requests.size(), "Reject untrusted targets before issuing a request");
            }
            int attempts = http.requests.size();
            artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            assertEquals(attempts, http.requests.size(), "Failure must activate backoff");
            http.enqueue(200, Map.of("Content-Type", List.of("image/png")), png(64, 64));
            artwork.refreshOnline(List.of(ship));
            SwingUtilities.invokeAndWait(() -> assertEquals(0xFFFF00FF, pixel(handle)));
        }
    }

    /** The byte ceiling is inclusive, independently of the dimension ceiling. */
    @Test
    void exactlyTwoMebibytesIsAccepted() throws Exception {
        var http = new ScriptedArtworkHttpClient();
        http.enqueue(200, Map.of("Content-Type", List.of("image/png"),
                "Content-Length", List.of("2097152")), paddedPng(png(64, 64), 2097152));
        Ship ship = ship();
        try (var artwork = new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(),
                List.of(), ShipArtworkHttpTest::resource, http)) {
            ImageIcon handle = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            SwingUtilities.invokeAndWait(() -> assertEquals(0xFFFF00FF, pixel(handle)));
        }
    }

    /** Keeps PNG framing valid while corrupting only the compressed stream's ending. */
    private static byte[] brokenZlib(byte[] png, boolean truncate) {
        int offset = 8;
        while (java.nio.ByteBuffer.wrap(png).getInt(offset + 4) != 0x49444154) {
            offset += java.nio.ByteBuffer.wrap(png).getInt(offset) + 12;
        }
        int length = java.nio.ByteBuffer.wrap(png).getInt(offset);
        byte[] result = png.clone();
        if (truncate) {
            result = new byte[png.length - 1];
            System.arraycopy(png, 0, result, 0, offset + 8 + length - 1);
            System.arraycopy(png, offset + 8 + length, result, offset + 8 + length - 1,
                    png.length - offset - 8 - length);
            length--;
            java.nio.ByteBuffer.wrap(result).putInt(offset, length);
        } else {
            result[offset + 8 + length - 1] ^= 1;
        }
        var crc = new java.util.zip.CRC32();
        crc.update(result, offset + 4, length + 4);
        java.nio.ByteBuffer.wrap(result).putInt(offset + length + 8, (int) crc.getValue());
        return result;
    }

    /** Inserts a valid private ancillary chunk so byte-limit fixtures remain decodable PNGs. */
    private static byte[] paddedPng(byte[] png, int size) {
        byte[] result = new byte[size];
        int offset = png.length - 12;
        int padding = size - png.length - 12;
        System.arraycopy(png, 0, result, 0, offset);
        java.nio.ByteBuffer.wrap(result).putInt(offset, padding).putInt(offset + 4, 0x72614e64);
        var crc = new java.util.zip.CRC32();
        crc.update(result, offset + 4, padding + 4);
        java.nio.ByteBuffer.wrap(result).putInt(offset + padding + 8, (int) crc.getValue());
        System.arraycopy(png, offset, result, size - 12, 12);
        return result;
    }

    /** Rewrites IHDR with a valid checksum so dimension rejection is independent of corruption. */
    private static byte[] dimensions(byte[] bytes, int width, int height) {
        java.nio.ByteBuffer.wrap(bytes).putInt(16, width).putInt(20, height);
        var crc = new java.util.zip.CRC32();
        crc.update(bytes, 12, 17);
        java.nio.ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
        return bytes;
    }

    /** Creates canonical facts with no bundled image. */
    private static Ship ship() {
        return new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common, Role.None,
                "http fixture", 0, 0, 0, RuleType.All.rewardBonus(0), "");
    }

    private static InputStream resource(String name) {
        return ShipArtworkHttpTest.class.getResourceAsStream("/com/kor/admiralty/ui/resources/" + name);
    }

    /** Encodes recognizable real PNG bytes to exercise the production decoder. */
    private static byte[] png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.MAGENTA);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static int pixel(ImageIcon icon) {
        return ((BufferedImage) icon.getImage()).getRGB(32, 32);
    }
}
