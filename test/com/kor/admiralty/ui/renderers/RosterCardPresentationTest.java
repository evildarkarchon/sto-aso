/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

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
