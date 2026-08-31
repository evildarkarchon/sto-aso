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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.ui;

import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.LabelOneTimeShips;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.TabAssignments;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.TabPrimary;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kor.admiralty.AppTestFixture;
import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.io.AdmiralsStore;
import com.kor.admiralty.io.AdmiralsStoreException;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.panels.AdmiralPanel;
import com.kor.admiralty.ui.resources.ShipIconFactory;

/** Specifies the headless host lifecycle around real Admiral workspaces. */
class AdmiralWorkspaceHostTest {

    @TempDir
    Path tempDir;

    /** Restores process-start application state after production-factory coverage. */
    @AfterEach
    void resetApp() {
        AppTestFixture.reset();
    }

    /**
     * Verifies startup creates one workspace root per Admiral and preserves the
     * established outer and inner tab order.
     *
     * @throws Exception if persistence initialization or event-thread dispatch fails
     */
    @Test
    void startupCreatesOneOrderedWorkspacePerAdmiral() throws Exception {
        HostScenario scenario = new HostScenario("First Admiral", "Second Admiral");
        SwingUtilities.invokeAndWait(() -> scenario.createHost());

        assertAll(
                () -> assertEquals(2, scenario.tabs.getTabCount()),
                () -> assertEquals("First Admiral", scenario.tabs.getTitleAt(0)),
                () -> assertEquals("Second Admiral", scenario.tabs.getTitleAt(1)),
                () -> assertInstanceOf(AdmiralPanel.class, scenario.tabs.getComponentAt(0)),
                () -> assertInstanceOf(AdmiralPanel.class, scenario.tabs.getComponentAt(1)),
                () -> assertNotSame(scenario.tabs.getComponentAt(0), scenario.tabs.getComponentAt(1)),
                () -> assertEquals(
                        List.of(TabPrimary, LabelOneTimeShips, TabAssignments, "Starship Traits"),
                        tabTitles(requireDescendant(scenario.tabs.getComponentAt(0), JTabbedPane.class))),
                () -> assertEquals(
                        List.of(TabPrimary, LabelOneTimeShips, TabAssignments, "Starship Traits"),
                        tabTitles(requireDescendant(scenario.tabs.getComponentAt(1), JTabbedPane.class))));
    }

    /**
     * Verifies AdmiraltyConsole's headless production factory crosses the same real
     * workspace seam as the isolated host lifecycle tests.
     *
     * @throws Exception if application initialization or event-thread dispatch fails
     */
    @Test
    void consoleProductionFactoryConstructsTheSoleWorkspaceHost() throws Exception {
        GameData gameData = GameData.builder().build();
        AppTestFixture.initialize(gameData);
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.LEFT);
        AtomicReference<AdmiralWorkspaceHost> hostReference = new AtomicReference<AdmiralWorkspaceHost>();

        SwingUtilities.invokeAndWait(
                () -> hostReference.set(AdmiraltyConsole.createWorkspaceHost(tabs)));

