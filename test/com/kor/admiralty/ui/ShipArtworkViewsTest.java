/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.ui.resources.ActualShipIconFactory;
import com.kor.admiralty.ui.resources.IconCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Characterizes real surface artwork selection without platform-dependent text pixel comparisons. */
class ShipArtworkViewsTest {

    @TempDir
    Path scratch;

    /**
     * Verifies all checked-in surface captures exist and actual renderers retain specific versus generic artwork.
     *
     * @throws Exception if Swing dispatch or baseline image decoding fails
     */
    @Test
    void surfacesRetainTheirCurrentArtworkPolicies() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ActualShipIconFactory expected = new ActualShipIconFactory(new IconCache(scratch));
            Icon cruiserSpecific = expected.getIcon("Cruiser.png", ShipFaction.Federation, Role.Eng, Rarity.Epic, true);
            Icon cruiserGeneric = expected.getIcon("Cruiser.png", ShipFaction.Federation, Role.Eng, Rarity.Epic, false);
            Icon warbirdSpecific = expected.getIcon("Dhelan_Warbird.png", ShipFaction.Romulan, Role.Sci, Rarity.VeryRare, true);
            Icon warbirdGeneric = expected.getIcon("Dhelan_Warbird.png", ShipFaction.Romulan, Role.Sci, Rarity.VeryRare, false);
            assertFalse(java.util.Arrays.equals(pixels(cruiserSpecific), pixels(cruiserGeneric)),
                    "Fixture must distinguish specific artwork from generic artwork");
            Map<String, List<Icon>> policies = Map.of(
                    "reusable-roster", List.of(cruiserSpecific, warbirdSpecific),
                    "one-time-ships", List.of(cruiserGeneric),
                    "roster-starship-traits", List.of(cruiserSpecific),
                    "gamedata-starship-traits", List.of(cruiserGeneric),
                    "reusable-selection", List.of(cruiserGeneric, warbirdGeneric),
                    "one-time-selection", List.of(cruiserGeneric, warbirdGeneric),
                    "roster-card-selection", List.of(cruiserSpecific, warbirdSpecific),
                    "ship-usage", List.of(cruiserSpecific, warbirdGeneric),
                    "solution-cards", List.of(cruiserSpecific, warbirdSpecific, cruiserGeneric));
            Map<String, JComponent> surfaces = ShipArtworkVisualBaseline.surfaces(scratch);
            assertEquals(policies.keySet(), surfaces.keySet());
            for (Map.Entry<String, JComponent> surface : surfaces.entrySet()) {
                List<Icon> actual = ShipArtworkVisualBaseline.cardArtwork(surface.getValue());
                List<Icon> wanted = policies.get(surface.getKey());
                assertEquals(wanted.size(), actual.size(), surface.getKey());
                // Slot/canonical order can differ; the artwork multiset is the presentation contract here.
                List<String> actualPixels = actual.stream().map(ShipArtworkViewsTest::pixelKey).sorted().toList();
                List<String> wantedPixels = wanted.stream().map(ShipArtworkViewsTest::pixelKey).sorted().toList();
                assertEquals(wantedPixels, actualPixels, surface.getKey());
                for (ShipDetailsPanel details : ShipArtworkVisualBaseline.children(surface.getValue(), ShipDetailsPanel.class)) {
                    assertArrayEquals(pixels(warbirdGeneric), pixels(details.lblIcon.getIcon()),
                            "Selection details retain generic artwork: " + surface.getKey());
                }
                assertNotNull(ShipArtworkVisualBaseline.paint(surface.getValue()));
            }
        });
        for (String name : List.of("reusable-roster", "one-time-ships", "roster-starship-traits",
                "gamedata-starship-traits", "reusable-selection", "one-time-selection",
                "roster-card-selection", "ship-usage", "solution-cards")) {
            try (var input = getClass().getResourceAsStream("/ship-artwork/views/" + name + ".png")) {
                assertNotNull(input, name);
                BufferedImage baseline = ImageIO.read(input);
                assertNotNull(baseline, name);
                assertEquals(1000, baseline.getWidth(), name);
                assertEquals(name.equals("solution-cards") ? 620 : 400, baseline.getHeight(), name);
            }
        }
    }

    /** Produces an exact sortable pixel representation rather than a potentially colliding checksum. */
    private static String pixelKey(Icon icon) {
        return java.util.Arrays.toString(pixels(icon));
    }

    /** Paints only artwork, keeping font rendering and look-and-feel variation out of assertions. */
    private static int[] pixels(Icon icon) {
        assertEquals(64, icon.getIconWidth());
        assertEquals(64, icon.getIconHeight());
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image.getRGB(0, 0, 64, 64, null, 0, 64);
    }
}
