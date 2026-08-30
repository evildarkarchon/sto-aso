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
package com.kor.admiralty.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

/**
 * Specifies side-effect-free cross-Admiral projections through the Admirals seam.
 */
class AdmiralsProjectionTest {

    /**
     * Verifies current reusable and One-Time cards form an immutable union while usage history remains separate.
     */
    @Test
    void currentRosterShipTypesAreImmutableAcrossMultipleAdmirals() {
        Ship activeShip = ship("Active Ship");
        Ship maintenanceShip = ship("Maintenance Ship");
        Ship oneTimeShip = ship("One-Time Ship");
        Ship historicalShip = ship("Historical Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(activeShip, maintenanceShip, oneTimeShip, historicalShip))
                .build();
        Admirals admirals = new Admirals(gameData);
        Admiral first = admirals.getAdmirals().get(0);
        Admiral second = admirals.addAdmiral();
        first.addReusableShips(List.of(activeShip), RosterState.ACTIVE);
        first.adjustOneTimeShipQuantity(oneTimeShip, 2);
        first.setUsage(new HashMap<String, Integer>(Map.of(historicalShip.getName(), 4)));
        second.addReusableShips(List.of(maintenanceShip), RosterState.MAINTENANCE);
        second.adjustOneTimeShipQuantity(oneTimeShip, 1);

        Set<Ship> projectedShipTypes = admirals.getCurrentRosterShipTypes();

        assertEquals(Set.of(activeShip, maintenanceShip, oneTimeShip), projectedShipTypes);
        assertSame(activeShip, projectedShipTypes.stream()
                .filter(ship -> ship.getName().equals(activeShip.getName()))
                .findFirst()
                .orElseThrow());
        assertSame(oneTimeShip, projectedShipTypes.stream()
                .filter(ship -> ship.getName().equals(oneTimeShip.getName()))
                .findFirst()
                .orElseThrow());
        assertFalse(projectedShipTypes.contains(historicalShip));
        assertThrows(UnsupportedOperationException.class, projectedShipTypes::clear);
        for (Ship ship : gameData.ships()) {
            assertFalse(ship.isOwned());
            assertEquals(-1, ship.getUsageCount());
        }
    }

    /**
     * Verifies every Roster mutation is reflected on the next projection without a validation or refresh step.
     */
    @Test
    void currentRosterShipTypesFollowRosterChangesWithoutRevalidation() {
        Ship reusableShip = ship("Reusable Ship");
        Ship oneTimeShip = ship("Quantity Ship");
        GameData gameData = GameData.builder().ships(List.of(reusableShip, oneTimeShip)).build();
        Admirals admirals = new Admirals(gameData);
        Admiral admiral = admirals.getAdmirals().get(0);
        Set<Ship> initialProjection = admirals.getCurrentRosterShipTypes();

        admiral.addReusableShips(List.of(reusableShip), RosterState.ACTIVE);
        assertEquals(Set.of(reusableShip), admirals.getCurrentRosterShipTypes());
        assertEquals(Set.of(), initialProjection);

        RosterCard reusableCard = admiral.getRoster().getReusableCards().get(0);
        admiral.moveReusableCards(List.of(reusableCard), RosterState.MAINTENANCE);
        assertEquals(Set.of(reusableShip), admirals.getCurrentRosterShipTypes());

        admiral.adjustOneTimeShipQuantity(oneTimeShip, 2);
        assertEquals(Set.of(reusableShip, oneTimeShip), admirals.getCurrentRosterShipTypes());

        admiral.removeReusableCards(List.of(admiral.getRoster().getReusableCards().get(0)));
        assertEquals(Set.of(oneTimeShip), admirals.getCurrentRosterShipTypes());

        admiral.adjustOneTimeShipQuantity(oneTimeShip, -2);
        assertEquals(Set.of(), admirals.getCurrentRosterShipTypes());
    }

    /**
     * Creates representative canonical Ship facts for projection tests.
     *
     * @param name canonical Ship name
     * @return mutable Ship supplied directly to builder-created GameData
     */
    private static Ship ship(String name) {
        return new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Common,
                Role.Eng,
                name,
                10,
                10,
                10,
                RuleType.All.rewardBonus(0),
                "");
    }
}
