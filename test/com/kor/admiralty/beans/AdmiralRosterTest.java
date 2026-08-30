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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

/**
 * Specifies reusable Roster behavior through the public Admiral seam.
 */
class AdmiralRosterTest {

    /**
     * Verifies add and move operations commit one immutable, revisioned change while retaining card identity.
     */
    @Test
    void reusableAddAndMovePublishCommittedRevisionedChanges() {
        Ship canonicalShip = ship("Canonical Ship");
        GameData gameData = GameData.builder().ships(List.of(canonicalShip)).build();
        Admiral admiral = new Admiral(gameData);
        List<RosterChange> changes = new ArrayList<RosterChange>();
        List<RosterState> statesObservedByListener = new ArrayList<RosterState>();
        admiral.addRosterChangeListener(change -> {
            changes.add(change);
            statesObservedByListener.add(admiral.getRoster().getReusableState(canonicalShip));
            assertSame(change.getAfter(), admiral.getRoster());
        });

        RosterView initial = admiral.getRoster();
        admiral.addReusableShips(List.of(canonicalShip), RosterState.ACTIVE);

        RosterView active = admiral.getRoster();
        RosterCard activeCard = active.getActiveCards().get(0);
        assertEquals(0L, initial.getRevision());
        assertEquals(1L, active.getRevision());
        assertSame(canonicalShip, activeCard.getShip());
        assertEquals(RosterState.ACTIVE, activeCard.getState());
        assertEquals(List.of(RosterState.ACTIVE), statesObservedByListener);
        assertSame(initial, changes.get(0).getBefore());
        assertSame(active, changes.get(0).getAfter());
        assertThrows(UnsupportedOperationException.class, () -> active.getActiveCards().clear());

        admiral.addReusableShips(List.of(canonicalShip), RosterState.ACTIVE);

        assertSame(active, admiral.getRoster());
        assertEquals(1, changes.size());

        admiral.addReusableShips(List.of(canonicalShip), RosterState.MAINTENANCE);

        RosterView maintenance = admiral.getRoster();
        RosterCard maintenanceCard = maintenance.getMaintenanceCards().get(0);
        assertEquals(2L, maintenance.getRevision());
        assertEquals(activeCard.getId(), maintenanceCard.getId());
        assertEquals(RosterState.MAINTENANCE, maintenanceCard.getState());
        assertEquals(List.of(RosterState.ACTIVE, RosterState.MAINTENANCE), statesObservedByListener);
        assertEquals(2, changes.size());
        assertEquals(1, active.getActiveCards().size());
        assertEquals(0, active.getMaintenanceCards().size());
    }

