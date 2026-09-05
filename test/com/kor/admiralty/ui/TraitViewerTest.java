/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.ui.shipfilter.ShipFilterView;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Specifies that the standalone Trait Viewer defers all UI and data access
 * until its entry point runs.
 */
class TraitViewerTest {

    /**
     * Creates canonical Ship facts with optional Starship Trait content.
     *
     * @param name  canonical Ship name
     * @param tier  canonical tier used by presentation ordering
     * @param trait Starship Trait description, or empty when none is unlocked
     * @return canonical test Ship
     */
    private static Ship ship(String name, Tier tier, String trait) {
        return new ShipImpl(
                ShipFaction.Federation,
                tier,
                Rarity.Epic,
                Role.Tac,
                name,
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                trait);
    }

    /**
     * Verifies loading the entry-point class does not construct a frame or read
     * unbootstrapped application state.
     */
    @Test
    void classInitializationDoesNotRequireApplicationBootstrap() {
        assertDoesNotThrow(() -> Class.forName(
                "com.kor.admiralty.ui.TraitViewer",
                true,
                TraitViewerTest.class.getClassLoader()));
    }

    /**
     * Verifies the standalone GameData Starship Trait presentation remains
     * vertically ordered, non-horizontally-scrolling, trait-only, and rendered
     * with generic rather than owned Roster artwork.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void gameDataTraitPresentationRetainsOrderingScrollingAndArtwork() throws Exception {
        Ship tierSix = ship("Tier Six Trait", Tier.Tier6, "Tier Six");
        Ship tierOne = ship("Tier One Trait", Tier.Tier1, "Tier One");
        Ship noTrait = ship("No Trait", Tier.Tier3, "");
        List<Boolean> ownedRequests = new ArrayList<Boolean>();
        ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        SwingUtilities.invokeAndWait(() -> {
            ShipFilterView<Ship, ShipSortOrder> presentation = TraitViewer.presentation(
                    List.of(tierSix, noTrait, tierOne),
                    (iconName, faction, role, rarity, owned) -> {
                        ownedRequests.add(owned);
                        return icon;
                    });

            JList<?> list = child(presentation, JList.class);
            JScrollPane scrollPane = child(presentation, JScrollPane.class);
            assertEquals(2, list.getModel().getSize());
            assertSame(tierOne, list.getModel().getElementAt(0));
            assertSame(tierSix, list.getModel().getElementAt(1));
            assertEquals(JList.VERTICAL, list.getLayoutOrientation());
            assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                    scrollPane.getHorizontalScrollBarPolicy());
            assertSame(list, scrollPane.getViewport().getView());
            assertTrue(list.getScrollableTracksViewportWidth());

            ownedRequests.clear();
            renderFirstEntry(list);
            assertEquals(List.of(false), ownedRequests);
        });
    }

    /**
     * Verifies replacement keeps the trait-only policy and stable canonical
     * ordering, including exact identities for comparator-equal Ships.
     *
     * @throws Exception if event-thread dispatch fails
     */
    @Test
    void replacementRetainsOnlyTraitsInStableCanonicalOrder() throws Exception {
        Ship alpha = ship("Alpha", Tier.Tier6, "First Trait");
        Ship equalAlpha = ship("Alpha", Tier.Tier6, "Second Trait");
        Ship zulu = ship("Zulu", Tier.Tier6, "Last Trait");
        Ship noTrait = ship("No Trait", Tier.Tier1, "");

        SwingUtilities.invokeAndWait(() -> {
            ShipFilterView<Ship, ShipSortOrder> presentation = TraitViewer.presentation(
                    List.of(), (iconName, faction, role, rarity, owned) -> new ImageIcon());
            presentation.present(List.of(zulu, equalAlpha, noTrait, alpha));

            JList<?> list = child(presentation, JList.class);
            assertEquals(3, list.getModel().getSize());
            assertSame(equalAlpha, list.getModel().getElementAt(0));
            assertSame(alpha, list.getModel().getElementAt(1));
            assertSame(zulu, list.getModel().getElementAt(2));

            presentation.present(List.of(noTrait));
            assertEquals(0, list.getModel().getSize());
        });
    }

    /**
     * Verifies callers cannot construct the standalone content outside Swing's
     * event-dispatch thread.
     */
    @Test
    void presentationRequiresEventDispatchThread() {
        assertThrows(IllegalStateException.class, () -> TraitViewer.presentation(
                List.of(), (iconName, faction, role, rarity, owned) -> new ImageIcon()));
    }

    /**
     * Renders a visible entry through Swing's public renderer contract.
     *
     * @param list displayed entries
     * @param <E> entry type captured from the list
     */
    private static <E> void renderFirstEntry(JList<E> list) {
        list.getCellRenderer().getListCellRendererComponent(
                list, list.getModel().getElementAt(0), 0, false, false);
    }

    /**
     * Finds a displayed Swing component without depending on private view state.
     *
     * @param root presentation subtree
     * @param type displayed component type
     * @param <T> component type
     * @return first matching component, or null when absent
     */
    private static <T extends Component> T child(Container root, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container container) {
                T found = child(container, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
