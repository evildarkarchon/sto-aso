/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Shared strict PNG decoding for remote sources and persisted artwork. */
final class ArtworkPng {
    private ArtworkPng() { }

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

    /**
     * Checks PNG framing and checksums because ImageIO can accept files missing their end chunk.
     * Rejects dimensions above the caller's limit before allocating pixels.
     *
     * @throws IOException if framing, checksums, compressed pixels or dimensions are invalid
     */
    static BufferedImage decode(byte[] bytes, int maxDimension) throws IOException {
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
                if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension) {
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