    /**
     * Verifies identity-based moves and removals preserve uniqueness and re-adding creates a new card.
     */
    @Test
    void reusableCardsMoveAndRemoveByOpaqueIdentity() {
        Ship alpha = ship("Alpha");
        Ship beta = ship("Beta");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(alpha, beta)).build());
        List<RosterChange> changes = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(changes::add);
        admiral.addReusableShips(List.of(alpha, beta), RosterState.ACTIVE);
        RosterCard alphaCard = cardFor(admiral.getRoster(), alpha);

        admiral.moveReusableCards(List.of(alphaCard), RosterState.MAINTENANCE);

        RosterView moved = admiral.getRoster();
        assertEquals(RosterState.MAINTENANCE, moved.getReusableState(alpha));
        assertEquals(RosterState.ACTIVE, moved.getReusableState(beta));
        assertEquals(alphaCard.getId(), cardFor(moved, alpha).getId());
        assertEquals(2L, moved.getRevision());

        admiral.removeReusableCards(List.of(cardFor(moved, alpha)));

        RosterView removed = admiral.getRoster();
        assertEquals(RosterState.ABSENT, removed.getReusableState(alpha));
        assertEquals(RosterState.ACTIVE, removed.getReusableState(beta));
        assertEquals(3L, removed.getRevision());

        admiral.addReusableShips(List.of(alpha), RosterState.ACTIVE);

        assertNotEquals(alphaCard.getId(), cardFor(admiral.getRoster(), alpha).getId());
        assertEquals(4L, admiral.getRoster().getRevision());
        assertEquals(4, changes.size());
    }

    /**
     * Verifies quantity-backed One-Time copies retain distinct identities beside a reusable card of the same Ship.
     */
    @Test
    void oneTimeQuantitiesExposeDistinctCardsAlongsideReusableCard() {
        Ship sharedShip = ship("Reusable and One-Time Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(sharedShip)).build());
        admiral.addReusableShips(List.of(sharedShip), RosterState.ACTIVE);

        admiral.adjustOneTimeShipQuantity(sharedShip, 2);

        RosterView roster = admiral.getRoster();
        RosterCard reusableCard = roster.getActiveCards().get(0);
        RosterCard firstOneTimeCard = roster.getOneTimeCards().get(0);
        RosterCard secondOneTimeCard = roster.getOneTimeCards().get(1);
        assertEquals(2, roster.getOneTimeQuantity(sharedShip));
        assertEquals(3, roster.getCards().size());
        assertEquals(RosterCardKind.REUSABLE, reusableCard.getKind());
        assertEquals(RosterCardKind.ONE_TIME, firstOneTimeCard.getKind());
        assertEquals(RosterState.ONE_TIME, firstOneTimeCard.getState());
        assertEquals(RosterState.ACTIVE, roster.getReusableState(sharedShip));
        assertSame(sharedShip, firstOneTimeCard.getShip());
        assertSame(sharedShip, secondOneTimeCard.getShip());
        assertNotEquals(reusableCard.getId(), firstOneTimeCard.getId());
        assertNotEquals(firstOneTimeCard.getId(), secondOneTimeCard.getId());
        assertEquals(List.of(reusableCard, firstOneTimeCard, secondOneTimeCard), roster.getDeployableCards(true));
    }

    /**
     * Verifies temporary repeated-name delegates canonicalize One-Time multiplicity through the Roster.
     */
    @Test
    void oneTimeCompatibilityDelegatesPreserveCanonicalMultiplicity() {
        Ship canonicalShip = ship("Canonical One-Time Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(canonicalShip))
                .renamedShips(java.util.Map.of("Former One-Time Ship", canonicalShip.getName()))
                .build();
        Admiral admiral = new Admiral(gameData);
        List<String> input = new ArrayList<String>(List.of(
                "former one-time ship",
                "CANONICAL ONE-TIME SHIP",
                "Unknown Ship"));

        admiral.setOneTime(input);
        input.clear();

        assertEquals(2, admiral.getRoster().getOneTimeQuantity(canonicalShip));
        assertEquals(List.of(canonicalShip.getName(), canonicalShip.getName()), admiral.getOneTime());
        assertThrows(UnsupportedOperationException.class, () -> admiral.getOneTime().clear());

        admiral.addOneTime(canonicalShip.getName());
        assertEquals(3, admiral.getRoster().getOneTimeQuantity(canonicalShip));

        admiral.removeOneTime(canonicalShip.getName());
        assertEquals(2, admiral.getRoster().getOneTimeQuantity(canonicalShip));
        assertEquals(2, admiral.getOneTimeShips().size());
    }

    /**
     * Verifies one bulk One-Time quantity intent adjusts multiple Ship types in one committed Roster change.
     */
    @Test
    void oneTimeQuantityIntentAdjustsMultipleShipTypesAtomically() {
        Ship alpha = ship("Alpha One-Time Intent");
        Ship beta = ship("Beta One-Time Intent");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(alpha, beta)).build());
        List<RosterChange> changes = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(changes::add);

        admiral.adjustOneTimeShipQuantities(List.of(alpha, beta), 1);

        assertEquals(1, admiral.getRoster().getOneTimeQuantity(alpha));
        assertEquals(1, admiral.getRoster().getOneTimeQuantity(beta));
        assertEquals(1, changes.size());

        admiral.adjustOneTimeShipQuantities(List.of(alpha, beta), -1);

        assertEquals(0, admiral.getRoster().getOneTimeQuantity(alpha));
        assertEquals(0, admiral.getRoster().getOneTimeQuantity(beta));
        assertEquals(2, changes.size());
    }

    /**
     * Verifies bulk Ship-shaped adapters adjust repeated One-Time copies in one committed Roster change.
     */
    @Test
    void shipCompatibilityAdaptersAdjustOneTimeCopiesAtomically() {
        Ship oneTimeShip = ship("Bulk One-Time Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(oneTimeShip)).build());
        List<RosterChange> changes = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(changes::add);

        admiral.addOneTimeShips(List.of(oneTimeShip, oneTimeShip));

        assertEquals(2, admiral.getRoster().getOneTimeQuantity(oneTimeShip));
        assertEquals(1L, admiral.getRoster().getRevision());
        assertEquals(1, changes.size());

        admiral.removeOneTimeShips(List.of(oneTimeShip));

        assertEquals(1, admiral.getRoster().getOneTimeQuantity(oneTimeShip));
        assertEquals(2L, admiral.getRoster().getRevision());
        assertEquals(2, changes.size());
    }

    /**
     * Verifies quantity decrements retain surviving identities and invalid negatives cannot partially mutate state.
     */
    @Test
    void oneTimeQuantityZeroMeansAbsenceAndInvalidNegativeIsAtomic() {
        Ship oneTimeShip = ship("Quantity Invariant Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(oneTimeShip)).build());
        admiral.setUsage(new java.util.HashMap<String, Integer>(java.util.Map.of(oneTimeShip.getName(), 7)));
        admiral.adjustOneTimeShipQuantity(oneTimeShip, 3);
        RosterView quantityThree = admiral.getRoster();
        List<RosterCardId> originalIds = quantityThree.getOneTimeCards().stream()
                .map(RosterCard::getId)
                .collect(java.util.stream.Collectors.toList());

        admiral.adjustOneTimeShipQuantity(oneTimeShip, -1);

        RosterView quantityTwo = admiral.getRoster();
        assertEquals(originalIds.subList(0, 2), quantityTwo.getOneTimeCards().stream()
                .map(RosterCard::getId)
                .collect(java.util.stream.Collectors.toList()));
        assertEquals(2, quantityTwo.getOneTimeQuantity(oneTimeShip));
        assertEquals(3, quantityThree.getOneTimeCards().size());
        assertThrows(UnsupportedOperationException.class, () -> quantityTwo.getOneTimeCards().clear());

        admiral.adjustOneTimeShipQuantity(oneTimeShip, -2);

        RosterView absent = admiral.getRoster();
        List<RosterChange> rejectedChanges = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(rejectedChanges::add);
        assertEquals(0, absent.getOneTimeQuantity(oneTimeShip));
        assertEquals(List.of(), absent.getOneTimeCards());

        assertThrows(IllegalArgumentException.class, () -> admiral.adjustOneTimeShipQuantity(oneTimeShip, -1));

        assertSame(absent, admiral.getRoster());
        assertEquals(3L, admiral.getRoster().getRevision());
        assertEquals(java.util.Map.of(oneTimeShip.getName(), 7), admiral.getUsage());
        assertEquals(List.of(), rejectedChanges);
    }

    /**
     * Verifies one immutable revision exposes every Roster category with natural and priority ordering intact.
     */
    @Test
    void completeRosterViewKeepsNaturalOrderingWithinDeployablePriorityGroups() {
        Ship activeZulu = ship("Zulu Active");
        Ship activeAlpha = ship("Alpha Active");
        Ship maintenance = ship("Maintenance Only");
        Ship oneTimeZulu = ship("Zulu One-Time");
        Ship oneTimeAlpha = ship("Alpha One-Time");
        Admiral admiral = new Admiral(GameData.builder()
                .ships(List.of(activeZulu, activeAlpha, maintenance, oneTimeZulu, oneTimeAlpha))
                .build());
        admiral.addReusableShips(List.of(activeZulu, activeAlpha), RosterState.ACTIVE);
        admiral.addReusableShips(List.of(maintenance), RosterState.MAINTENANCE);
        admiral.adjustOneTimeShipQuantity(oneTimeZulu, 1);
        admiral.adjustOneTimeShipQuantity(oneTimeAlpha, 2);

        RosterView roster = admiral.getRoster();

        assertEquals(List.of("Alpha Active", "Zulu Active"), cardNames(roster.getActiveCards()));
        assertEquals(List.of("Maintenance Only"), cardNames(roster.getMaintenanceCards()));
        assertEquals(
                List.of("Alpha One-Time", "Alpha One-Time", "Zulu One-Time"),
                cardNames(roster.getOneTimeCards()));
        assertEquals(
                List.of(
                        "Alpha Active", "Zulu Active", "Maintenance Only",
                        "Alpha One-Time", "Alpha One-Time", "Zulu One-Time"),
                cardNames(roster.getCards()));
        assertEquals(
                List.of(
                        "Alpha Active", "Zulu Active",
                        "Alpha One-Time", "Alpha One-Time", "Zulu One-Time"),
                cardNames(roster.getDeployableCards(true)));
        assertEquals(
                List.of(
                        "Alpha One-Time", "Alpha One-Time", "Zulu One-Time",
                        "Alpha Active", "Zulu Active"),
                cardNames(roster.getDeployableCards(false)));
    }

    /**
     * Verifies temporary Ship-shaped methods preserve atomic Roster semantics without exposing mutable names.
     */
    @Test
    void shipCompatibilityDelegatesUseTheAtomicRoster() {
        Ship ship = ship("Compatibility Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(ship)).build());
        List<RosterChange> changes = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(changes::add);

        admiral.addMaintenanceShips(List.of(ship));

        RosterCard maintenanceCard = cardFor(admiral.getRoster(), ship);
        assertEquals(RosterState.MAINTENANCE, maintenanceCard.getState());
        assertEquals(1L, admiral.getRoster().getRevision());

        admiral.addActiveShips(List.of(ship));

        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(ship));
        assertEquals(maintenanceCard.getId(), cardFor(admiral.getRoster(), ship).getId());
        assertEquals(2L, admiral.getRoster().getRevision());
        assertEquals(2, changes.size());
        assertEquals(List.of(ship.getName()), admiral.getActive());
        assertEquals(List.of(), admiral.getMaintenance());
        assertThrows(UnsupportedOperationException.class, () -> admiral.getActive().clear());
    }

    /**
     * Verifies name replacement and removal delegates canonicalize, deduplicate, and preserve one-state membership.
     */
    @Test
    void nameReplacementAndRemovalDelegatesCannotBypassRosterInvariants() {
        Ship alpha = ship("Alpha");
        Ship beta = ship("Beta");
        Ship gamma = ship("Gamma");
        GameData gameData = GameData.builder()
                .ships(List.of(alpha, beta, gamma))
                .renamedShips(java.util.Map.of("Former Alpha", alpha.getName()))
                .build();
        Admiral admiral = new Admiral(gameData);
        List<String> activeInput = new ArrayList<String>(List.of("former alpha", "BETA", "former alpha"));

        admiral.setActive(activeInput);
        activeInput.clear();

        assertEquals(List.of(alpha.getName(), beta.getName()), admiral.getActive());
        assertEquals(List.of(alpha.getName(), beta.getName()), shipNames(admiral.getActiveShips()));
        assertEquals(1L, admiral.getRoster().getRevision());

        admiral.setMaintenance(new ArrayList<String>(List.of("beta", gamma.getName())));

        assertEquals(List.of(alpha.getName()), admiral.getActive());
        assertEquals(List.of(beta.getName(), gamma.getName()), admiral.getMaintenance());
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(alpha));
        assertEquals(RosterState.MAINTENANCE, admiral.getRoster().getReusableState(beta));
        assertEquals(RosterState.MAINTENANCE, admiral.getRoster().getReusableState(gamma));

        admiral.removeActiveShips(List.of(alpha));
        admiral.removeMaintenance(beta.getName());
        admiral.removeActiveOrMaintenanceShips(List.of(gamma));

        assertEquals(List.of(), admiral.getActive());
        assertEquals(List.of(), admiral.getMaintenance());
        assertEquals(5L, admiral.getRoster().getRevision());
    }

    /**
     * Verifies compatibility-only input ordering does not create a planning-relevant Roster change.
     */
    @Test
    void reorderingSameCompatibilityNamesIsARosterNoOp() {
        Ship alpha = ship("Order Alpha");
        Ship beta = ship("Order Beta");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(alpha, beta)).build());
        admiral.setActive(new ArrayList<String>(List.of(alpha.getName(), beta.getName())));
        RosterView beforeReorder = admiral.getRoster();
        List<RosterChange> changes = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(changes::add);

        admiral.setActive(new ArrayList<String>(List.of(beta.getName(), alpha.getName())));

        assertSame(beforeReorder, admiral.getRoster());
        assertEquals(1L, admiral.getRoster().getRevision());
        assertEquals(List.of(), changes);
    }

    /**
     * Verifies legacy multi-Ship deployment moves all reusable cards in one committed Roster change.
     */
    @Test
    void legacyAssignmentMovesReusableCardsInOneCommit() {
        Ship alpha = ship("Alpha Assignment Ship");
        Ship beta = ship("Beta Assignment Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(alpha, beta)).build());
        admiral.addReusableShips(List.of(alpha, beta), RosterState.ACTIVE);
        List<RosterChange> assignmentChanges = new ArrayList<RosterChange>();
        List<List<RosterState>> listenerStates = new ArrayList<List<RosterState>>();
        admiral.addRosterChangeListener(change -> {
            assignmentChanges.add(change);
            listenerStates.add(List.of(
                    admiral.getRoster().getReusableState(alpha),
                    admiral.getRoster().getReusableState(beta)));
        });

        admiral.assignShips(List.of(alpha, beta));

        assertEquals(2L, admiral.getRoster().getRevision());
        assertEquals(List.of(), admiral.getRoster().getActiveCards());
        assertEquals(2, admiral.getRoster().getMaintenanceCards().size());
        assertEquals(1, assignmentChanges.size());
        assertEquals(
                List.of(List.of(RosterState.MAINTENANCE, RosterState.MAINTENANCE)),
                listenerStates);
    }

    /**
     * Verifies the legacy Ship-shaped assignment adapter consumes authoritative One-Time quantity.
     */
    @Test
    void legacyAssignmentConsumesOneTimeQuantityThroughRoster() {
        Ship oneTimeShip = ship("One-Time Assignment Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(oneTimeShip)).build());
        admiral.adjustOneTimeShipQuantity(oneTimeShip, 2);
        List<RosterChange> assignmentChanges = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(assignmentChanges::add);

        admiral.assignShips(List.of(admiral.getOneTimeShips().get(0)));

        assertEquals(1, admiral.getRoster().getOneTimeQuantity(oneTimeShip));
        assertEquals(2L, admiral.getRoster().getRevision());
        assertEquals(java.util.Map.of(oneTimeShip.getName(), 1), admiral.getUsage());
        assertEquals(1, assignmentChanges.size());
    }

    /**
     * Verifies two Admirals sharing canonical GameData own independent card identities, states, and revisions.
     */
    @Test
    void admiralsSharingGameDataKeepReusableRostersIsolated() {
        Ship sharedShip = ship("Shared Canonical Ship");
        GameData gameData = GameData.builder().ships(List.of(sharedShip)).build();
        Admiral first = new Admiral(gameData);
        Admiral second = new Admiral(gameData);

        first.addReusableShips(List.of(sharedShip), RosterState.ACTIVE);

        RosterCard firstCard = cardFor(first.getRoster(), sharedShip);
        assertSame(sharedShip, firstCard.getShip());
        assertEquals(RosterState.ACTIVE, first.getRoster().getReusableState(sharedShip));
        assertEquals(RosterState.ABSENT, second.getRoster().getReusableState(sharedShip));
        assertEquals(1L, first.getRoster().getRevision());
        assertEquals(0L, second.getRoster().getRevision());

        second.addReusableShips(List.of(sharedShip), RosterState.MAINTENANCE);

        assertNotEquals(firstCard.getId(), cardFor(second.getRoster(), sharedShip).getId());
        assertEquals(RosterState.ACTIVE, first.getRoster().getReusableState(sharedShip));
        assertEquals(RosterState.MAINTENANCE, second.getRoster().getReusableState(sharedShip));
    }

    /**
     * Verifies a foreign identity rejects an entire bulk move before any valid local card is changed.
     */
    @Test
    void foreignCardRejectsWholeBulkOperationWithoutMutation() {
        Ship alpha = ship("Local Alpha");
        Ship beta = ship("Local Beta");
        GameData gameData = GameData.builder().ships(List.of(alpha, beta)).build();
        Admiral local = new Admiral(gameData);
        Admiral foreign = new Admiral(gameData);
        local.addReusableShips(List.of(alpha, beta), RosterState.ACTIVE);
        foreign.addReusableShips(List.of(alpha), RosterState.ACTIVE);
        RosterView before = local.getRoster();
        List<RosterChange> rejectedChanges = new ArrayList<RosterChange>();
        local.addRosterChangeListener(rejectedChanges::add);

        assertThrows(
                IllegalArgumentException.class,
                () -> local.moveReusableCards(
                        List.of(cardFor(before, beta), cardFor(foreign.getRoster(), alpha)),
                        RosterState.MAINTENANCE));

        assertSame(before, local.getRoster());
        assertEquals(RosterState.ACTIVE, local.getRoster().getReusableState(alpha));
        assertEquals(RosterState.ACTIVE, local.getRoster().getReusableState(beta));
        assertEquals(List.of(), rejectedChanges);
    }

    /**
     * Verifies clearing usage retains the exact Roster revision on which an otherwise applicable Solution depends.
     */
    @Test
    void clearingUsagePreservesCurrentRosterAndPlanningRevision() {
        Ship activeShip = ship("Active Usage Ship");
        Ship maintenanceShip = ship("Maintenance Usage Ship");
        Ship oneTimeShip = ship("One-Time Usage Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(activeShip, maintenanceShip, oneTimeShip))
                .build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(activeShip), RosterState.ACTIVE);
        admiral.addReusableShips(List.of(maintenanceShip), RosterState.MAINTENANCE);
        admiral.adjustOneTimeShipQuantity(oneTimeShip, 2);
        admiral.setUsage(new HashMap<String, Integer>(Map.of(activeShip.getName(), 9)));
        RosterView rosterBeforeClear = admiral.getRoster();

        admiral.clearUsage();

        assertEquals(Map.of(), admiral.getUsage());
        assertSame(rosterBeforeClear, admiral.getRoster());
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(activeShip));
        assertEquals(RosterState.MAINTENANCE, admiral.getRoster().getReusableState(maintenanceShip));
        assertEquals(2, admiral.getRoster().getOneTimeQuantity(oneTimeShip));
    }

    /**
     * Finds a reusable card by its canonical Ship in one immutable Roster view.
     *
     * @param roster immutable Roster snapshot
     * @param ship canonical Ship to locate
     * @return the matching reusable card
     */
    private static RosterCard cardFor(RosterView roster, Ship ship) {
        return roster.getReusableCards().stream()
                .filter(card -> card.getShip() == ship)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Projects canonical names from a Ship collection for ordering assertions.
     *
     * @param ships Ships to project
     * @return names in iteration order
     */
    private static List<String> shipNames(java.util.Collection<Ship> ships) {
        List<String> names = new ArrayList<String>();
        for (Ship ship : ships) {
            names.add(ship.getName());
        }
        return names;
    }

    /**
     * Projects canonical names from identity-bearing Roster cards for ordering assertions.
     *
     * @param cards cards in the order exposed by a Roster view
     * @return canonical Ship names in the same order
     */
    private static List<String> cardNames(java.util.Collection<RosterCard> cards) {
        List<String> names = new ArrayList<String>();
        for (RosterCard card : cards) {
            names.add(card.getShip().getName());
        }
        return names;
    }

    /**
     * Creates representative canonical Ship facts for Roster tests.
     *
     * @param name canonical Ship name
     * @return a mutable Ship suitable for builder-created GameData
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
