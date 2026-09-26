/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import javax.swing.ImageIcon;
import javax.swing.CellRendererPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/** A stable Swing handle whose owned pixels and presentation cannot be changed by callers. */
final class ArtworkHandle extends ImageIcon {
    private static final long serialVersionUID = 1L;
    private volatile BufferedImage pixels;
    private final List<WeakReference<Component>> owners = new ArrayList<>();

    /** Takes ownership of a completed, private 64-pixel composition. */
    ArtworkHandle(BufferedImage pixels) {
        this.pixels = pixels;
    }

    /** Paints owned pixels and weakly remembers the useful Swing owner, including renderer hosts. */
    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        rememberOwner(component);
        graphics.drawImage(pixels, x, y, null);
    }

    /** Resolves rubber-stamp renderer components to their list/table host without retaining views. */
    private synchronized void rememberOwner(Component component) {
        Component owner = component;
        for (Component ancestor = component; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof CellRendererPane) {
                owner = ancestor.getParent();
                break;
            }
        }
        if (owner == null || !owner.isShowing()) {
            return;
        }
        owners.removeIf(reference -> reference.get() == null);
        for (var reference : owners) {
            if (reference.get() == owner) {
                return;
            }
        }
        owners.add(new WeakReference<>(owner));
    }

    /** Takes private composed pixels on the EDT and repaints visible owners only when pixels change. */
    synchronized void replace(BufferedImage replacement) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Artwork replacement requires the Swing event thread");
        }
        if (Arrays.equals(pixels.getRGB(0, 0, 64, 64, null, 0, 64),
                replacement.getRGB(0, 0, 64, 64, null, 0, 64))) {
            return;
        }
        pixels = replacement;
        owners.removeIf(reference -> {
            Component owner = reference.get();
            if (owner == null || !owner.isShowing()) {
                return true;
            }
            owner.repaint();
            return false;
        });
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