        assertAll(
                () -> assertInstanceOf(AdmiralWorkspaceHost.class, hostReference.get()),
                () -> assertEquals(1, tabs.getTabCount()),
                () -> assertInstanceOf(AdmiralPanel.class, tabs.getComponentAt(0)));
    }

    /**
     * Verifies adding creates one associated workspace root and one Admiral name
     * change updates only its outer title through one host notification.
     *
     * @throws Exception if persistence initialization or event-thread dispatch fails
     */
    @Test
    void addingAndRenamingAnAdmiralCreatesAndUpdatesExactlyOneOuterTab() throws Exception {
        HostScenario scenario = new HostScenario("New Admiral");
        AtomicReference<Admiral> addedReference = new AtomicReference<Admiral>();
        AtomicInteger titleNotifications = new AtomicInteger();

        SwingUtilities.invokeAndWait(() -> {
            AdmiralWorkspaceHost host = scenario.createHost();
            Admiral added = host.addAdmiral();
            addedReference.set(added);
            scenario.tabs.addPropertyChangeListener(
                    "indexForTitle",
                    event -> titleNotifications.incrementAndGet());
            added.setName("Renamed Admiral");
        });

        assertAll(
                () -> assertEquals(2, scenario.admirals.getAdmirals().size()),
                () -> assertEquals(2, scenario.tabs.getTabCount()),
                () -> assertEquals("New Admiral", scenario.tabs.getTitleAt(0)),
                () -> assertEquals("Renamed Admiral", scenario.tabs.getTitleAt(1)),
                () -> assertInstanceOf(AdmiralPanel.class, scenario.tabs.getComponentAt(1)),
                () -> assertEquals(1, titleNotifications.get()),
                () -> assertEquals("Renamed Admiral", addedReference.get().getName()));
    }

    /**
     * Verifies confirmation releases the selected real workspace, removes exactly
     * its Admiral and tab, and leaves the other association operational.
     *
     * @throws Exception if persistence initialization or event-thread dispatch fails
     */
    @Test
    void confirmedDeletionCleansUpOnlyTheSelectedAdmiralWorkspace() throws Exception {
        HostScenario scenario = new HostScenario("First Admiral", "Second Admiral");
        Admiral first = scenario.admiral(0);
        Admiral second = scenario.admiral(1);
        AtomicReference<Admiral> confirmedReference = new AtomicReference<Admiral>();
        AtomicReference<Component> firstWorkspaceReference = new AtomicReference<Component>();
        AtomicReference<Component> deletedWorkspaceReference = new AtomicReference<Component>();
        AtomicBoolean deleted = new AtomicBoolean();
        AtomicInteger titleNotifications = new AtomicInteger();

        SwingUtilities.invokeAndWait(() -> {
            AdmiralWorkspaceHost host = scenario.createHost(
                    admiral -> {
                        confirmedReference.set(admiral);
                        return true;
                    });
            firstWorkspaceReference.set(scenario.tabs.getComponentAt(0));
            scenario.tabs.setSelectedIndex(1);
            deletedWorkspaceReference.set(scenario.tabs.getSelectedComponent());
            deleted.set(host.deleteSelectedAdmiral());
            scenario.tabs.addPropertyChangeListener(
                    "indexForTitle",
                    event -> titleNotifications.incrementAndGet());
            second.setName("Detached Admiral");
            first.setName("First Renamed");
        });

        assertAll(
                () -> assertTrue(deleted.get()),
                () -> assertSame(second, confirmedReference.get()),
                () -> assertEquals(List.of(first), scenario.admirals.getAdmirals()),
                () -> assertEquals(1, scenario.tabs.getTabCount()),
                () -> assertSame(firstWorkspaceReference.get(), scenario.tabs.getComponentAt(0)),
                () -> assertFalse(deletedWorkspaceReference.get().isEnabled()),
                () -> assertEquals("First Renamed", scenario.tabs.getTitleAt(0)),
                () -> assertEquals(1, titleNotifications.get()));
    }

    /**
     * Verifies declining deletion leaves the selected Admiral, workspace, tab,
     * associations, and root/host listener behavior unchanged.
     *
     * @throws Exception if persistence initialization or event-thread dispatch fails
     */
    @Test
    void declinedDeletionLeavesTheSelectedAdmiralWorkspaceUnchanged() throws Exception {
        HostScenario scenario = new HostScenario("First Admiral", "Second Admiral");
        Admiral first = scenario.admiral(0);
        Admiral second = scenario.admiral(1);
        AtomicReference<Admiral> declinedReference = new AtomicReference<Admiral>();
        AtomicReference<Component> selectedWorkspaceReference = new AtomicReference<Component>();
        AtomicBoolean deleted = new AtomicBoolean(true);
        AtomicInteger titleNotifications = new AtomicInteger();

        SwingUtilities.invokeAndWait(() -> {
            AdmiralWorkspaceHost host = scenario.createHost(
                    admiral -> {
                        declinedReference.set(admiral);
                        return false;
                    });
            scenario.tabs.setSelectedIndex(1);
            selectedWorkspaceReference.set(scenario.tabs.getSelectedComponent());
            deleted.set(host.deleteSelectedAdmiral());
            scenario.tabs.addPropertyChangeListener(
                    "indexForTitle",
                    event -> titleNotifications.incrementAndGet());
            second.setName("Second Renamed");
        });

        assertAll(
                () -> assertFalse(deleted.get()),
                () -> assertSame(second, declinedReference.get()),
                () -> assertEquals(List.of(first, second), scenario.admirals.getAdmirals()),
                () -> assertEquals(2, scenario.tabs.getTabCount()),
                () -> assertSame(selectedWorkspaceReference.get(), scenario.tabs.getComponentAt(1)),
                () -> assertTrue(selectedWorkspaceReference.get().isEnabled()),
                () -> assertEquals("Second Renamed", scenario.tabs.getTitleAt(1)),
                () -> assertEquals(
                        "Second Renamed",
                        requireDescendant(selectedWorkspaceReference.get(), JTextField.class).getText()),
                () -> assertEquals(1, titleNotifications.get()));
    }

    /** Holds one isolated set of real host dependencies for a lifecycle scenario. */
    private final class HostScenario {

        private final GameData gameData;
        private final Admirals admirals;
        private final AdmiralsStore admiralsStore;
        private final JTabbedPane tabs;

        /** Creates named Admirals and their isolated production host dependencies. */
        private HostScenario(String... names) throws AdmiralsStoreException {
            gameData = GameData.builder().build();
            List<Admiral> restoredAdmirals = new ArrayList<Admiral>();
            for (String name : names) {
                Admiral admiral = new Admiral(gameData);
                admiral.setName(name);
                restoredAdmirals.add(admiral);
            }
            admirals = Admirals.restore(gameData, restoredAdmirals);
            admiralsStore = new AdmiralsStore();
            tabs = new JTabbedPane(JTabbedPane.LEFT);
        }

        /** Returns one scenario Admiral in its stable application order. */
        private Admiral admiral(int index) {
            return admirals.getAdmirals().get(index);
        }

        /** Creates the real host using the production confirmation boundary. */
        private AdmiralWorkspaceHost createHost() {
            return new AdmiralWorkspaceHost(
                    tabs,
                    admirals,
                    gameData,
                    admiralsStore,
                    tempDir,
                    testIconRenderer());
        }

        /** Creates the real host using one deterministic deletion confirmation. */
        private AdmiralWorkspaceHost createHost(
                AdmiralWorkspaceHost.DeletionConfirmation deletionConfirmation) {
            return new AdmiralWorkspaceHost(
                    tabs,
                    admirals,
                    gameData,
                    admiralsStore,
                    tempDir,
                    testIconRenderer(),
                    deletionConfirmation);
        }
    }

    /** Returns deterministic in-memory artwork at the workspace icon boundary. */
    private static ShipIconFactory testIconRenderer() {
        ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        return (iconName, faction, role, rarity, owned) -> icon;
    }

    /** Returns the exact displayed titles from one tabbed pane. */
    private static List<String> tabTitles(JTabbedPane tabs) {
        return java.util.stream.IntStream.range(0, tabs.getTabCount())
                .mapToObj(tabs::getTitleAt)
                .toList();
    }

    /** Requires the first descendant assignable to the requested Swing type. */
    private static <T extends Component> T requireDescendant(Component root, Class<T> type) {
        T match = findDescendant(root, type);
        if (match != null) {
            return match;
        }
        throw new AssertionError("Missing child component: " + type.getSimpleName());
    }

    /** Searches one descendant branch without failing when no matching child exists. */
    private static <T extends Component> T findDescendant(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component component : container.getComponents()) {
                T match = findDescendant(component, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
