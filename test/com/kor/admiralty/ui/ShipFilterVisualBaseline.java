/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.AppTestFixture;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.ShipSortOrder;
import com.kor.admiralty.io.AdmiralsStore;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.panels.AdmiralPanel;
import com.kor.admiralty.ui.resources.ActualShipIconFactory;
import com.kor.admiralty.ui.resources.IconCache;
import com.kor.admiralty.ui.resources.Swing;
import com.kor.admiralty.ui.shipfilter.ShipFilterView;
import com.kor.admiralty.ui.shipfilter.ShipFilterViews;
import org.jdesktop.swingx.JXTaskPane;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.*;
import static com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.LabelCancel;
import static com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.LabelOkay;

/**
 * Opens deterministic Ship Filter views for native screenshot comparison. The
 * established names retain pre-migration baselines while explicitly suffixed
 * modes render migrated presentations. This manual tool is intentionally not a
 * Surefire test.
 */
public final class ShipFilterVisualBaseline {

    private ShipFilterVisualBaseline() {
    }

    /**
     * Loads the stable test GameData fixture, publishes isolated application
     * state, and opens the requested visual comparison view on the Swing event
     * thread.
     *
     * @param args one view name and, optionally, a PNG output path documented by
     *             the visual-baseline README
     * @throws Exception if fixture loading or event-thread dispatch fails
     */
    static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Expected a view name and optional PNG output path");
        }
        Fixture fixture = fixture();
        Swing.setLookAndFeel();
        SwingUtilities.invokeAndWait(() -> show(args[0], fixture));
        if (args.length == 2) {
            Path output = Path.of(args[1]);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    captureVisibleWindow(output);
                } catch (IOException cause) {
                    throw new UncheckedIOException(cause);
                }
            });
            System.exit(0);
        }
    }

    /**
     * Renders the visible real Swing window to a PNG. This component-level capture
     * remains usable when automation and Swing run in separate Windows stations.
     *
     * @param output destination PNG path
     * @throws IOException if the output directory or image cannot be written
     */
    private static void captureVisibleWindow(Path output) throws IOException {
        Window window = java.util.Arrays.stream(Window.getWindows())
                .filter(Window::isVisible)
                .max(java.util.Comparator.comparingLong(
                        candidate -> (long) candidate.getWidth() * candidate.getHeight()))
                .orElseThrow(() -> new IllegalStateException("No visible visual-baseline window"));
        window.validate();
        BufferedImage image = new BufferedImage(
                window.getWidth(),
                window.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            window.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IOException("No PNG writer is available");
        }
    }

    /**
     * Opens one requested dialog, embedded presentation, or standalone frame.
     *
     * @param view    stable command-line view name
     * @param fixture isolated GameData, Admiral, and artwork dependencies
     */
    private static void show(String view, Fixture fixture) {
        switch (view) {
            case "active-dialog" -> showActiveDialog(fixture);
            case "active-dialog-after" -> showMigratedActiveDialog(fixture);
            case "one-time-dialog" -> showOneTimeDialog(fixture);
            case "one-time-dialog-after" -> showMigratedOneTimeDialog(fixture);
            case "roster-card-dialog" -> showRosterCardDialog(fixture);
            case "roster-card-dialog-after" -> showMigratedRosterCardDialog(fixture);
            case "primary-roster" -> showAdmiralTab(fixture, "Primary Ships");
            case "one-time-roster" -> showAdmiralTab(fixture, "One-Time Ships");
            case "roster-traits" -> showAdmiralTab(fixture, "Starship Traits");
            case "game-data-traits" -> showTraitViewer();
            case "ship-usage" -> showShipUsage();
            default -> throw new IllegalArgumentException("Unknown visual baseline view: " + view);
        }
    }

    /**
     * Shows reusable Ship selection with its filter expanded and Ship details
     * populated from the first visible candidate.
     *
     * @param fixture isolated visual data
     */
    private static void showActiveDialog(Fixture fixture) {
        ShipSelectionPanel panel = ShipSelectionPanel.activeShips(
                PlayerFaction.RomulanFed,
                fixture.gameData().ships(),
                fixture.iconRenderer());
        prepareSelectionPanel(panel);
        showDialog(TitleAddActiveShips, panel);
    }

    /**
     * Shows migrated reusable Ship selection with the same deterministic state
     * as the retained pre-migration baseline.
     *
     * @param fixture isolated visual data
     */
    private static void showMigratedActiveDialog(Fixture fixture) {
        ShipFilterView<Ship, ShipSortOrder> panel = new ShipFilterViews(fixture.iconRenderer())
                .reusableShipSelection(PlayerFaction.RomulanFed, fixture.gameData().ships());
        prepareSelectionPanel(panel);
        showDialog(TitleAddActiveShips, panel);
    }

    /**
     * Shows One-Time Ship selection with the combined Admiral and Tier 6 profile
     * visible in its expanded filter.
     *
     * @param fixture isolated visual data
     */
    private static void showOneTimeDialog(Fixture fixture) {
        ShipSelectionPanel panel = ShipSelectionPanel.oneTimeShips(
                PlayerFaction.RomulanFed,
                fixture.gameData().ships(),
                fixture.iconRenderer());
        prepareSelectionPanel(panel);
        showDialog(TitleAddOneTimeShips, panel);
    }

    /**
     * Shows migrated One-Time Ship selection with the same deterministic state
     * as the retained pre-migration baseline.
     *
     * @param fixture isolated visual data
     */
    private static void showMigratedOneTimeDialog(Fixture fixture) {
        ShipFilterView<Ship, ShipSortOrder> panel = new ShipFilterViews(fixture.iconRenderer())
                .oneTimeShipSelection(PlayerFaction.RomulanFed, fixture.gameData().ships());
        prepareSelectionPanel(panel);
        showDialog(TitleAddOneTimeShips, panel);
    }

    /**
     * Shows the established compact list-only Roster-card selection dialog.
     *
     * @param fixture isolated visual data
     */
    private static void showRosterCardDialog(Fixture fixture) {
        ShipListPanel<RosterCard, ShipSortOrder> panel = ShipListPanel.rosterCards(
                fixture.admiral().getRoster().getReusableCards(),
                fixture.iconRenderer());
        expandFilter(panel);
        selectFirstEntry(panel);
        showDialog(TitleRemoveActiveShips, panel);
    }

    /**
     * Shows migrated list-only RosterCard selection with the same deterministic
     * state as the retained pre-migration baseline.
     *
     * @param fixture isolated visual data
     */
    private static void showMigratedRosterCardDialog(Fixture fixture) {
        ShipFilterView<RosterCard, ShipSortOrder> panel = new ShipFilterViews(fixture.iconRenderer())
                .rosterCardSelection(fixture.admiral().getRoster().getReusableCards());
        expandFilter(panel);
        selectFirstEntry(panel);
        showDialog(TitleRemoveActiveShips, panel);
    }

    /**
     * Expands the filter and selects the first visible Ship so its details panel
     * participates in the baseline.
     *
     * @param panel configured Ship selection surface
     */
    private static void prepareSelectionPanel(Container panel) {
        expandFilter(panel);
        selectFirstEntry(panel);
    }

    /**
     * Expands every filter task pane under one selection surface.
     *
     * @param root selection surface to prepare
     */
    private static void expandFilter(Container root) {
        for (JXTaskPane taskPane : children(root, JXTaskPane.class)) {
            taskPane.setCollapsed(false);
        }
    }

    /**
     * Selects the first visible entry in a selection surface when one exists.
     *
     * @param root selection surface containing the production list
     */
    private static void selectFirstEntry(Container root) {
        JList<?> list = child(root, JList.class);
        if (list.getModel().getSize() > 0) {
            list.setSelectedIndex(0);
        }
    }

    /**
     * Wraps real dialog content in the same JOptionPane option configuration as
     * production while keeping the window modeless for automated capture.
     *
     * @param title established dialog title
     * @param panel production dialog content
     */
    private static void showDialog(String title, Component panel) {
        JOptionPane optionPane = new JOptionPane(
                panel,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION,
                null,
                new String[]{LabelOkay, LabelCancel},
                LabelOkay);
        JDialog dialog = optionPane.createDialog(null, title);
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
        dialog.toFront();
    }

    /**
     * Shows the real Admiral workspace with one passive presentation tab selected.
     *
     * @param fixture  isolated visual data
     * @param tabTitle established tab title
     */
    private static void showAdmiralTab(Fixture fixture, String tabTitle) {
        AdmiralPanel panel = new AdmiralPanel(
                fixture.admiral(),
                fixture.gameData(),
                fixture.admiralsStore(),
                Path.of("target", "visual-baseline-data"),
                fixture.iconRenderer());
        JTabbedPane tabs = child(panel, JTabbedPane.class);
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (tabTitle.equals(tabs.getTitleAt(index))) {
                tabs.setSelectedIndex(index);
                break;
            }
        }
        JFrame frame = new JFrame("Ship Filter Before - " + tabTitle);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(panel);
        frame.setSize(1100, 760);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
    }

    /**
     * Shows the production standalone GameData Starship Trait frame.
     */
    private static void showTraitViewer() {
        TraitViewer viewer = new TraitViewer();
        viewer.setTitle("Ship Filter Before - GameData Starship Traits");
        viewer.setLocationRelativeTo(null);
        viewer.setVisible(true);
        viewer.toFront();
    }

    /**
     * Shows Ship Usage in its established initial Most Used presentation.
     */
    private static void showShipUsage() {
        ShipUsageFrame frame = new ShipUsageFrame();
        frame.setTitle("Ship Filter Before - Ship Usage");
        frame.setVisible(true);
        frame.toFront();
    }

    /**
     * Loads the stable test GameData fixture and publishes one deterministic
     * Admiral with Active, Maintenance, One-Time, trait, and usage examples.
     *
     * @return complete visual fixture
     * @throws Exception if GameData or application dependencies cannot initialize
     */
    private static Fixture fixture() throws Exception {
        GameData gameData = GameData.load(Path.of("test", "resources", "gamedata"));
        Ship enterprise = ship(gameData, "U.S.S. Enterprise");
        Ship shuttle = ship(gameData, "Class F Shuttle");
        Ship bortas = ship(gameData, "I.K.S. Bortas");
        Ship dhelan = ship(gameData, "R.R.W. Dhelan");
        Ship jemHadarCarrier = ship(gameData, "Jem'Hadar Vanguard Carrier");
        Admiral admiral = Admiral.restore(
                gameData,
                "Visual Baseline Admiral",
                PlayerFaction.RomulanFed,
                List.of(enterprise, shuttle),
                List.of(bortas, dhelan),
                List.of(jemHadarCarrier, enterprise, enterprise),
                Map.of(
                        enterprise, 12,
                        shuttle, 7,
                        bortas, 7,
                        dhelan, 2,
                        jemHadarCarrier, 4),
                true);
        Admirals admirals = Admirals.restore(gameData, List.of(admiral));
        Path dataDirectory = Path.of("target", "visual-baseline-data");
        Files.createDirectories(dataDirectory);
        IconCache iconCache = new IconCache(dataDirectory);
        iconCache.load();
        AdmiralsStore admiralsStore = new AdmiralsStore();
        AppTestFixture.initialize(gameData, admirals, dataDirectory, admiralsStore, iconCache);
        return new Fixture(gameData, admiral, admiralsStore, new ActualShipIconFactory(iconCache));
    }

    /**
     * Resolves one required canonical Ship by its test GameData fixture name.
     *
     * @param gameData loaded test reference data
     * @param name     exact canonical Ship name
     * @return required canonical Ship
     */
    private static Ship ship(GameData gameData, String name) {
        Ship ship = gameData.ship(name);
        if (ship == null) {
            throw new IllegalArgumentException("Visual baseline Ship is missing: " + name);
        }
        return ship;
    }

    /**
     * Finds the first component of one type in a Swing subtree.
     *
     * @param root          subtree to inspect
     * @param componentType requested component type
     * @param <T>           concrete component type
     * @return first matching component
     * @throws IllegalArgumentException if no component matches
     */
    private static <T extends Component> T child(Container root, Class<T> componentType) {
        for (Component component : root.getComponents()) {
            if (componentType.isInstance(component)) {
                return componentType.cast(component);
            }
            if (component instanceof Container container) {
                try {
                    return child(container, componentType);
                } catch (IllegalArgumentException ignored) {
                    // Continue through sibling containers until the requested child is found.
                }
            }
        }
        throw new IllegalArgumentException("No child component found: " + componentType.getName());
    }

    /**
     * Collects every component of one type from a Swing subtree.
     *
     * @param root          subtree to inspect
     * @param componentType requested component type
     * @param <T>           concrete component type
     * @return matching descendants in depth-first order
     */
    private static <T extends Component> List<T> children(Container root, Class<T> componentType) {
        java.util.ArrayList<T> matches = new java.util.ArrayList<T>();
        for (Component component : root.getComponents()) {
            if (componentType.isInstance(component)) {
                matches.add(componentType.cast(component));
            }
            if (component instanceof Container container) {
                matches.addAll(children(container, componentType));
            }
        }
        return matches;
    }

    /**
     * Groups application state needed by every visual baseline mode.
     *
     * @param gameData      loaded test reference data
     * @param admiral       deterministic Admiral state
     * @param admiralsStore initialized persistence dependency
     * @param iconRenderer  production artwork adapter over an isolated Icon Cache
     */
    private record Fixture(
            GameData gameData,
            Admiral admiral,
            AdmiralsStore admiralsStore,
            ActualShipIconFactory iconRenderer) {
    }
}
