/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.AssignmentView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.renderers.ShipCellRenderer;
import com.kor.admiralty.ui.resources.ActualShipIconFactory;
import com.kor.admiralty.ui.resources.IconCache;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.shipfilter.ShipFilterViews;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Captures real artwork-bearing Swing surfaces without native windows or user state. */
public final class ShipArtworkVisualBaseline {

    private ShipArtworkVisualBaseline() {
    }

    /**
     * Writes review images to a build directory unless an explicit destination is supplied.
     * All Swing construction and painting runs on the event-dispatch thread.
     *
     * @param args optional output directory; checked-in baselines require an explicit path
     * @throws Exception if event dispatch, look-and-feel setup, or image output fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Expected an optional output directory");
        }
        Path output = args.length == 0 ? Path.of("build/ship-artwork-views") : Path.of(args[0]);
        Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            try {
                // Metal makes the capture independent of the desktop's selected native theme.
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                for (Map.Entry<String, JComponent> surface : surfaces(output).entrySet()) {
                    if (!ImageIO.write(paint(surface.getValue()), "png", output.resolve(surface.getKey() + ".png").toFile())) {
                        throw new IOException("No PNG writer is available");
                    }
                }
            } catch (IOException cause) {
                throw new UncheckedIOException(cause);
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException cause) {
                throw new IllegalStateException(cause);
            }
        });
    }

    /**
     * Creates the nine production surface variants with synthetic, canonical Ship facts.
     * The cache is never loaded or saved, so even an existing destination cannot supply user pixels.
     *
     * @param scratch path used only to construct an empty in-memory Icon Cache
     * @return surfaces in the README's review order; caller must be on the event thread
     */
    static Map<String, JComponent> surfaces(Path scratch) {
        Ship cruiser = ship("Cruiser", ShipFaction.Federation, Role.Eng, Rarity.Epic, "Artwork baseline trait");
        Ship warbird = ship("Dhelan Warbird", ShipFaction.Romulan, Role.Sci, Rarity.VeryRare, "");
        GameData data = GameData.builder().ships(List.of(cruiser, warbird))
                .traits(Map.of("Artwork baseline trait", "A stable trait description for artwork review.")).build();
        Admiral admiral = Admiral.restore(data, "Artwork baseline", PlayerFaction.RomulanFed,
                List.of(cruiser, warbird), List.of(), List.of(cruiser), Map.of(), true);
        ShipIconFactory icons = new ActualShipIconFactory(new IconCache(scratch));
        ShipFilterViews views = new ShipFilterViews(icons);
        Map<String, JComponent> surfaces = new LinkedHashMap<>();
        surfaces.put("reusable-roster", views.reusableRoster(admiral.getRoster().getReusableCards()));
        surfaces.put("one-time-ships", views.oneTimeRoster(admiral.getRoster().getOneTimeCards()));
        surfaces.put("roster-starship-traits", views.rosterStarshipTraits(admiral.getRoster().getReusableCards()));
        surfaces.put("gamedata-starship-traits", views.gameDataStarshipTraits(data.ships()));
        surfaces.put("reusable-selection", dialogContent(views.reusableShipSelection(PlayerFaction.RomulanFed, data.ships())));
        surfaces.put("one-time-selection", dialogContent(views.oneTimeShipSelection(PlayerFaction.RomulanFed, data.ships())));
        surfaces.put("roster-card-selection", dialogContent(views.rosterCardSelection(admiral.getRoster().getReusableCards())));
        surfaces.put("ship-usage", views.shipUsage(List.of(
                new ShipUsageRow(cruiser, 12, true), new ShipUsageRow(warbird, 7, false))));

        AssignmentView assignment = new AssignmentView(150, 150, 150, 0, 0, 0, 0, 80, 120);
        admiral.getAssignment(0).apply(assignment);
        AssignmentPanel solution = new AssignmentPanel(data, icons);
        solution.setAssignmentView(assignment, ignored -> {
            // A baseline only projects state; no interactive editor changes are persisted.
        });
        solution.setAssignmentSolution(admiral.solveAssignments().getFirst().getSolution(0));
        surfaces.put("solution-cards", solution);
        return surfaces;
    }

    /** Creates synthetic statistics with a real bundled icon filename; these are not live game facts. */
    private static Ship ship(String name, ShipFaction faction, Role role, Rarity rarity, String trait) {
        return new ShipImpl(faction, Tier.Tier6, rarity, role, name, 50, 50, 50,
                RuleType.All.rewardBonus(0), trait);
    }

    /** Wraps actual selection content in the production option labels without opening a modal window. */
    private static JComponent dialogContent(JComponent view) {
        children(view, JList.class).getFirst().setSelectedIndex(0);
        return new JOptionPane(view, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null,
                new String[]{com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.LabelOkay,
                        com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.LabelCancel});
    }

    /** Paints the complete production content at fixed review dimensions on the event thread. */
    static BufferedImage paint(JComponent surface) {
        // Lightweight peers let CellRendererPane validate renderer children in headless mode.
        surface.addNotify();
        surface.setSize(1000, surface instanceof AssignmentPanel ? 620 : 400);
        layout(surface);
        BufferedImage image = new BufferedImage(surface.getWidth(), surface.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            surface.printAll(graphics);
        } finally {
            graphics.dispose();
            surface.removeNotify();
        }
        return image;
    }

    /** Lays out every nested Swing container because headless captures have no validating native peer. */
    private static void layout(Container root) {
        root.doLayout();
        for (Component component : root.getComponents()) {
            if (component instanceof Container child) {
                layout(child);
            }
        }
    }

    /** Collects matching components recursively, including the root, in visual tree order. */
    static <T> List<T> children(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) {
            matches.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                matches.addAll(children(child, type));
            }
        }
        return matches;
    }

    /**
     * Reads artwork from actual list renderer components and embedded Solution cards.
     * Details artwork is returned separately by the details-panel assertion in the test.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<Icon> cardArtwork(JComponent surface) {
        List<Icon> icons = new ArrayList<>();
        for (JList list : children(surface, JList.class)) {
            for (int index = 0; index < list.getModel().getSize(); index++) {
                Component card = list.getCellRenderer().getListCellRendererComponent(
                        list, list.getModel().getElementAt(index), index, index == 0, false);
                icons.add(artwork(card));
            }
        }
        if (surface instanceof AssignmentPanel) {
            for (ShipCellRenderer card : children(surface, ShipCellRenderer.class)) {
                icons.add(artwork(card));
            }
        }
        return icons;
    }

    /** Finds the 64-pixel artwork label, excluding the small statistic glyphs. */
    private static Icon artwork(Component card) {
        return children(card, JLabel.class).stream().map(JLabel::getIcon)
                .filter(icon -> icon != null && icon.getIconWidth() == 64 && icon.getIconHeight() == 64)
                .findFirst().orElseThrow(() -> new AssertionError("Missing 64 x 64 artwork"));
    }
}
