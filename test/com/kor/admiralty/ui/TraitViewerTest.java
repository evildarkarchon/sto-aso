/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JList;
import javax.swing.JScrollPane;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

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
     */
    @Test
    void gameDataTraitPresentationRetainsOrderingScrollingAndArtwork() {
        Ship tierSix = ship("Tier Six Trait", Tier.Tier6, "Tier Six");
        Ship tierOne = ship("Tier One Trait", Tier.Tier1, "Tier One");
        Ship noTrait = ship("No Trait", Tier.Tier3, "");
        List<Boolean> ownedRequests = new ArrayList<Boolean>();
        ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        TraitViewer.TraitPresentation presentation = TraitViewer.presentation(
                List.of(tierSix, noTrait, tierOne),
                (iconName, faction, role, rarity, owned) -> {
                    ownedRequests.add(owned);
                    return icon;
                });

        assertEquals(2, presentation.model().getSize());
        assertSame(tierOne, presentation.model().getElementAt(0));
        assertSame(tierSix, presentation.model().getElementAt(1));
        assertEquals(JList.VERTICAL, presentation.list().getLayoutOrientation());
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                presentation.scrollPane().getHorizontalScrollBarPolicy());
        assertSame(presentation.list(), presentation.scrollPane().getViewport().getView());

        presentation.list().getCellRenderer().getListCellRendererComponent(
                presentation.list(),
                tierOne,
                0,
                false,
                false);
        assertEquals(List.of(false), ownedRequests);
    }
}
