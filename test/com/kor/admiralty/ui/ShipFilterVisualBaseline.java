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
import com.kor.admiralty.beans.ShipUsageRow;
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
import java.awt.event.WindowEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
 * Opens current named Ship Filter views for deterministic native screenshot
 * comparison and exercises their real modal boundaries. Historical screenshots
 * remain in the baseline directories; this tool is intentionally not a Surefire test.
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
        if ("interaction-smoke".equals(args[0]) || "passive-smoke".equals(args[0])) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        if ("passive-smoke".equals(args[0])) {
                            exercisePassiveViews(fixture);
                        } else {
                            interactionSmoke(fixture);
                        }
                    } finally {
                        // JOptionPane may retain hidden owner windows after failures.
                        for (Window window : Window.getWindows()) {
                            window.dispose();
                        }
                    }
                });
                System.exit(0);
            } catch (Exception cause) {
                cause.printStackTrace();
                System.exit(1);
            }
        }
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
            case "one-time-dialog" -> showOneTimeDialog(fixture);
            case "roster-card-dialog" -> showRosterCardDialog(fixture);
            case "primary-roster" -> showAdmiralTab(fixture, "Primary Ships");
            case "one-time-roster" -> showAdmiralTab(fixture, "One-Time Ships");
            case "roster-traits" -> showAdmiralTab(fixture, "Starship Traits");
            case "game-data-traits" -> showTraitViewer();
            case "ship-usage" -> showShipUsage();
            default -> throw new IllegalArgumentException("Unknown visual baseline view: " + view);
        }
    }

    /**
     * Shows migrated reusable Ship selection with the same deterministic state
     * as the retained pre-migration baseline.
     *
     * @param fixture isolated visual data
     */
    private static void showActiveDialog(Fixture fixture) {
        ShipFilterView<Ship, ShipSortOrder> panel = new ShipFilterViews(fixture.iconRenderer())
                .reusableShipSelection(PlayerFaction.RomulanFed, fixture.gameData().ships());
        prepareSelectionPanel(panel);
        showDialog(TitleAddActiveShips, panel);
    }

    /**
     * Shows migrated One-Time Ship selection with the same deterministic state
     * as the retained pre-migration baseline.
     *
     * @param fixture isolated visual data
     */
    private static void showOneTimeDialog(Fixture fixture) {
        ShipFilterView<Ship, ShipSortOrder> panel = new ShipFilterViews(fixture.iconRenderer())
                .oneTimeShipSelection(PlayerFaction.RomulanFed, fixture.gameData().ships());
        prepareSelectionPanel(panel);
        showDialog(TitleAddOneTimeShips, panel);
    }

    /**
     * Shows migrated list-only RosterCard selection with the same deterministic
     * state as the retained pre-migration baseline.
     *
     * @param fixture isolated visual data
     */
    private static void showRosterCardDialog(Fixture fixture) {
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
     * Exercises real visible dialogs and usage controls on the EDT with isolated
     * data. This is programmatic native-window verification, not human input.
     *
     * @param fixture isolated data used by every native interaction
     */
    private static void interactionSmoke(Fixture fixture) {
        ShipFilterViews views = new ShipFilterViews(fixture.iconRenderer());
        for (String path : List.of("reusable", "one-time", "roster-card")) {
            for (String outcome : List.of("accept", "cancel", "close")) {
                exerciseDialog(fixture, views, path, outcome);
            }
        }
        ShipUsageFrame frame = new ShipUsageFrame();
        try {
            frame.setVisible(true);
            JList<?> list = child(frame, JList.class);
            verify(((ShipUsageRow) list.getModel().getElementAt(0)).deploymentCount() == 12,
                    "Usage must initially show the most-used Ship");
            exerciseFilters(frame);
            for (String label : List.of("Least Used", "Default", "Most Used")) {
                button(frame, label).doClick();
                verify(list.getModel().getSize() == 5, "Sort must retain every fixture usage row");
                if (!label.equals("Default")) {
                    int previous = label.equals("Most Used") ? Integer.MAX_VALUE : Integer.MIN_VALUE;
                    for (int index = 0; index < list.getModel().getSize(); index++) {
                        int count = ((ShipUsageRow) list.getModel().getElementAt(index)).deploymentCount();
                        verify(label.equals("Most Used") ? count <= previous : count >= previous,
                                "Usage ordering must match the chosen control");
                        previous = count;
                    }
                }
            }
            JComboBox<?> admirals = child(frame, JComboBox.class);
            for (int index = 0; index < admirals.getItemCount(); index++) {
                admirals.setSelectedIndex(index);
            }
            verify(list.getModel().getSize() == 5, "Individual Admiral usage must retain fixture rows");
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
            verify(!frame.isVisible(), "Usage close must hide the native frame");
            frame.run();
            verify(frame.isVisible(), "Usage frame must reopen after close");
            System.out.println("PASS usage: all filter controls, three order controls, all Admiral choices, close/reopen");
        } finally {
            frame.dispose();
        }
        exercisePassiveViews(fixture);
    }

    /**
     * Exercises passive production consumers in real frames, including reusable
     * card activation and wheel scrolling at constrained native window sizes.
     * All domain transitions originate from dispatched list mouse events.
     *
     * @param fixture isolated Admiral and GameData dependencies
     */
    private static void exercisePassiveViews(Fixture fixture) {
        AdmiralPanel workspace = new AdmiralPanel(fixture.admiral(), fixture.gameData(),
                fixture.admiralsStore(), Path.of("target", "visual-baseline-data"), fixture.iconRenderer());
        JFrame frame = new JFrame("Ship Filter passive interaction");
        try {
            frame.setContentPane(workspace);
            frame.setSize(1100, 760);
            frame.setVisible(true);
            frame.validate();
            JTabbedPane tabs = child(workspace, JTabbedPane.class);
            selectTab(tabs, "Primary Ships");
            List<JList> lists = children((Container) tabs.getSelectedComponent(), JList.class);
            verify(lists.size() == 2, "Primary Ships must show Active and Maintenance together");
            JList<?> active = lists.get(0);
            JList<?> maintenance = lists.get(1);
            RosterCard original = (RosterCard) active.getModel().getElementAt(0);
            doubleClick(active, 0);
            verify(active.getModel().getSize() == 1 && maintenance.getModel().getSize() == 3,
                    "Active activation must move exactly one card to Maintenance");
            int movedIndex = -1;
            for (int index = 0; index < maintenance.getModel().getSize(); index++) {
                RosterCard candidate = (RosterCard) maintenance.getModel().getElementAt(index);
                if (candidate.getId().equals(original.getId())) {
                    movedIndex = index;
                    verify(candidate.getShip() == original.getShip(), "Moved card must retain its canonical Ship");
                }
            }
            verify(movedIndex >= 0, "Maintenance must contain the exact activated card identity");
            doubleClick(maintenance, movedIndex);
            verify(active.getModel().getSize() == 2 && maintenance.getModel().getSize() == 2,
                    "Maintenance activation must return exactly one card to Active");
            verify(fixture.admiral().getRoster().getActiveCards().stream()
                            .anyMatch(card -> card.getId().equals(original.getId())),
                    "Returned card must retain its runtime identity in the Admiral Roster");
            frame.setSize(800, 220);
            frame.validate();
            for (JList<?> list : lists) {
                exerciseWheel(list, "Reusable Roster");
            }
            selectTab(tabs, "One-Time Ships");
            frame.validate();
            JList<?> oneTime = child((Container) tabs.getSelectedComponent(), JList.class);
            verify(oneTime.getModel().getSize() == 3, "One-Time view must retain all three independent copies");
            RosterCard firstCopy = (RosterCard) oneTime.getModel().getElementAt(1);
            RosterCard secondCopy = (RosterCard) oneTime.getModel().getElementAt(2);
            verify(firstCopy.getShip() == secondCopy.getShip() && !firstCopy.getId().equals(secondCopy.getId()),
                    "Duplicate One-Time Ship copies must have distinct card identities");
            exerciseWheel(oneTime, "One-Time Roster");
            selectTab(tabs, "Starship Traits");
            frame.validate();
            JList<?> rosterTraits = child((Container) tabs.getSelectedComponent(), JList.class);
            verify(rosterTraits.getModel().getSize() == 1
                            && ((RosterCard) rosterTraits.getModel().getElementAt(0)).getShip()
                            == ship(fixture.gameData(), "U.S.S. Enterprise"),
                    "Roster Traits must contain the trait-bearing reusable Ship only");
            exerciseWheel(rosterTraits, "Roster Starship Traits");
            frame.setSize(1100, 760);
            frame.validate();
            verify(rosterTraits.getModel().getSize() == 1, "Roster trait resize must retain content");
            System.out.println("PASS passive Roster: Active/Maintenance double-click round trip, all three lists wheel, Roster Traits scrollbar/resize");
        } finally {
            workspace.dispose();
            frame.dispose();
        }
        TraitViewer traits = new TraitViewer();
        try {
            traits.setVisible(true);
            traits.setSize(640, 100);
            traits.validate();
            JList<?> list = child(traits, JList.class);
            verify(list.getModel().getSize() == 1
                            && list.getModel().getElementAt(0) == ship(fixture.gameData(), "U.S.S. Enterprise"),
                    "GameData Traits must contain the canonical trait-bearing Ship only");
            exerciseWheel(list, "GameData Starship Traits");
            traits.setSize(900, 600);
            traits.validate();
            verify(list.getModel().getSize() == 1, "GameData trait resize must retain content");
            System.out.println("PASS GameData Traits: visible canonical content, native resize and scrollbar movement");
        } finally {
            traits.dispose();
        }
    }

    /** Selects an established workspace tab and validates its visible component tree. */
    private static void selectTab(JTabbedPane tabs, String title) {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (title.equals(tabs.getTitleAt(index))) {
                tabs.setSelectedIndex(index);
                tabs.validate();
                return;
            }
        }
        throw new AssertionError("Missing workspace tab: " + title);
    }

    /** Dispatches a primary-button double click inside one actual visible list cell. */
    private static void doubleClick(JList<?> list, int index) {
        list.ensureIndexIsVisible(index);
        Rectangle cell = list.getCellBounds(index, index);
        verify(cell != null && list.isShowing(), "Double-click target must be a visible list cell");
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                0, cell.x + 5, cell.y + 5, 2, false, MouseEvent.BUTTON1));
    }

    /**
     * Dispatches wheel input through the production scroll pane and checks exact
     * model retention. A single trait row can have no next wheel unit, so those
     * presentations additionally exercise their scrollbar to move the viewport.
     *
     * @param list visible list constrained by its native frame size
     * @param presentation diagnostic name for the current passive presentation
     */
    private static void exerciseWheel(JList<?> list, String presentation) {
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, list);
        verify(scroll != null && list.isShowing(), presentation + " must be visible in a scroll pane");
        scroll.validate();
        List<?> entries = java.util.stream.IntStream.range(0, list.getModel().getSize())
                .mapToObj(list.getModel()::getElementAt).toList();
        JScrollBar vertical = scroll.getVerticalScrollBar();
        verify(vertical.getMaximum() > vertical.getVisibleAmount(),
                presentation + " must have scrollable overflow at the constrained size");
        vertical.setValue(0);
        scroll.dispatchEvent(new MouseWheelEvent(scroll, MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), 0, 10, 10, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
        if (vertical.getValue() == 0 && presentation.contains("Traits")) {
            // A single oversized trait cell has no next row for unit-wheel movement.
            vertical.setValue(vertical.getMaximum() - vertical.getVisibleAmount());
            System.out.println("INFO " + presentation + ": single-row wheel left viewport unchanged; scrollbar exercised");
        }
        verify(vertical.getValue() > 0, presentation + " scroll control must move the viewport");
        verify(list.getModel().getSize() == entries.size(), presentation + " scrolling must retain row count");
        for (int index = 0; index < entries.size(); index++) {
            verify(list.getModel().getElementAt(index) == entries.get(index),
                    presentation + " scrolling must retain exact row identities and order");
        }
    }

    /**
     * Opens a production modal selection path and drives its actual controls.
     * The Swing timer runs inside the modal secondary event loop; failures close
     * the window before returning so verification cannot strand a modal dialog.
     *
     * @param fixture isolated selection candidates
     * @param views current named production factory
     * @param path named selection path
     * @param outcome acceptance, cancellation, or window-close action
     */
    private static void exerciseDialog(Fixture fixture, ShipFilterViews views, String path, String outcome) {
        String title = "Ship Filter interaction - " + path + " - " + outcome;
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.ArrayList<Object> selected = new java.util.ArrayList<>();
        Timer interaction = new Timer(150, event -> {
            JDialog dialog = java.util.Arrays.stream(Window.getWindows())
                    .filter(window -> window instanceof JDialog candidate
                            && candidate.isVisible() && title.equals(candidate.getTitle()))
                    .map(JDialog.class::cast).findFirst().orElse(null);
            if (dialog == null) {
                return;
            }
            ((Timer) event.getSource()).stop();
            try {
                exerciseFilters(dialog);
                JList<?> list = child(dialog, JList.class);
                verify(list.getModel().getSize() > 0, "Dialog must offer fixture candidates");
                list.setSelectionInterval(0, Math.min(1, list.getModel().getSize() - 1));
                selected.addAll(list.getSelectedValuesList());
                if (outcome.equals("close")) {
                    dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
                } else {
                    button(dialog, outcome.equals("accept") ? LabelOkay : LabelCancel).doClick();
                }
            } catch (Throwable cause) {
                failure.set(cause);
                dialog.dispose();
            }
        });
        interaction.start();
        List<?> result;
        try {
            result = switch (path) {
                case "reusable" -> views.chooseReusableShips(null, PlayerFaction.RomulanFed,
                        fixture.gameData().ships(), title);
                case "one-time" -> views.chooseOneTimeShips(null, PlayerFaction.RomulanFed,
                        fixture.gameData().ships(), title);
                case "roster-card" -> views.chooseRosterCards(null,
                        fixture.admiral().getRoster().getReusableCards(), title);
                default -> throw new IllegalArgumentException(path);
            };
        } finally {
            interaction.stop();
        }
        if (failure.get() != null) {
            throw new AssertionError("Native dialog interaction failed: " + title, failure.get());
        }
        verify(outcome.equals("accept") ? result.size() == selected.size() : result.isEmpty(),
                "Only explicit acceptance may return selected entries");
        for (int index = 0; index < result.size(); index++) {
            verify(result.get(index) == selected.get(index), "Accepted entries must retain exact visible identities");
        }
        System.out.println("PASS " + path + " " + outcome + ": filter controls and exact visible selection outcome");
    }

    /**
     * Toggles all 21 filter controls twice and verifies each restores the exact
     * visible projection, then clears all dimensions to check an empty result.
     *
     * @param root native surface containing one filter and list
     */
    private static void exerciseFilters(Container root) {
        expandFilter(root);
        List<JCheckBox> controls = children(root, JCheckBox.class);
        verify(controls.size() == 21, "Expected all four filter dimensions and 21 controls");
        JList<?> list = child(root, JList.class);
        List<?> original = java.util.stream.IntStream.range(0, list.getModel().getSize())
                .mapToObj(list.getModel()::getElementAt).toList();
        for (JCheckBox control : controls) {
            control.doClick();
            control.doClick();
            verify(list.getModel().getSize() == original.size(), "Filter round trip must retain row count");
            for (int index = 0; index < original.size(); index++) {
                verify(list.getModel().getElementAt(index) == original.get(index),
                        "Filter round trip must retain exact visible row identities and order");
            }
        }
        List<JCheckBox> selectedControls = controls.stream().filter(JCheckBox::isSelected).toList();
        selectedControls.forEach(AbstractButton::doClick);
        verify(list.getModel().getSize() == 0, "Empty filter dimensions must hide every row");
        selectedControls.forEach(AbstractButton::doClick);
        verify(list.getModel().getSize() == original.size(), "Restored controls must restore rows");
    }

    /** Finds an actual visible action control by its established label. */
    private static AbstractButton button(Container root, String label) {
        return children(root, AbstractButton.class).stream()
                .filter(candidate -> label.equals(candidate.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing control: " + label));
    }

    /** Fails native smoke verification independently of the JVM assertions flag. */
    private static void verify(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
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
