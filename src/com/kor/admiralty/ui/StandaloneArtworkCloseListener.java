/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.ui.artwork.ShipArtwork;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

/** Ends a standalone window's owned Ship Artwork lifetime with the window. */
final class StandaloneArtworkCloseListener extends WindowAdapter {
    private final ShipArtwork artwork;

    /**
     * Captures the module supplied to a standalone root rather than looking up
     * process state after its window has begun closing.
     *
     * @param artwork the root's owned Ship Artwork instance
     */
    StandaloneArtworkCloseListener(ShipArtwork artwork) {
        this.artwork = Objects.requireNonNull(artwork, "artwork");
    }

    /** Closes before an EXIT_ON_CLOSE frame terminates the process. */
    @Override
    public void windowClosing(WindowEvent event) {
        artwork.close();
    }

    /**
     * Also closes frames disposed without a closing request. Both events can
     * occur for one window, and Ship Artwork deliberately accepts repeated close.
     */
    @Override
    public void windowClosed(WindowEvent event) {
        artwork.close();
    }
}
