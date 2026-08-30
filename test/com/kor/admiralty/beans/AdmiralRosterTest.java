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
import java.util.List;

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
