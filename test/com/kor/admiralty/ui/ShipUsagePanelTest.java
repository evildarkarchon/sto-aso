/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.io.GameData;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static com.kor.admiralty.ui.resources.Strings.ShipStatistics.*;

/** Consumer tests exercise the same controls installed in the usage window. */
class ShipUsagePanelTest {

    /** Verifies the initially selected Most Used control agrees with the first rows. */
    @Test
    void opensWithMostRelevantHistoryFirst() throws Exception {
        Admirals admirals = admirals();
        SwingUtilities.invokeAndWait(() -> {
            ShipUsagePanel panel = new ShipUsagePanel(admirals,
                    (name, faction, role, rarity, owned) -> new ImageIcon());
            assertEquals(List.of("U.S.S. Enterprise", "Class F Shuttle"), names(panel));
            assertEquals(63, rows(panel).getFirst().deploymentCount());
            assertTrue(button(panel, "Most Used").isSelected());
            assertEquals(1, children(panel, JList.class).size());
            assertTrue(children(panel, ShipDetailsPanel.class).isEmpty());
        });
    }

    /** Every Admiral choice retains each selected ordering and publishes only final rows. */
    @Test
    void everyAdmiralSelectionRetainsEveryOrderingWithOnePublication() throws Exception {
        Admirals admirals = admirals();
        SwingUtilities.invokeAndWait(() -> {
            ShipUsagePanel panel = new ShipUsagePanel(admirals,
                    (name, faction, role, rarity, owned) -> new ImageIcon());
            JComboBox<?> selector = children(panel, JComboBox.class).getFirst();
            assertEquals(11, selector.getItemCount());
            List<List<ShipUsageRow>> publications = observe(panel);
            int[] expectedCounts = {63, 21, 42, 12, 48, 1, 2, 4, 8, 16, 32};
            for (String order : List.of(LabelDefaultSort, LabelMostUsed, LabelLeastUsed)) {
                publications.clear();
                button(panel, order).doClick();
                assertEquals(1, publications.size());
                for (int index = 0; index < selector.getItemCount(); index++) {
                    publications.clear();
                    selector.setSelectedIndex(index);
                    assertEquals(1, publications.size(), "Admiral choice " + index + " in " + order);
                    List<ShipUsageRow> visible = rows(panel);
                    assertEquals(visible, publications.getFirst());
                    assertEquals(order.equals(LabelMostUsed)
                                    ? List.of("U.S.S. Enterprise", "Class F Shuttle")
                                    : List.of("Class F Shuttle", "U.S.S. Enterprise"),
                            names(panel));
                    assertEquals(expectedCounts[index], visible.stream()
                            .filter(row -> row.ship().getName().equals("U.S.S. Enterprise"))
                            .findFirst().orElseThrow().deploymentCount());
                    assertTrue(button(panel, order).isSelected());
                }
            }
        });
    }

    /** A Ship Filter choice persists through Admiral replacement and window refresh. */
    @Test
    void selectedFilterAndOrderingSurviveReplacementAndRefresh() throws Exception {
        Admirals admirals = admirals();
        SwingUtilities.invokeAndWait(() -> {
            ShipUsagePanel panel = new ShipUsagePanel(admirals,
                    (name, faction, role, rarity, owned) -> new ImageIcon());
            List<List<ShipUsageRow>> publications = observe(panel);
            button(panel, LabelLeastUsed).doClick();
            button(panel, "Small Craft").doClick();
            assertEquals(2, publications.size());
            JComboBox<?> selector = children(panel, JComboBox.class).getFirst();
            for (int index = 0; index < selector.getItemCount(); index++) {
                publications.clear();
                selector.setSelectedIndex(index);
                assertEquals(1, publications.size());
                assertEquals(List.of("U.S.S. Enterprise"), names(panel));
                assertFalse(button(panel, "Small Craft").isSelected());
                assertTrue(button(panel, LabelLeastUsed).isSelected());
            }

            List<ShipUsageRow> previous = rows(panel);
            admirals.getAdmirals().getLast().clearUsage();
            publications.clear();
            panel.refresh();
            assertEquals(List.of(List.of()), publications);
            assertEquals(32, previous.getFirst().deploymentCount());
            button(panel, "Small Craft").doClick();
            assertEquals(List.of("Class F Shuttle"), names(panel));
        });
    }

    /** Native-free usage content obeys the named presentation's EDT contract. */
    @Test
    void constructionAndRefreshRequireEventThread() throws Exception {
        Admirals admirals = admirals();
        assertThrows(IllegalStateException.class, () -> new ShipUsagePanel(admirals,
                (name, faction, role, rarity, owned) -> new ImageIcon()));
        ShipUsagePanel[] panel = new ShipUsagePanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new ShipUsagePanel(admirals,
                (name, faction, role, rarity, owned) -> new ImageIcon()));
        assertThrows(IllegalStateException.class, panel[0]::refresh);
    }

    /** Records complete public model observations at each publication. */
    private static List<List<ShipUsageRow>> observe(Container panel) {
        List<List<ShipUsageRow>> publications = new ArrayList<>();
        children(panel, JList.class).getFirst().getModel().addListDataListener(new ListDataListener() {
            /** Records an insertion notification if the model emits one. */
            @Override
            public void intervalAdded(ListDataEvent event) {
                publications.add(rows(panel));
            }

            /** Records a removal notification if the model emits one. */
            @Override
            public void intervalRemoved(ListDataEvent event) {
                publications.add(rows(panel));
            }

            /** Records the final replacement visible to Swing observers. */
            @Override
            public void contentsChanged(ListDataEvent event) {
                publications.add(rows(panel));
            }
        });
        return publications;
    }

    /** Restores all six player factions with distinct counts and an unused Roster card. */
    private static Admirals admirals() throws Exception {
        GameData data = GameData.load(Path.of("test/resources/gamedata"));
        List<Admiral> admirals = new ArrayList<>();
        int count = 1;
        for (PlayerFaction faction : PlayerFaction.values()) {
            admirals.add(Admiral.restore(data, faction.name(), faction,
                    List.of(data.ship("Class F Shuttle")), List.of(), List.of(),
                    Map.of(data.ship("U.S.S. Enterprise"), count), true));
            count *= 2;
        }
        return Admirals.restore(data, admirals);
    }

    /** Returns immutable row observations through the public Swing model contract. */
    private static List<ShipUsageRow> rows(Container panel) {
        ListModel<?> model = children(panel, JList.class).getFirst().getModel();
        List<ShipUsageRow> rows = new ArrayList<>();
        for (int index = 0; index < model.getSize(); index++) {
            rows.add((ShipUsageRow) model.getElementAt(index));
        }
        return List.copyOf(rows);
    }

    /** Returns the names displayed to the player in list order. */
    private static List<String> names(Container panel) {
        return rows(panel).stream().map(row -> row.ship().getName()).toList();
    }

    /** Finds one visible control by its player-facing label. */
    private static AbstractButton button(Container panel, String label) {
        return children(panel, AbstractButton.class).stream()
                .filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
    }

    /** Traverses public Swing components without accessing private presentation state. */
    private static <T extends Component> List<T> children(Container root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                matches.add(type.cast(child));
            }
            if (child instanceof Container container) {
                matches.addAll(children(container, type));
            }
        }
        return matches;
    }
}
