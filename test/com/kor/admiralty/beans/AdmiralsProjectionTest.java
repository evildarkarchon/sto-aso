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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * Verifies selected Admirals project immutable canonical rows that combine current Roster membership and history.
     */
    @Test
    void usageRowsCombineCanonicalCountsWithCurrentRosterMembership() {
        Ship activeShip = ship("Active Ship");
        Ship maintenanceShip = ship("Maintenance Ship");
        Ship oneTimeShip = ship("One-Time Ship");
        Ship historicalShip = ship("Historical Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(activeShip, maintenanceShip, oneTimeShip, historicalShip))
                .renamedShips(Map.of("Former Historical Ship", historicalShip.getName()))
                .build();
        Admirals admirals = new Admirals(gameData);
        Admiral first = admirals.getAdmirals().get(0);
        Admiral second = admirals.addAdmiral();
        first.addReusableShips(List.of(activeShip), RosterState.ACTIVE);
        first.adjustOneTimeShipQuantity(oneTimeShip, 2);
        first.setUsage(new HashMap<String, Integer>(Map.of(
                "Former Historical Ship", 4,
                oneTimeShip.getName(), 1)));
        second.addReusableShips(List.of(maintenanceShip), RosterState.MAINTENANCE);
        second.setUsage(new HashMap<String, Integer>(Map.of(
                historicalShip.getName(), 3,
                oneTimeShip.getName(), 2)));

        List<ShipUsageRow> rows = admirals.getShipUsageRows(first, second);

        assertEquals(
                List.of("Active Ship", "Historical Ship", "Maintenance Ship", "One-Time Ship"),
                rows.stream().map(row -> row.getShip().getName()).collect(java.util.stream.Collectors.toList()));
        assertUsageRow(rows, activeShip, 0, true);
        assertUsageRow(rows, maintenanceShip, 0, true);
        assertUsageRow(rows, oneTimeShip, 3, true);
        assertUsageRow(rows, historicalShip, 7, false);
        assertThrows(UnsupportedOperationException.class, rows::clear);
        for (Ship ship : gameData.ships()) {
            assertFalse(ship.isOwned());
        }
    }

    /**
     * Verifies changing the selected Admirals repeatedly never rewrites GameData or either Admiral's state.
     */
    @Test
    void changingUsageSelectionRepeatedlyLeavesSharedAndPerAdmiralStateUnchanged() {
        Ship firstShip = ship("First Ship");
        Ship secondShip = ship("Second Ship");
        Ship sharedHistory = ship("Shared History");
        GameData gameData = GameData.builder().ships(List.of(firstShip, secondShip, sharedHistory)).build();
        Admirals admirals = new Admirals(gameData);
        Admiral first = admirals.getAdmirals().get(0);
        Admiral second = admirals.addAdmiral();
        first.addReusableShips(List.of(firstShip), RosterState.ACTIVE);
        second.adjustOneTimeShipQuantity(secondShip, 1);
        first.setUsage(new HashMap<String, Integer>(Map.of(sharedHistory.getName(), 2)));
        second.setUsage(new HashMap<String, Integer>(Map.of(sharedHistory.getName(), 5)));
        RosterView firstRoster = first.getRoster();
        RosterView secondRoster = second.getRoster();
        Map<String, Integer> firstUsage = new HashMap<String, Integer>(first.getUsage());
        Map<String, Integer> secondUsage = new HashMap<String, Integer>(second.getUsage());

        List<ShipUsageRow> firstSelection = admirals.getShipUsageRows(first);
        assertUsageRow(firstSelection, sharedHistory, 2, false);
        assertUsageRow(admirals.getShipUsageRows(second), sharedHistory, 5, false);
        assertUsageRow(admirals.getShipUsageRows(first, second), sharedHistory, 7, false);
        assertUsageRow(admirals.getShipUsageRows(second, first), sharedHistory, 7, false);
        assertUsageRow(admirals.getShipUsageRows(first), sharedHistory, 2, false);
        assertUsageRow(firstSelection, sharedHistory, 2, false);

        assertSame(firstRoster, first.getRoster());
        assertSame(secondRoster, second.getRoster());
        assertEquals(firstUsage, first.getUsage());
        assertEquals(secondUsage, second.getUsage());
        for (Ship ship : gameData.ships()) {
            assertFalse(ship.isOwned());
        }
    }

    /**
     * Verifies clearing history removes historical-only rows while retaining zero-use current Roster rows and revision.
     */
    @Test
    void clearingUsageRetainsCurrentRosterRowsWithoutInvalidatingRosterRevision() {
        Ship reusableShip = ship("Reusable Row");
        Ship oneTimeShip = ship("One-Time Row");
        Ship historicalShip = ship("Historical Row");
        GameData gameData = GameData.builder()
                .ships(List.of(reusableShip, oneTimeShip, historicalShip))
                .build();
        Admirals admirals = new Admirals(gameData);
        Admiral admiral = admirals.getAdmirals().get(0);
        admiral.addReusableShips(List.of(reusableShip), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(oneTimeShip, 2);
        admiral.setUsage(new HashMap<String, Integer>(Map.of(
                reusableShip.getName(), 3,
                oneTimeShip.getName(), 2,
                historicalShip.getName(), 8)));
        RosterView rosterBeforeClear = admiral.getRoster();

        admiral.clearUsage();
        List<ShipUsageRow> rowsAfterClear = admirals.getShipUsageRows(admiral);

        assertSame(rosterBeforeClear, admiral.getRoster());
        assertUsageRow(rowsAfterClear, reusableShip, 0, true);
        assertUsageRow(rowsAfterClear, oneTimeShip, 0, true);
        assertFalse(rowsAfterClear.stream().anyMatch(row -> row.getShip() == historicalShip));
        assertEquals(2, rowsAfterClear.size());
    }

    /**
     * Finds one projected row by canonical identity and verifies all of its usage facts.
     *
     * @param rows immutable projected usage rows
     * @param ship expected canonical Ship instance
     * @param deploymentCount expected aggregate deployment count
     * @param inCurrentRoster whether the Ship type should occur in a selected current Roster
     */
    private static void assertUsageRow(
            List<ShipUsageRow> rows,
            Ship ship,
            int deploymentCount,
            boolean inCurrentRoster) {
        ShipUsageRow row = rows.stream()
                .filter(candidate -> candidate.getShip().getName().equals(ship.getName()))
                .findFirst()
                .orElseThrow();
        assertSame(ship, row.getShip());
        assertEquals(deploymentCount, row.getDeploymentCount());
        assertEquals(inCurrentRoster, row.isInCurrentRoster());
        assertTrue(row.getDeploymentCount() >= 0);
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
