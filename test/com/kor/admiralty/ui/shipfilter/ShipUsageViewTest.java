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
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.ui.ShipDetailsPanel;
import org.jdesktop.swingx.JXTaskPane;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.LabelEngineering;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the named usage presentation through its public Swing surface. */
class ShipUsageViewTest {

    /**
     * Most Used applies to the initial rows; each usage order resolves ties by
     * tier, rarity, role, and name while retaining equal rows in input order.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void allUsageOrdersPreserveCanonicalTieBreakersAndExactRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShipUsageRow tier = row("Tier", Tier.Tier1, Rarity.Epic, Role.Tac, 5);
            ShipUsageRow rarity = row("Rarity", Tier.Tier6, Rarity.Common, Role.Tac, 5);
            ShipUsageRow eng = row("Eng", Tier.Tier6, Rarity.Epic, Role.Eng, 5);
            ShipUsageRow equalEng = new ShipUsageRow(eng.ship(), 5, false);
            ShipUsageRow sci = row("Sci", Tier.Tier6, Rarity.Epic, Role.Sci, 5);
            ShipUsageRow alpha = row("Alpha", Tier.Tier6, Rarity.Epic, Role.Tac, 5);
            ShipUsageRow beta = row("Beta", Tier.Tier6, Rarity.Epic, Role.Tac, 5);
            ShipUsageRow highest = row("Highest", Tier.Tier6, Rarity.Epic, Role.Tac, 9);
            ShipUsageRow zero = row("Zero", Tier.Tier6, Rarity.Epic, Role.Tac, 0);
            ShipFilterView<ShipUsageRow, ShipUsageSortOrder> view = views().shipUsage(
                    List.of(highest, zero, beta, equalEng, sci, eng, alpha, rarity, tier));
            JList<?> list = list(view);
            assertEquals(List.of(highest, tier, rarity, equalEng, eng, sci, alpha, beta, zero), rows(list));
            assertSame(equalEng, list.getModel().getElementAt(3));
            assertSame(eng, list.getModel().getElementAt(4));

            view.orderBy(ShipUsageSortOrder.Default);
            assertEquals(List.of(tier, rarity, equalEng, eng, sci, alpha, beta, highest, zero), rows(list));
            view.orderBy(ShipUsageSortOrder.LeastUsed);
            assertEquals(List.of(zero, tier, rarity, equalEng, eng, sci, alpha, beta, highest), rows(list));
            view.orderBy(ShipUsageSortOrder.MostUsed);
            assertEquals(List.of(highest, tier, rarity, equalEng, eng, sci, alpha, beta, zero), rows(list));
        });
    }

    /**
     * Usage rows use the shared four dimensions and retain the active filter and
     * order across atomic source replacement, including empty projections.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void controlsAndReplacementPublishOnlyCompleteFilteredUsageRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShipUsageRow retained = row("Retained", Tier.Tier6, Rarity.Epic, Role.Eng, 3);
            ShipUsageRow replaced = row("Replaced", Tier.Tier6, Rarity.Epic, Role.Eng, 9);
            ShipFilterView<ShipUsageRow, ShipUsageSortOrder> view = views().shipUsage(List.of(retained, replaced));
            JList<?> list = list(view);
            List<List<?>> observed = new ArrayList<>();
            List<List<?>> selections = new ArrayList<>();
            observe(list, () -> {
                observed.add(rows(list));
                selections.add(List.copyOf(list.getSelectedValuesList()));
            });
            for (String dimension : List.of(ShipFaction.Federation.toString(), LabelEngineering,
                    Tier.Tier6.toString(), Rarity.Epic.toString())) {
                checkBox(view, dimension).doClick();
                assertEquals(List.of(), observed.getLast());
                checkBox(view, dimension).doClick();
                assertEquals(List.of(replaced, retained), observed.getLast());
            }
            assertEquals(8, observed.size());
            observed.clear();
            selections.clear();
            list.setSelectedIndex(1);
            view.orderBy(ShipUsageSortOrder.LeastUsed);
            assertEquals(List.of(List.of(retained, replaced)), observed);
            observed.clear();
            selections.clear();

            ShipUsageRow replacement = row("Replacement", Tier.Tier6, Rarity.Epic, Role.Eng, 1);
            ArrayList<ShipUsageRow> source = new ArrayList<>(List.of(retained, replacement));
            view.present(source);
            source.clear();
            assertEquals(List.of(List.of(replacement, retained)), observed);
            assertEquals(List.of(List.of(retained)), selections);
            assertSame(retained, list.getSelectedValue());
            assertEquals(3, retained.deploymentCount());

            checkBox(view, Rarity.Epic.toString()).doClick();
            observed.clear();
            view.present(List.of(replacement));
            assertEquals(List.of(List.of()), observed);
            assertTrue(list.isSelectionEmpty());
            observed.clear();
            assertThrows(NullPointerException.class, () -> view.present(java.util.Arrays.asList(replacement, null)));
            assertTrue(observed.isEmpty());
            checkBox(view, Rarity.Epic.toString()).doClick();
            assertEquals(List.of(replacement), rows(list));
        });
    }

    /**
     * The named usage path retains collapsed controls, a list without details,
     * canonical artwork ownership, and the row's immutable deployment count.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void usagePresentationRendersSnapshotCountsAndCurrentRosterArtwork() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Boolean> ownership = new ArrayList<>();
            ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
            ShipFilterViews views = new ShipFilterViews((name, faction, role, rarity, owned) -> {
                ownership.add(owned);
                return icon;
            });
            ShipUsageRow row = row("Historical", Tier.Tier6, Rarity.Epic, Role.Eng, 12);
            ShipUsageRow historical = new ShipUsageRow(row.ship(), 12, false);
            ShipFilterView<ShipUsageRow, ShipUsageSortOrder> view = views.shipUsage(List.of(historical));
            JList<?> list = list(view);
            assertEquals(21, components(view).filter(JCheckBox.class::isInstance).count());
            assertTrue(components(view).filter(JCheckBox.class::isInstance)
                    .map(JCheckBox.class::cast).allMatch(JCheckBox::isSelected));
            assertTrue(components(view).filter(JXTaskPane.class::isInstance)
                    .map(JXTaskPane.class::cast).findFirst().orElseThrow().isCollapsed());
            assertFalse(components(view).anyMatch(ShipDetailsPanel.class::isInstance));
            ownership.clear();
            Component rendered = render(list, historical);
            assertTrue(components(rendered).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                    .anyMatch(label -> "12".equals(label.getText())));
            assertEquals(List.of(false), ownership);
            ownership.clear();
            render(list, row);
            assertEquals(List.of(true), ownership);
        });
    }

    /**
     * Builds a canonical Ship and immutable row with independently chosen facts.
     *
     * @return usage row whose Ship is Federation-aligned and currently owned
     */
    private static ShipUsageRow row(String name, Tier tier, Rarity rarity, Role role, int count) {
        Ship ship = new ShipImpl(ShipFaction.Federation, tier, rarity, role, name,
                10, 20, 30, RuleType.All.rewardBonus(0), "");
        return new ShipUsageRow(ship, count, true);
    }

