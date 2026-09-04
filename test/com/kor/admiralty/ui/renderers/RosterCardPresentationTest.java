/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.renderers;

import com.kor.admiralty.beans.*;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Specifies card-kind presentation shared by Roster lists and deployed
 * Assignment slots.
 */
class RosterCardPresentationTest {

    /**
     * Verifies reusable cards retain owned artwork while One-Time cards retain
     * generic artwork and quantity text.
     */
    @Test
    void cardKindPreservesHistoricalArtworkAndDisplayName() {
        Ship ship = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Epic,
                Role.Tac,
                "Presentation Ship",
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(ship)).build());
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(ship, 1);
        RosterCard reusableCard = admiral.getRoster().getActiveCards().getFirst();
        RosterCard oneTimeCard = admiral.getRoster().getOneTimeCards().getFirst();

        assertTrue(RosterCardPresentation.useRosterArtwork(reusableCard));
        assertFalse(RosterCardPresentation.useRosterArtwork(oneTimeCard));
        assertEquals(ship.getDisplayName(), RosterCardPresentation.displayName(reusableCard));
        assertEquals("(1x) " + ship.getName(), RosterCardPresentation.displayName(oneTimeCard));
    }
}
