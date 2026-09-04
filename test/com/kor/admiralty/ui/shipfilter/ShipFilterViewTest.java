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
package com.kor.admiralty.ui.shipfilter;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.ui.ShipDetailsPanel;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the named Swing Ship Filter presentation through observable
 * component behavior rather than its internal projection machinery.
 */
class ShipFilterViewTest {

    /**
     * Runs one reusable selection through a deterministic modal option adapter.
     *
     * @param option     Swing option result to return
     * @param select     whether to select the first and third visible entries
     * @param candidates reusable Ship candidates
     * @return named dialog path result
     * @throws Exception if event-thread dispatch fails
     */
    private static List<Ship> chooseForOption(int option, boolean select, List<Ship> candidates) throws Exception {
        AtomicReference<List<Ship>> outcome = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ShipFilterViews views = new ShipFilterViews(testIconRenderer(), (owner, content, title) -> {
                assertEquals("Choose Ships", title);
                if (select) {
                    shipList(content).setSelectedIndices(new int[]{2, 0});
                }
                return option;
            });
            outcome.set(views.chooseReusableShips(
                    null,
                    PlayerFaction.Federation,
                    candidates,
                    "Choose Ships"));
        });
        return outcome.get();
    }

    /**
     * Supplies one independently observable case for all five faction, three
     * role, seven tier, and six rarity controls.
     *
     * @return all 21 established filter controls
     */
    private static Stream<ControlCase> filterControls() {
        return Stream.of(
                new ControlCase(
                        ShipFaction.Federation.toString(),
                        ship("Federation", ShipFaction.Federation),
                        true),
                new ControlCase(
                        ShipFaction.Klingon.toString(),
                        ship("Klingon", ShipFaction.Klingon),
                        false),
                new ControlCase(
                        ShipFaction.Romulan.toString(),
                        ship("Romulan", ShipFaction.Romulan),
                        true),
                new ControlCase(
                        ShipFaction.JemHadar.toString(),
                        ship("JemHadar", ShipFaction.JemHadar),
                        true),
                new ControlCase(
                        ShipFaction.Universal.toString(),
                        ship("Universal", ShipFaction.Universal),
                        true),
                new ControlCase(
                        LabelEngineering,
                        ship("Engineering", ShipFaction.Universal, Role.Eng, Tier.Tier6, Rarity.Epic),
                        true),
                new ControlCase(
                        LabelTactical,
                        ship("Tactical", ShipFaction.Universal, Role.Tac, Tier.Tier6, Rarity.Epic),
                        true),
                new ControlCase(
                        LabelScience,
                        ship("Science", ShipFaction.Universal, Role.Sci, Tier.Tier6, Rarity.Epic),
                        true),
                new ControlCase(
                        Tier.SmallCraft.toString(),
                        ship("Small Craft", ShipFaction.Universal, Role.Smc, Tier.SmallCraft, Rarity.Epic),
                        true),
                new ControlCase(
                        Tier.Tier1.toString(),
                        ship("Tier 1", ShipFaction.Universal, Role.Tac, Tier.Tier1, Rarity.Epic),
                        true),
                new ControlCase(
                        Tier.Tier2.toString(),
                        ship("Tier 2", ShipFaction.Universal, Role.Tac, Tier.Tier2, Rarity.Epic),
                        true),
                new ControlCase(
                        Tier.Tier3.toString(),
                        ship("Tier 3", ShipFaction.Universal, Role.Tac, Tier.Tier3, Rarity.Epic),
                        true),
                new ControlCase(
                        Tier.Tier4.toString(),
                        ship("Tier 4", ShipFaction.Universal, Role.Tac, Tier.Tier4, Rarity.Epic),
                        true),
                new ControlCase(
                        Tier.Tier5.toString(),
                        ship("Tier 5", ShipFaction.Universal, Role.Tac, Tier.Tier5, Rarity.Epic),
                        true),
                new ControlCase(
                        Tier.Tier6.toString(),
                        ship("Tier 6", ShipFaction.Universal, Role.Tac, Tier.Tier6, Rarity.Epic),
                        true),
                new ControlCase(
                        Rarity.Common.toString(),
                        ship("Common", ShipFaction.Universal, Role.Tac, Tier.Tier6, Rarity.Common),
                        true),
                new ControlCase(
                        Rarity.Uncommon.toString(),
                        ship("Uncommon", ShipFaction.Universal, Role.Tac, Tier.Tier6, Rarity.Uncommon),
                        true),
                new ControlCase(
                        Rarity.Rare.toString(),
                        ship("Rare", ShipFaction.Universal, Role.Tac, Tier.Tier6, Rarity.Rare),
                        true),
                new ControlCase(
                        Rarity.VeryRare.toString(),
                        ship("Very Rare", ShipFaction.Universal, Role.Tac, Tier.Tier6, Rarity.VeryRare),
                        true),
                new ControlCase(
                        Rarity.UltraRare.toString(),
                        ship("Ultra Rare", ShipFaction.Universal, Role.Tac, Tier.Tier6, Rarity.UltraRare),
                        true),
                new ControlCase(
                        Rarity.Epic.toString(),
                        ship("Epic", ShipFaction.Universal, Role.Tac, Tier.Tier6, Rarity.Epic),
                        true));
    }

    /**
     * Creates one canonical Ship with fixed non-faction filter facts.
     *
     * @param name    canonical Ship name
     * @param faction faction filter value
     * @return deterministic test Ship
     */
    private static Ship ship(String name, ShipFaction faction) {
        return ship(name, faction, Role.Tac, Tier.Tier6, Rarity.Epic);
    }

    /**
     * Creates one canonical Ship with explicit filter-dimension facts.
     *
     * @param name    canonical Ship name
     * @param faction faction filter value
     * @param role    role filter value
     * @param tier    tier filter value
     * @param rarity  rarity filter value
     * @return deterministic test Ship
     */
    private static Ship ship(String name, ShipFaction faction, Role role, Tier tier, Rarity rarity) {
        return new ShipImpl(
                faction,
                tier,
                rarity,
                role,
                name,
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "");
    }

    /**
     * Creates deterministic in-memory artwork without Icon Cache state.
     *
     * @return isolated Ship artwork adapter
     */
    private static ShipIconFactory testIconRenderer() {
        ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        return (iconName, faction, role, rarity, owned) -> icon;
    }

    /**
     * Reads visible canonical names from the presentation's Swing list model.
     *
     * @param root named Ship Filter presentation
     * @return visible names in presentation order
     */
    private static List<String> visibleNames(Container root) {
        JList<?> list = child(root, JList.class);
        return IntStream.range(0, list.getModel().getSize())
                .mapToObj(index -> ((Ship) list.getModel().getElementAt(index)).getName())
                .toList();
    }

    /**
     * Returns the Ship list exposed by the named reusable-Ship presentation.
     *
     * @param root reusable-Ship view
     * @return typed observable Ship list
     */
    @SuppressWarnings("unchecked")
    private static JList<Ship> shipList(Container root) {
        return (JList<Ship>) (JList<?>) child(root, JList.class);
    }

    /**
     * Creates a listener that counts every list-data event in one shared slot.
     *
     * @param events one-element event counter
     * @return listener counting every event kind
     */
    private static ListDataListener countEvents(int[] events) {
        return new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent event) {
                events[0]++;
            }

            @Override
            public void intervalRemoved(ListDataEvent event) {
                events[0]++;
            }

            @Override
            public void contentsChanged(ListDataEvent event) {
                events[0]++;
            }
        };
    }

    /**
     * Creates a listener that snapshots the selection visible to observers at
     * each completed projection event.
     *
     * @param view               named Ship Filter presentation
     * @param observedSelections destination for event-time selection snapshots
     * @return listener recording every event kind
     */
    private static ListDataListener observeSelections(
            ShipFilterView<Ship, ShipSortOrder> view,
            List<List<Ship>> observedSelections) {
        return new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent event) {
                observedSelections.add(view.selectedEntries());
            }

            @Override
            public void intervalRemoved(ListDataEvent event) {
                observedSelections.add(view.selectedEntries());
            }

            @Override
            public void contentsChanged(ListDataEvent event) {
                observedSelections.add(view.selectedEntries());
            }
        };
    }

    /**
     * Finds one visible filter control by its established label.
     *
     * @param root presentation subtree
     * @param text exact checkbox label
     * @return matching filter control
     * @throws NoSuchElementException if the control is absent
     */
    private static JCheckBox checkBox(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox checkBox && text.equals(checkBox.getText())) {
                return checkBox;
            }
            if (component instanceof Container container) {
                try {
                    return checkBox(container, text);
                } catch (NoSuchElementException ignored) {
                    // Continue through sibling containers until the requested control is found.
                }
            }
        }
        throw new NoSuchElementException(text);
    }

    /**
     * Reports whether a component subtree contains one exact visible label.
     *
     * @param root         component subtree
     * @param expectedText exact label text
     * @return whether a matching label is present
     */
    private static boolean hasLabel(Component root, String expectedText) {
        if (root instanceof javax.swing.JLabel label && expectedText.equals(label.getText())) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component component : container.getComponents()) {
                if (hasLabel(component, expectedText)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds the first descendant of one component type.
     *
     * @param root          component subtree
     * @param componentType requested component type
     * @param <T>           component type
     * @return first matching descendant
     * @throws NoSuchElementException if no descendant matches
     */
    private static <T extends Component> T child(Container root, Class<T> componentType) {
        for (Component component : root.getComponents()) {
            if (componentType.isInstance(component)) {
                return componentType.cast(component);
            }
            if (component instanceof Container container) {
                try {
                    return child(container, componentType);
                } catch (NoSuchElementException ignored) {
                    // Continue through sibling containers until the requested child is found.
                }
            }
        }
        throw new NoSuchElementException(componentType.getName());
    }

    /**
     * Verifies reusable Ship selection installs the complete Admiral profile
     * before candidates become visible and uses canonical presentation order.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void reusableSelectionUsesAdmiralProfileInCanonicalOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Ship federation = ship("Federation", ShipFaction.Federation);
            Ship klingon = ship("Klingon", ShipFaction.Klingon);
            Ship romulan = ship("Romulan", ShipFaction.Romulan);
            Ship jemHadar = ship("JemHadar", ShipFaction.JemHadar);
            Ship universal = ship("Universal", ShipFaction.Universal);
            Ship historical = ship("Historical", ShipFaction.None);

            ShipFilterView<Ship, ShipSortOrder> view = new ShipFilterViews(testIconRenderer())
                    .reusableShipSelection(
                            PlayerFaction.RomulanFed,
                            List.of(universal, klingon, romulan, historical, jemHadar, federation));

            assertEquals(
                    List.of("Federation", "Historical", "JemHadar", "Romulan", "Universal"),
                    visibleNames(view));
        });
    }

    /**
     * Verifies every established control publishes exactly one completed
     * projection when it changes one dimension of the immutable Ship Filter.
     *
     * @param controlCase control and canonical Ship fact exercised by the case
     * @throws Exception if event-thread dispatch fails
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("filterControls")
    void eachControlPublishesOneFinalProjection(ControlCase controlCase) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShipFilterView<Ship, ShipSortOrder> view = new ShipFilterViews(testIconRenderer())
                    .reusableShipSelection(PlayerFaction.RomulanFed, List.of(controlCase.candidate()));
            JCheckBox control = checkBox(view, controlCase.label());
            JList<Ship> list = shipList(view);
            List<List<String>> observedProjections = new ArrayList<List<String>>();
            list.getModel().addListDataListener(new ListDataListener() {
                @Override
                public void intervalAdded(ListDataEvent event) {
                    observedProjections.add(visibleNames(view));
                }

                @Override
                public void intervalRemoved(ListDataEvent event) {
                    observedProjections.add(visibleNames(view));
                }

                @Override
                public void contentsChanged(ListDataEvent event) {
                    observedProjections.add(visibleNames(view));
                }
            });

            assertEquals(controlCase.initiallyVisible(), control.isSelected());
            assertEquals(
                    controlCase.initiallyVisible() ? List.of(controlCase.candidate().getName()) : List.of(),
                    visibleNames(view));

            control.doClick();

            List<String> expected = controlCase.initiallyVisible()
                    ? List.of()
                    : List.of(controlCase.candidate().getName());
            assertEquals(List.of(expected), observedProjections);
            assertEquals(expected, visibleNames(view));
        });
    }

    /**
     * Verifies entry replacement retains the current filter and exact selected
     * identities while rejecting raw-index selection transfer.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void replacementRetainsFilterAndSelectionByExactIdentity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Ship alpha = ship("Alpha", ShipFaction.Universal);
            Ship retained = ship("Beta", ShipFaction.Universal);
            Ship hidden = ship("Federation", ShipFaction.Federation);
            Ship replacementAtSelectedIndex = ship("Zulu", ShipFaction.Universal);
            ShipFilterView<Ship, ShipSortOrder> view = new ShipFilterViews(testIconRenderer())
                    .reusableShipSelection(PlayerFaction.RomulanFed, List.of(retained, alpha));
            JList<?> list = child(view, JList.class);
            list.setSelectedIndex(1);
            checkBox(view, ShipFaction.Federation.toString()).doClick();
            int[] events = new int[1];
            list.getModel().addListDataListener(countEvents(events));
            List<List<Ship>> observedSelections = new ArrayList<List<Ship>>();
            list.getModel().addListDataListener(observeSelections(view, observedSelections));

            view.present(List.of(replacementAtSelectedIndex, retained, hidden));

            assertEquals(1, events[0]);
            assertEquals(1, observedSelections.size());
            assertEquals(1, observedSelections.getFirst().size());
            assertSame(retained, observedSelections.getFirst().getFirst());
            assertEquals(List.of("Beta", "Zulu"), visibleNames(view));
            assertEquals(1, view.selectedEntries().size());
            assertSame(retained, view.selectedEntries().getFirst());
            assertEquals(0, list.getSelectedIndex());

            view.present(List.of(replacementAtSelectedIndex, hidden));

            assertEquals(2, events[0]);
            assertEquals(List.of(), observedSelections.get(1));
            assertEquals(List.of("Zulu"), visibleNames(view));
            assertEquals(List.of(), view.selectedEntries());

            view.present(List.of(hidden));

            assertEquals(3, events[0]);
            assertEquals(List.of(), observedSelections.get(2));
            assertEquals(List.of(), visibleNames(view));
        });
    }

    /**
     * Verifies reusable selection retains the established two-column artwork and
     * details presentation while returning an immutable visible-order selection.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void selectionRetainsLayoutArtworkDetailsAndImmutableVisibleOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Ship alpha = ship("Alpha", ShipFaction.Universal);
            Ship beta = ship("Beta", ShipFaction.Universal);
            Ship gamma = ship("Gamma", ShipFaction.Universal);
            AtomicReference<Boolean> rosterArtwork = new AtomicReference<Boolean>();
            ShipIconFactory iconRenderer = (iconName, faction, role, rarity, owned) -> {
                rosterArtwork.set(owned);
                return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
            };
            ShipFilterView<Ship, ShipSortOrder> view = new ShipFilterViews(iconRenderer)
                    .reusableShipSelection(PlayerFaction.Federation, List.of(gamma, alpha, beta));
            JList<Ship> list = shipList(view);
            ShipDetailsPanel details = child(view, ShipDetailsPanel.class);

            assertInstanceOf(GridBagLayout.class, view.getLayout());
            assertEquals(2, view.getComponentCount());
            assertInstanceOf(JScrollPane.class, child(view, JScrollPane.class));
            assertInstanceOf(ShipDetailsPanel.class, details);
            assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, list.getSelectionMode());

            list.getCellRenderer().getListCellRendererComponent(list, alpha, 0, false, false);
            assertEquals(Boolean.FALSE, rosterArtwork.get());

            list.setSelectedIndex(1);
            assertSame(beta, list.getSelectedValue());
            assertTrue(hasLabel(details, "Beta"));
            list.setSelectedIndices(new int[]{2, 0});

            assertEquals(List.of(alpha, gamma), view.selectedEntries());
            assertThrows(UnsupportedOperationException.class, () -> view.selectedEntries().add(beta));

            checkBox(view, ShipFaction.Universal.toString()).doClick();

            assertEquals(List.of(), view.selectedEntries());
            assertFalse(hasLabel(details, "Alpha"));
            assertFalse(hasLabel(details, "Beta"));
            assertFalse(hasLabel(details, "Gamma"));
        });
    }

    /**
     * Verifies Swing view construction and every public mutation fail near their
     * cause when invoked outside the event-dispatch thread.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void constructionAndMutationRequireTheEventDispatchThread() throws Exception {
        Ship candidate = ship("Candidate", ShipFaction.Universal);
        ShipFilterViews views = new ShipFilterViews(testIconRenderer());

        assertThrows(
                IllegalStateException.class,
                () -> views.reusableShipSelection(PlayerFaction.Federation, List.of(candidate)));

        AtomicReference<ShipFilterView<Ship, ShipSortOrder>> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(
                views.reusableShipSelection(PlayerFaction.Federation, List.of(candidate))));

        ShipFilterView<Ship, ShipSortOrder> view = reference.get();
        assertThrows(IllegalStateException.class, () -> view.present(List.of(candidate)));
        assertThrows(IllegalStateException.class, () -> view.orderBy(ShipSortOrder.Default));
    }

    /**
     * Verifies invalid replacements and ordering are transactional: prior
     * projection, exact selection, details, and publication count remain intact.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void invalidUpdatesLeavePriorProjectionAndSelectionIntact() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Ship alpha = ship("Alpha", ShipFaction.Universal);
            Ship beta = ship("Beta", ShipFaction.Universal);
            Ship invalid = ship(null, ShipFaction.Universal);
            ShipFilterView<Ship, ShipSortOrder> view = new ShipFilterViews(testIconRenderer())
                    .reusableShipSelection(PlayerFaction.Federation, List.of(alpha, beta));
            JList<Ship> list = shipList(view);
            ShipDetailsPanel details = child(view, ShipDetailsPanel.class);
            list.setSelectedIndex(1);
            int[] events = new int[1];
            list.getModel().addListDataListener(countEvents(events));

            assertThrows(NullPointerException.class, () -> view.present(null));
            assertThrows(NullPointerException.class, () -> view.present(List.of(alpha, invalid)));
            assertThrows(NullPointerException.class, () -> view.orderBy(null));

            assertEquals(0, events[0]);
            assertEquals(List.of("Alpha", "Beta"), visibleNames(view));
            assertEquals(1, view.selectedEntries().size());
            assertSame(beta, view.selectedEntries().getFirst());
            assertTrue(hasLabel(details, "Beta"));
        });
    }

    /**
     * Verifies the named reusable dialog path translates every modal outcome and
     * never exposes a mutable selected-entry list.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void reusableDialogReturnsImmutableVisibleOrderOnlyForAcceptance() throws Exception {
        Ship alpha = ship("Alpha", ShipFaction.Universal);
        Ship beta = ship("Beta", ShipFaction.Universal);
        Ship gamma = ship("Gamma", ShipFaction.Universal);
        List<Ship> candidates = List.of(gamma, alpha, beta);

        List<Ship> accepted = chooseForOption(JOptionPane.OK_OPTION, true, candidates);
        List<Ship> cancelled = chooseForOption(JOptionPane.CANCEL_OPTION, true, candidates);
        List<Ship> closed = chooseForOption(JOptionPane.CLOSED_OPTION, true, candidates);
        List<Ship> emptyAcceptance = chooseForOption(JOptionPane.OK_OPTION, false, candidates);

        assertEquals(2, accepted.size());
        assertSame(alpha, accepted.get(0));
        assertSame(gamma, accepted.get(1));
        assertEquals(List.of(), cancelled);
        assertEquals(List.of(), closed);
        assertEquals(List.of(), emptyAcceptance);
        assertThrows(UnsupportedOperationException.class, () -> accepted.add(beta));
        assertThrows(UnsupportedOperationException.class, () -> cancelled.add(beta));
        assertThrows(UnsupportedOperationException.class, () -> closed.add(beta));
        assertThrows(UnsupportedOperationException.class, () -> emptyAcceptance.add(beta));
    }

    /**
     * Verifies complete filter and entry updates still publish one final-state
     * event when both the prior and resulting projections are empty.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void emptyProjectionStillPublishesOneEventPerCompleteUpdate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShipFilterView<Ship, ShipSortOrder> view = new ShipFilterViews(testIconRenderer())
                    .reusableShipSelection(PlayerFaction.Federation, List.of());
            JList<?> list = child(view, JList.class);
            int[] events = new int[1];
            list.getModel().addListDataListener(countEvents(events));

            checkBox(view, ShipFaction.Federation.toString()).doClick();
            view.present(List.of());

            assertEquals(2, events[0]);
            assertEquals(List.of(), visibleNames(view));
        });
    }

    /**
     * One control label, its representative Ship, and initial Admiral-profile
     * visibility.
     *
     * @param label            established visible control label
     * @param candidate        Ship governed by that control
     * @param initiallyVisible whether the complete initial profile permits it
     */
    private record ControlCase(String label, Ship candidate, boolean initiallyVisible) {

        @Override
        public String toString() {
            return label;
        }
    }
}
