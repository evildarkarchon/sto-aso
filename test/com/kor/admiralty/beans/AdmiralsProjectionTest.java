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
 * Specifies side-effect-free cross-Admiral projections through the Admirals
 * seam.
 */
class AdmiralsProjectionTest {

        /**
         * Finds one projected row by canonical identity and verifies all of its usage
         * facts.
         *
         * @param rows            immutable projected usage rows
         * @param ship            expected canonical Ship instance
         * @param deploymentCount expected aggregate deployment count
         * @param inCurrentRoster whether the Ship type should occur in a selected
         *                        current Roster
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
         * Restores one test Admiral with canonical history while leaving its Roster
         * empty for projection operations.
         *
         * @param gameData    reference data shared by the test container
         * @param usageCounts canonical usage history
         * @return construction-safe Admiral
         */
        private static Admiral restoredAdmiral(GameData gameData, Map<Ship, Integer> usageCounts) {
                return Admiral.restore(
                                gameData,
                                "Projection Admiral",
                                com.kor.admiralty.enums.PlayerFaction.Federation,
                                List.of(),
                                List.of(),
                                List.of(),
                                usageCounts,
                                true);
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

        /**
         * Verifies callers cannot add or remove Admirals without using the container's
         * intent operations.
         */
        @Test
        void admiralsViewDoesNotExposeMutableContainerState() {
                GameData gameData = GameData.builder().build();
                Admirals admirals = new Admirals(gameData);

                List<Admiral> currentAdmirals = admirals.getAdmirals();

                assertThrows(UnsupportedOperationException.class, currentAdmirals::clear);
                assertEquals(1, admirals.getAdmirals().size());
        }

        /**
         * Verifies current reusable and One-Time cards form an immutable union while
         * usage history remains separate.
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
                Admiral first = restoredAdmiral(gameData, Map.of(historicalShip, 4));
                Admiral second = new Admiral(gameData);
                Admirals admirals = Admirals.restore(gameData, List.of(first, second));
                first.addReusableShips(List.of(activeShip), RosterState.ACTIVE);
                first.adjustOneTimeShipQuantity(oneTimeShip, 2);
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
        }

        /**
         * Verifies every Roster mutation is reflected on the next projection without a
         * validation or refresh step.
         */
        @Test
        void currentRosterShipTypesFollowRosterChangesWithoutRevalidation() {
                Ship reusableShip = ship("Reusable Ship");
                Ship oneTimeShip = ship("Quantity Ship");
                GameData gameData = GameData.builder().ships(List.of(reusableShip, oneTimeShip)).build();
                Admirals admirals = new Admirals(gameData);
                Admiral admiral = admirals.getAdmirals().getFirst();
                Set<Ship> initialProjection = admirals.getCurrentRosterShipTypes();

                admiral.addReusableShips(List.of(reusableShip), RosterState.ACTIVE);
                assertEquals(Set.of(reusableShip), admirals.getCurrentRosterShipTypes());
                assertEquals(Set.of(), initialProjection);

                RosterCard reusableCard = admiral.getRoster().getReusableCards().getFirst();
                admiral.moveReusableCards(List.of(reusableCard), RosterState.MAINTENANCE);
                assertEquals(Set.of(reusableShip), admirals.getCurrentRosterShipTypes());

                admiral.adjustOneTimeShipQuantity(oneTimeShip, 2);
                assertEquals(Set.of(reusableShip, oneTimeShip), admirals.getCurrentRosterShipTypes());

                admiral.removeReusableCards(List.of(admiral.getRoster().getReusableCards().getFirst()));
                assertEquals(Set.of(oneTimeShip), admirals.getCurrentRosterShipTypes());

                admiral.adjustOneTimeShipQuantity(oneTimeShip, -2);
                assertEquals(Set.of(), admirals.getCurrentRosterShipTypes());
        }

        /**
         * Verifies selected Admirals project immutable canonical rows that combine
         * current Roster membership and history.
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
                Admiral first = restoredAdmiral(gameData, Map.of(historicalShip, 4, oneTimeShip, 1));
                Admiral second = restoredAdmiral(gameData, Map.of(historicalShip, 3, oneTimeShip, 2));
                Admirals admirals = Admirals.restore(gameData, List.of(first, second));
                first.addReusableShips(List.of(activeShip), RosterState.ACTIVE);
                first.adjustOneTimeShipQuantity(oneTimeShip, 2);
                second.addReusableShips(List.of(maintenanceShip), RosterState.MAINTENANCE);

                List<ShipUsageRow> rows = admirals.getShipUsageRows(first, second);

                assertEquals(
                                List.of("Active Ship", "Historical Ship", "Maintenance Ship", "One-Time Ship"),
                                rows.stream().map(row -> row.getShip().getName())
                                                .collect(java.util.stream.Collectors.toList()));
                assertUsageRow(rows, activeShip, 0, true);
                assertUsageRow(rows, maintenanceShip, 0, true);
                assertUsageRow(rows, oneTimeShip, 3, true);
                assertUsageRow(rows, historicalShip, 7, false);
                assertThrows(UnsupportedOperationException.class, rows::clear);
        }

        /**
         * Verifies changing the selected Admirals repeatedly never rewrites GameData or
         * either Admiral's state.
         */
        @Test
        void changingUsageSelectionRepeatedlyLeavesSharedAndPerAdmiralStateUnchanged() {
                Ship firstShip = ship("First Ship");
                Ship secondShip = ship("Second Ship");
                Ship sharedHistory = ship("Shared History");
                GameData gameData = GameData.builder().ships(List.of(firstShip, secondShip, sharedHistory)).build();
                Admiral first = restoredAdmiral(gameData, Map.of(sharedHistory, 2));
                Admiral second = restoredAdmiral(gameData, Map.of(sharedHistory, 5));
                Admirals admirals = Admirals.restore(gameData, List.of(first, second));
                first.addReusableShips(List.of(firstShip), RosterState.ACTIVE);
                second.adjustOneTimeShipQuantity(secondShip, 1);
                RosterView firstRoster = first.getRoster();
                RosterView secondRoster = second.getRoster();
                Map<String, Integer> firstUsage = first.getUsageCounts();
                Map<String, Integer> secondUsage = second.getUsageCounts();

                List<ShipUsageRow> firstSelection = admirals.getShipUsageRows(first);
                assertUsageRow(firstSelection, sharedHistory, 2, false);
                assertUsageRow(admirals.getShipUsageRows(second), sharedHistory, 5, false);
                assertUsageRow(admirals.getShipUsageRows(first, second), sharedHistory, 7, false);
                assertUsageRow(admirals.getShipUsageRows(second, first), sharedHistory, 7, false);
                assertUsageRow(admirals.getShipUsageRows(first), sharedHistory, 2, false);
                assertUsageRow(firstSelection, sharedHistory, 2, false);

                assertSame(firstRoster, first.getRoster());
                assertSame(secondRoster, second.getRoster());
                assertEquals(firstUsage, first.getUsageCounts());
                assertEquals(secondUsage, second.getUsageCounts());
        }

        /**
         * Verifies clearing history removes historical-only rows while retaining
         * zero-use current Roster rows and revision.
         */
        @Test
        void clearingUsageRetainsCurrentRosterRowsWithoutInvalidatingRosterRevision() {
                Ship reusableShip = ship("Reusable Row");
                Ship oneTimeShip = ship("One-Time Row");
                Ship historicalShip = ship("Historical Row");
                GameData gameData = GameData.builder()
                                .ships(List.of(reusableShip, oneTimeShip, historicalShip))
                                .build();
                Admiral admiral = restoredAdmiral(
                                gameData,
                                Map.of(reusableShip, 3, oneTimeShip, 2, historicalShip, 8));
                Admirals admirals = Admirals.restore(gameData, List.of(admiral));
                admiral.addReusableShips(List.of(reusableShip), RosterState.ACTIVE);
                admiral.adjustOneTimeShipQuantity(oneTimeShip, 2);
                RosterView rosterBeforeClear = admiral.getRoster();

                admiral.clearUsage();
                List<ShipUsageRow> rowsAfterClear = admirals.getShipUsageRows(admiral);

                assertSame(rosterBeforeClear, admiral.getRoster());
                assertUsageRow(rowsAfterClear, reusableShip, 0, true);
                assertUsageRow(rowsAfterClear, oneTimeShip, 0, true);
                assertFalse(rowsAfterClear.stream().anyMatch(row -> row.getShip() == historicalShip));
                assertEquals(2, rowsAfterClear.size());
        }
}
