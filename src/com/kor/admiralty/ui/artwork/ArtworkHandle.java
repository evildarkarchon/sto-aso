/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import javax.swing.ImageIcon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/** A stable Swing handle whose owned pixels and presentation cannot be changed by callers. */
final class ArtworkHandle extends ImageIcon {
    private static final long serialVersionUID = 1L;
    private final BufferedImage pixels;

    /** Takes ownership of a completed, private 64-pixel composition. */
    ArtworkHandle(BufferedImage pixels) {
        this.pixels = pixels;
    }

    /** Paints owned pixels directly; no source loading or mutable image escapes through painting. */
    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        graphics.drawImage(pixels, x, y, null);
    }

    /** Returns a defensive snapshot because ImageIcon callers can otherwise mutate shared pixels. */
    @Override
    public Image getImage() {
        BufferedImage copy = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, 64, 64, pixels.getRGB(0, 0, 64, 64, null, 0, 64), 0, 64);
        return copy;
    }

    @Override
    public int getIconWidth() {
        return 64;
    }

    @Override
    public int getIconHeight() {
        return 64;
    }

    @Override
    public int getImageLoadStatus() {
        return MediaTracker.COMPLETE;
    }

    /** Rejects public replacement; only the artwork lifetime owns presentation state. */
    @Override
    public void setImage(Image image) {
        throw new UnsupportedOperationException("Ship Artwork handles are read-only");
    }

    /** Rejects shared presentation metadata changes. */
    @Override
    public void setDescription(String description) {
        throw new UnsupportedOperationException("Ship Artwork handles are read-only");
    }

    /** Rejects caller-owned observer retention on a shared handle. */
    @Override
    public void setImageObserver(ImageObserver observer) {
        throw new UnsupportedOperationException("Ship Artwork handles are read-only");
    }
}
