/**
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.renderers.ShipCellRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises shared Ship presentation through real Ship Artwork handles. */
class ShipRendererArtworkTest {

    @TempDir
    Path directory;

    /**
     * Generic GameData and One-Time cards stay offline; reusable cards request specific artwork.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void shipCardsChooseNamedPresentationFromCanonicalRosterState() throws Exception {
        Ship ship = new ShipImpl(ShipFaction.Federation, Tier.Tier6, Rarity.Epic,
                Role.Eng, "Renderer Artwork Probe", 10, 20, 30,
                RuleType.All.rewardBonus(0), "");
        GameData data = GameData.builder().ships(List.of(ship)).build();
        Admiral admiral = new Admiral(data);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(ship, 1);
        RosterCard reusable = admiral.getRoster().getReusableCards().getFirst();
        RosterCard oneTime = admiral.getRoster().getOneTimeCards().getFirst();
        List<String> requests = new ArrayList<>();

        try (ShipArtwork artwork = new ShipArtwork(directory, data, List.of(),
                name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, completed) -> {
                    requests.add(name);
                    completed.accept(null);
                })) {
            SwingUtilities.invokeAndWait(() -> {
                ShipCellRenderer renderer = new ShipCellRenderer(artwork);
                renderer.getListCellRendererComponent(new JList<Ship>(), ship, 0, false, false);
                assertSame(artwork.forShip(ship, ShipArtwork.Presentation.GENERIC), icon(renderer));
                assertEquals(List.of(), requests);

                renderer.setRosterCard(oneTime);
                assertSame(artwork.forShip(ship, ShipArtwork.Presentation.GENERIC), icon(renderer));
                assertTrue(labels(renderer).contains("(1x) " + ship.getName()));
                assertEquals(List.of(), requests);

                renderer.setRosterCard(reusable);
                assertSame(artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC), icon(renderer));
                assertTrue(labels(renderer).contains(ship.getDisplayName()));
                assertEquals(List.of(ship.getIconName()), requests);
            });
        }
    }

    /** Reads the visible icon from the renderer's established Swing component surface. */
    private static ImageIcon icon(ShipCellRenderer renderer) {
        return Arrays.stream(renderer.getComponents())
                .filter(JLabel.class::isInstance)
                .map(JLabel.class::cast)
                .map(JLabel::getIcon)
                .filter(ImageIcon.class::isInstance)
                .map(ImageIcon.class::cast)
                .findFirst().orElseThrow();
    }

    /** Reads visible text from the renderer without depending on private fields. */
    private static List<String> labels(ShipCellRenderer renderer) {
        return Arrays.stream(renderer.getComponents())
                .filter(JLabel.class::isInstance)
                .map(JLabel.class::cast)
                .map(JLabel::getText)
                .toList();
    }
}