    /** Returns a named-view factory with deterministic in-memory artwork. */
    private static ShipFilterViews views() {
        ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        return new ShipFilterViews((name, faction, role, rarity, owned) -> icon);
    }

    /** Traverses the observable Swing component tree, including the root. */
    private static Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root instanceof Container container
                ? Stream.of(container.getComponents()).flatMap(ShipUsageViewTest::components) : Stream.empty());
    }

    /** Returns the presentation's visible entry list. */
    private static JList<?> list(Component view) {
        return components(view).filter(JList.class::isInstance).map(JList.class::cast).findFirst().orElseThrow();
    }

    /** Returns a filter checkbox by its established visible label. */
    private static JCheckBox checkBox(Component view, String label) {
        return components(view).filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast)
                .filter(control -> label.equals(control.getText())).findFirst().orElseThrow();
    }

    /** Captures the currently visible row identities in list order. */
    private static List<?> rows(JList<?> list) {
        List<Object> rows = new ArrayList<>();
        for (int index = 0; index < list.getModel().getSize(); index++) {
            rows.add(list.getModel().getElementAt(index));
        }
        return List.copyOf(rows);
    }

    /** Observes every list-model publication through the public Swing seam. */
    private static void observe(JList<?> list, Runnable observation) {
        list.getModel().addListDataListener(new ListDataListener() {
            /** Records any insertion publication through the public list model. */
            @Override
            public void intervalAdded(ListDataEvent event) {
                observation.run();
            }

            /** Records any removal publication through the public list model. */
            @Override
            public void intervalRemoved(ListDataEvent event) {
                observation.run();
            }

            /** Records the final projection published by a complete update. */
            @Override
            public void contentsChanged(ListDataEvent event) {
                observation.run();
            }
        });
    }

    /**
     * Invokes the usage renderer installed on the observed typed list. The cast
     * is confined to the component-tree seam, which erases the list's row type.
     */
    @SuppressWarnings("unchecked")
    private static Component render(JList<?> list, ShipUsageRow row) {
        JList<ShipUsageRow> usage = (JList<ShipUsageRow>) list;
        return usage.getCellRenderer().getListCellRendererComponent(usage, row, 0, false, false);
    }
}
