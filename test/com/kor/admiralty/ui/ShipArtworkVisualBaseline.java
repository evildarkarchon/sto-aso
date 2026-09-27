/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.AssignmentView;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import com.kor.admiralty.ui.artwork.ShipArtworkTestFixture;
import com.kor.admiralty.ui.renderers.ShipCellRenderer;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Captures real artwork-bearing Swing surfaces without native windows or user state. */
public final class ShipArtworkVisualBaseline {

    private ShipArtworkVisualBaseline() {
    }

    /**
     * Writes review images to a build directory unless an explicit destination is supplied.
     * The --live-replacement mode writes a paired capture of one retained Roster card.
     * Swing view construction and painting run on the event-dispatch thread.
     *
     * @param args optional output directory, or --live-replacement and its output directory
     * @throws Exception if event dispatch, look-and-feel setup, or image output fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--live-replacement".equals(args[0])) {
            if (args.length != 2) {
                throw new IllegalArgumentException("--live-replacement requires an output directory");
            }
            captureLiveReplacement(Path.of(args[1]));
            return;
        }
        if (args.length > 1) {
            throw new IllegalArgumentException("Expected an optional output directory or --live-replacement and a directory");
        }
        Path output = args.length == 0 ? Path.of("build/ship-artwork-views") : Path.of(args[0]);
        Files.createDirectories(output);
        Path scratch = Files.createTempDirectory("ship-artwork-views-");
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    // Metal makes the capture independent of the desktop's selected native theme.
                    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                    try (SurfaceSet baseline = surfaces(scratch)) {
                        for (Map.Entry<String, JComponent> surface : baseline.surfaces().entrySet()) {
                            if (!ImageIO.write(paint(surface.getValue()), "png", output.resolve(surface.getKey() + ".png").toFile())) {
                                throw new IOException("No PNG writer is available");
                            }
                        }
                    }
                } catch (IOException cause) {
                    throw new UncheckedIOException(cause);
                } catch (ReflectiveOperationException | UnsupportedLookAndFeelException cause) {
                    throw new IllegalStateException(cause);
                }
            });
        } finally {
            // The output is only for PNGs; a fresh empty directory owns temporary artwork state.
            Files.delete(scratch);
        }
    }

    /**
     * Captures the same mounted reusable-Roster view before and after a scripted source completes.
     * The deliberately unbundled Ship keeps the first frame generic without network access.
     *
     * @param output destination for live-before.png and live-after.png
     * @throws Exception if Swing dispatch, image output, or the expected live transition fails
     */
    private static void captureLiveReplacement(Path output) throws Exception {
        Files.createDirectories(output);
        Ship ship = ship("Live Capture Cruiser", ShipFaction.Federation, Role.Eng, Rarity.Epic, "");
        String resource = "/com/kor/admiralty/ui/resources/" + ship.getIconName();
        if (ShipArtworkVisualBaseline.class.getResource(resource) != null) {
            throw new IllegalStateException("Live capture Ship unexpectedly has a bundled source");
        }
        Path scratch = Files.createTempDirectory("ship-artwork-live-");
        GameData data = GameData.builder().ships(List.of(ship)).build();
        Admiral admiral = new Admiral(data);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        AtomicReference<Consumer<BufferedImage>> completion = new AtomicReference<>();
        AtomicReference<LiveSurface> mounted = new AtomicReference<>();
        try (ShipArtwork artwork = ShipArtworkTestFixture.scripted(scratch, data, (name, done) -> {
            if (!ship.getIconName().equals(name)) {
                throw new IllegalStateException("Unexpected live capture source: " + name);
            }
            completion.set(done);
        })) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            int fallbackPixel = ((BufferedImage) icon.getImage()).getRGB(32, 32);
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                        JComponent view = new ShipFilterViews(artwork)
                                .reusableRoster(admiral.getRoster().getReusableCards());
                        JList<?> list = children(view, JList.class).getFirst();
                        list.setSelectedIndex(0);
                        mounted.set(new LiveSurface(view, list, list.getModel(), list.getSelectedValue()));
                        // Keep this peer mounted so the second frame is the same already-open card.
                        view.addNotify();
                        view.setSize(1000, 400);
                        layout(view);
                        writePng(paintMounted(view), output.resolve("live-before.png"));
                    } catch (IOException cause) {
                        throw new UncheckedIOException(cause);
                    } catch (ReflectiveOperationException | UnsupportedLookAndFeelException cause) {
                        throw new IllegalStateException(cause);
                    }
                });
                Consumer<BufferedImage> done = completion.get();
                if (done == null) {
                    throw new IllegalStateException("No acquisition was requested for the live capture Ship");
                }
                done.accept(source(Color.MAGENTA));
                // Completion queues an EDT pixel swap; the next invokeAndWait runs after it.
                SwingUtilities.invokeAndWait(() -> {
                    LiveSurface state = mounted.get();
                    if (artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC) != icon
                            || state.list().getModel() != state.model()
                            || state.list().getSelectedValue() != state.selection()) {
                        throw new IllegalStateException("Live artwork replaced the view, model, selection, or handle");
                    }
                    if (((BufferedImage) icon.getImage()).getRGB(32, 32) == fallbackPixel) {
                        throw new IllegalStateException("Live artwork did not replace fallback pixels");
                    }
                    try {
                        writePng(paintMounted(state.view()), output.resolve("live-after.png"));
                    } catch (IOException cause) {
                        throw new UncheckedIOException(cause);
                    }
                });
            } finally {
                if (mounted.get() != null) {
                    SwingUtilities.invokeAndWait(() -> mounted.get().view().removeNotify());
                }
            }
        } finally {
            deleteScratch(scratch);
        }
    }

    /** Creates unmistakable synthetic source pixels for the offline live capture. */
    private static BufferedImage source(Color color) {
        BufferedImage image = new BufferedImage(132, 132, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Writes a PNG only when the installed ImageIO provider supports it. */
    private static void writePng(BufferedImage image, Path path) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("No PNG writer is available");
        }
    }

    /** Removes only the scratch directory created for this capture and its generated archive. */
    private static void deleteScratch(Path scratch) throws IOException {
        try (var paths = Files.walk(scratch)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Retains Swing identities while the same view is painted in both frames. */
    private record LiveSurface(JComponent view, JList<?> list, ListModel<?> model, Object selection) { }

    /**
     * Creates the nine production surface variants with synthetic, canonical Ship facts.
     * The isolated offline artwork lifetime prevents user pixels or network work
     * from changing the captures.
     *
     * @param scratch isolated directory for the artwork lifetime
     * @return owned surfaces in the README's review order; caller closes the result
     * @throws IllegalStateException if called outside the Swing event-dispatch thread
     */
    static SurfaceSet surfaces(Path scratch) {
        Ship cruiser = ship("Cruiser", ShipFaction.Federation, Role.Eng, Rarity.Epic, "Artwork baseline trait");
        Ship warbird = ship("Dhelan Warbird", ShipFaction.Romulan, Role.Sci, Rarity.VeryRare, "");
        GameData data = GameData.builder().ships(List.of(cruiser, warbird))
                .traits(Map.of("Artwork baseline trait", "A stable trait description for artwork review.")).build();
        Admiral admiral = Admiral.restore(data, "Artwork baseline", PlayerFaction.RomulanFed,
                List.of(cruiser, warbird), List.of(), List.of(cruiser), Map.of(), true);
        ShipArtwork artwork = ShipArtworkTestFixture.offline(scratch, data, List.of(cruiser, warbird));
        try {
            ShipFilterViews views = new ShipFilterViews(artwork);
            Map<String, JComponent> surfaces = new LinkedHashMap<>();
            surfaces.put("reusable-roster", views.reusableRoster(admiral.getRoster().getReusableCards()));
            surfaces.put("one-time-ships", views.oneTimeRoster(admiral.getRoster().getOneTimeCards()));
            surfaces.put("roster-starship-traits", views.rosterStarshipTraits(admiral.getRoster().getReusableCards()));
            surfaces.put("gamedata-starship-traits", views.gameDataStarshipTraits(data.ships()));
            surfaces.put("reusable-selection", dialogContent(views.reusableShipSelection(PlayerFaction.RomulanFed, data.ships())));
            surfaces.put("one-time-selection", dialogContent(views.oneTimeShipSelection(PlayerFaction.RomulanFed, data.ships())));
            surfaces.put("roster-card-selection", dialogContent(views.rosterCardSelection(admiral.getRoster().getReusableCards())));
            surfaces.put("ship-usage", views.shipUsage(List.of(
                    new ShipUsageRow(cruiser, 12, true, true), new ShipUsageRow(warbird, 7, false, false))));

            AssignmentView assignment = new AssignmentView(150, 150, 150, 0, 0, 0, 0, 80, 120);
            admiral.getAssignment(0).apply(assignment);
            AssignmentPanel solution = new AssignmentPanel(data, artwork);
            solution.setAssignmentView(assignment, ignored -> {
                // A baseline only projects state; no interactive editor changes are persisted.
            });
            solution.setAssignmentSolution(admiral.solveAssignments().getFirst().getSolution(0));
            surfaces.put("solution-cards", solution);
            return new SurfaceSet(data, artwork, surfaces);
        } catch (RuntimeException | Error failure) {
            // Construction has not transferred ownership to SurfaceSet yet.
            artwork.close();
            throw failure;
        }
    }

    /** Owns the canonical data and live artwork used by one set of visual surfaces. */
    record SurfaceSet(GameData gameData, ShipArtwork artwork, Map<String, JComponent> surfaces)
            implements AutoCloseable {
        /** Releases the deterministic artwork lifetime after every surface is inspected. */
        @Override
        public void close() {
            artwork.close();
        }
    }

    /** Creates synthetic Ship facts; the caller selects a bundled or deliberately unbundled name. */
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
        try {
            return paintMounted(surface);
        } finally {
            surface.removeNotify();
        }
    }

    /** Paints a laid-out Swing view without removing its peer between live frames. */
    private static BufferedImage paintMounted(JComponent surface) {
        BufferedImage image = new BufferedImage(surface.getWidth(), surface.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            surface.printAll(graphics);
        } finally {
            graphics.dispose();
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
